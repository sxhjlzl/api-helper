package com.lizhuolun.apihelper.cache

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiMethod
import com.intellij.util.messages.MessageBus
import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.core.annotation.AnnotationParser
import com.lizhuolun.apihelper.scanner.EndpointScanner
import com.lizhuolun.apihelper.settings.ApiHelperSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 项目级"双边映射"缓存：分别存储客户端侧 (Feign + HttpExchange) 与 Controller 侧的 HttpMappingInfo。
 *
 * 查询按 HTTP 方法 + URL 匹配，性能从 O(N) 优化到 O(M)，M 为同端方法数。
 *
 * @author lizhuolun
 * @date 2026/6/9
 */
@Service(Service.Level.PROJECT)
class BilateralMappingCacheService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {

    /**
     * 客户端侧映射缓存，key 为 HttpMappingInfo.qualifier
     **/
    private val clientMappings: MutableMap<String, HttpMappingInfo> = ConcurrentHashMap()

    /**
     * Controller 侧映射缓存，key 为 HttpMappingInfo.qualifier
     **/
    private val controllerMappings: MutableMap<String, HttpMappingInfo> = ConcurrentHashMap()

    private val controllerRefreshLock = Any()
    private var controllerRefreshJob: Job? = null
    private val controllerRefreshGeneration = AtomicLong()

    /**
     * 全量重建客户端侧缓存。
     *
     * @param mappings 新的客户端映射集合
     */
    fun replaceClient(mappings: Collection<HttpMappingInfo>) {
        clientMappings.clear()
        for (info in mappings) {
            clientMappings[info.qualifier] = info
        }
    }

    /**
     * 全量重建 Controller 侧缓存。
     *
     * @param mappings 新的 Controller 映射集合
     */
    fun replaceController(mappings: Collection<HttpMappingInfo>) {
        controllerMappings.clear()
        for (info in mappings) {
            controllerMappings[info.qualifier] = info
        }
    }

    /**
     * 增量覆盖，根据 EndpointKind 决定写入哪一侧。
     *
     * @param info 单条映射
     */
    fun upsert(info: HttpMappingInfo) {
        val map = if (info.kind == EndpointKind.CONTROLLER) controllerMappings else clientMappings
        map[info.qualifier] = info
    }

    /**
     * 按方法移除缓存，用于 childRemoved 或 psi 修改时清除旧条目。
     *
     * @param method 已失效或被修改的方法
     */
    fun removeByMethod(method: PsiMethod) {
        val key = readAction {
            if (method.isValid) HttpMappingInfo.qualifierOf(method) else null
        } ?: return
        clientMappings.remove(key)
        controllerMappings.remove(key)
    }

    /**
     * 移除整个类下的所有方法映射，按类全限定名前缀匹配。
     *
     * @param qualifiedName 类全限定名
     */
    fun removeByClassQualifiedName(qualifiedName: String) {
        val prefix = "$qualifiedName#"
        clientMappings.keys.removeIf { it.startsWith(prefix) }
        controllerMappings.keys.removeIf { it.startsWith(prefix) }
    }

    /**
     * 判断给定的客户端方法是否存在匹配的 Controller 映射。
     *
     * 实现为仅查询缓存 + 对应当前方法做即时解析，不会触发全工程兜底扫描，
     * 以保证在 LineMarker 渲染（EDT / read action）期间不会卡顿。
     *
     * @param clientMethod 客户端方法
     * @return 存在至少一个匹配的 Controller 映射时返回 true
     */
    fun hasControllerCounterpart(clientMethod: PsiMethod): Boolean {
        if (!readAction { clientMethod.isValid }) return false
        val source = resolveClientSource(clientMethod) ?: return false
        return readAction {
            controllerMappings.values.any { it.matches(source) && it.resolveMethod() != null }
        }
    }

    /**
     * 判断给定的 Controller 方法是否存在匹配的客户端映射。
     *
     * 实现为仅查询缓存 + 对应当前方法做即时解析，不会触发全工程兜底扫描，
     * 以保证在 LineMarker 渲染（EDT / read action）期间不会卡顿。
     *
     * @param controllerMethod Controller 方法
     * @return 存在至少一个匹配的客户端映射时返回 true
     */
    fun hasClientCounterpart(controllerMethod: PsiMethod): Boolean {
        if (!readAction { controllerMethod.isValid }) return false
        val source = resolveControllerSource(controllerMethod) ?: return false
        return readAction {
            clientMappings.values.any { it.matches(source) && it.resolveMethod() != null }
        }
    }

    /**
     * 给定一个客户端方法，找出所有匹配的 Controller 映射。
     *
     * @param clientMethod 客户端方法
     * @return 匹配的 Controller 端映射列表
     */
    fun findControllerTargets(clientMethod: PsiMethod): List<HttpMappingInfo> {
        if (!readAction { clientMethod.isValid }) return emptyList()
        var source = computeFreshClientMapping(clientMethod)

        // 即时解析失败时回退到缓存条目
        if (source == null) {
            val key = readAction { HttpMappingInfo.qualifierOf(clientMethod) }
            source = readAction {
                clientMappings[key]?.takeIf { it.resolveMethod() != null }
            }
        }

        // 缓存也未命中时兜底全量扫描客户端侧
        if (source == null) {
            val scanned = EndpointScanner.scanClientEndpoints(project)
            if (scanned.isNotEmpty()) {
                replaceClient(scanned)
                source = readAction {
                    scanned.firstOrNull { it.resolveMethod() == clientMethod }
                }
            }
        }

        // 仍然无法获取 source 时返回空列表
        if (source == null) return emptyList()

        upsert(source)
        val targets = readAction {
            controllerMappings.values.filter { it.matches(source) && it.resolveMethod() != null }
        }
        if (targets.isNotEmpty() || controllerMappings.isNotEmpty()) return targets

        val manualProfile = ApiHelperSettings.getInstance().state.manualActiveProfile
        val scanned = EndpointScanner.scanControllerEndpoints(project, manualProfile)
        if (scanned.isNotEmpty()) {
            replaceController(scanned)
        }
        return readAction {
            scanned.filter { it.matches(source) && it.resolveMethod() != null }
        }
    }

    /**
     * 给定一个 Controller 方法，找出所有匹配的客户端映射。
     *
     * @param controllerMethod Controller 方法
     * @return 匹配的客户端映射列表
     */
    fun findClientTargets(controllerMethod: PsiMethod): List<HttpMappingInfo> {
        if (!readAction { controllerMethod.isValid }) return emptyList()
        var source = computeFreshControllerMapping(controllerMethod)

        // 即时解析失败时回退到缓存条目
        if (source == null) {
            val key = readAction { HttpMappingInfo.qualifierOf(controllerMethod) }
            source = readAction {
                controllerMappings[key]?.takeIf { it.resolveMethod() != null }
            }
        }

        // 缓存也未命中时兜底全量扫描 Controller 侧
        if (source == null) {
            val manualProfile = ApiHelperSettings.getInstance().state.manualActiveProfile
            val scanned = EndpointScanner.scanControllerEndpoints(project, manualProfile)
            if (scanned.isNotEmpty()) {
                replaceController(scanned)
                source = readAction {
                    scanned.firstOrNull { it.resolveMethod() == controllerMethod }
                }
            }
        }

        // 仍然无法获取 source 时返回空列表
        if (source == null) return emptyList()

        upsert(source)
        val targets = readAction {
            clientMappings.values.filter { it.matches(source) && it.resolveMethod() != null }
        }
        if (targets.isNotEmpty() || clientMappings.isNotEmpty()) return targets

        val scanned = EndpointScanner.scanClientEndpoints(project)
        if (scanned.isNotEmpty()) {
            replaceClient(scanned)
        }
        return readAction {
            scanned.filter { it.matches(source) && it.resolveMethod() != null }
        }
    }

    /**
     * 直接根据方法定位缓存中的映射，不存在时即时计算并写入。
     *
     * @param method 要解析的方法
     * @return 对应的 HttpMappingInfo，无法解析时返回 null
     */
    fun resolveMapping(method: PsiMethod): HttpMappingInfo? {
        val key = readAction {
            if (method.isValid) HttpMappingInfo.qualifierOf(method) else null
        } ?: return null
        readAction {
            clientMappings[key]?.takeIf { it.resolveMethod() != null }
        }?.let { return it }
        readAction {
            controllerMappings[key]?.takeIf { it.resolveMethod() != null }
        }?.let { return it }
        return computeFreshClientMapping(method) ?: computeFreshControllerMapping(method)
    }

    /**
     * 获取当前所有有效的客户端侧映射快照。
     *
     * @return 有效的客户端映射列表
     */
    fun getAllClientMappings(): List<HttpMappingInfo> = readAction {
        clientMappings.values.filter { it.resolveMethod() != null }
    }

    /**
     * 获取当前所有有效的 Controller 侧映射快照。
     *
     * @return 有效的 Controller 映射列表
     */
    fun getAllControllerMappings(): List<HttpMappingInfo> = readAction {
        controllerMappings.values.filter { it.resolveMethod() != null }
    }

    /**
     * 清空缓存，通常仅在测试或异常恢复路径调用。
     */
    fun clear() {
        clientMappings.clear()
        controllerMappings.clear()
    }

    /**
     * 异步重建 Controller 映射。连续配置变更会合并为最后一次刷新。
     */
    fun scheduleControllerRefresh(delayMillis: Int = 300) {
        val generation = controllerRefreshGeneration.incrementAndGet()
        synchronized(controllerRefreshLock) {
            controllerRefreshJob?.cancel()
            controllerRefreshJob = coroutineScope.launch(Dispatchers.Default) {
                delay(delayMillis.toLong())
                if (project.isDisposed || generation != controllerRefreshGeneration.get()) {
                    return@launch
                }

                val manualProfile = ApiHelperSettings.getInstance().state.manualActiveProfile
                val mappings = smartReadAction(project) {
                    EndpointScanner.scanControllerEndpoints(project, manualProfile)
                }
                if (project.isDisposed || generation != controllerRefreshGeneration.get()) {
                    return@launch
                }

                replaceController(mappings)
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        DaemonCodeAnalyzer.getInstance(project).settingsChanged()
                        project.messageBus.syncPublisher(CacheChangeListener.TOPIC).onCacheChanged()
                    }
                }
            }
        }
    }

    /**
     * 按 qualifier 优先从缓存、其次从 PSI 即时解析客户端方法的映射。
     * 解析成功后会写入缓存，但不会像 [findControllerTargets] 那样触发全工程扫描。
     *
     * @param method 客户端方法
     * @return 映射结果，不存在时返回 null
     */
    private fun resolveClientSource(method: PsiMethod): HttpMappingInfo? {
        val key = readAction { HttpMappingInfo.qualifierOf(method) }
        readAction {
            clientMappings[key]?.takeIf { it.resolveMethod() != null }
        }?.let { return it }
        val fresh = computeFreshClientMapping(method) ?: return null
        upsert(fresh)
        return fresh
    }

    /**
     * 按 qualifier 优先从缓存、其次从 PSI 即时解析 Controller 方法的映射。
     * 解析成功后会写入缓存，但不会像 [findClientTargets] 那样触发全工程扫描。
     *
     * @param method Controller 方法
     * @return 映射结果，不存在时返回 null
     */
    private fun resolveControllerSource(method: PsiMethod): HttpMappingInfo? {
        val key = readAction { HttpMappingInfo.qualifierOf(method) }
        readAction {
            controllerMappings[key]?.takeIf { it.resolveMethod() != null }
        }?.let { return it }
        val fresh = computeFreshControllerMapping(method) ?: return null
        upsert(fresh)
        return fresh
    }

    /**
     * 临时从 PSI 反向解析客户端映射，用于缓存未命中时的兜底。
     *
     * @param method 客户端方法
     * @return 映射结果，类不是客户端接口时返回 null
     */
    private fun computeFreshClientMapping(method: PsiMethod): HttpMappingInfo? = readAction {
        val cls = method.containingClass ?: return@readAction null
        val kind = when {
            AnnotationParser.isFeignInterface(cls) -> EndpointKind.FEIGN
            AnnotationParser.isHttpExchangeInterface(cls) -> EndpointKind.HTTP_EXCHANGE
            else -> return@readAction null
        }
        EndpointScanner.extractClientMappings(cls, kind)
            .firstOrNull { it.resolveMethod() == method }
    }

    /**
     * 临时从 PSI 反向解析 Controller 映射，用于缓存未命中时的兜底。
     *
     * @param method Controller 方法
     * @return 映射结果，类不是 Controller 时返回 null
     */
    private fun computeFreshControllerMapping(method: PsiMethod): HttpMappingInfo? {
        val manualProfile = ApiHelperSettings.getInstance().state.manualActiveProfile
        return readAction {
            val cls = method.containingClass ?: return@readAction null
            if (!AnnotationParser.isControllerClass(cls)) return@readAction null
            EndpointScanner.extractControllerMappings(cls, manualProfile)
                .firstOrNull { it.resolveMethod() == method }
        }
    }

    private inline fun <T> readAction(crossinline block: () -> T): T =
        ApplicationManager.getApplication().runReadAction(Computable { block() })

    companion object {
        /**
         * 便捷获取入口，避免业务代码到处写 project.getService(...)。
         *
         * @param project 当前工程
         * @return 项目级缓存 Service
         */
        fun of(project: Project): BilateralMappingCacheService =
            project.getService(BilateralMappingCacheService::class.java)
    }
}
