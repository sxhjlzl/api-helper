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
import java.util.concurrent.atomic.AtomicLong

/**
 * 项目级"双边映射"缓存：分别存储客户端侧 (Feign + HttpExchange) 与 Controller 侧的 HttpMappingInfo。
 *
 * 查询按 HTTP 方法 + 归一化路径匹配，基于 matchUrl 倒排索引将查询复杂度从 O(N) 降为 O(1) 定位。
 *
 * 存储层采用不可变快照 + @Volatile 引用原子替换：
 * 全量重建时不会再出现"先清空再逐条写入"的空窗期，
 * 避免刷新瞬间查询到空缓存导致 gutter 图标闪烁或跳转误报无对端。
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
     * 单侧缓存的不可变快照，同时维护 qualifier 主键与 matchUrl 倒排索引。
     * 构造后不再变更，任何增删都通过生成新快照完成。
     *
     * @property byQualifier 以 HttpMappingInfo.qualifier 为 key 的主索引
     * @property byMatchUrl 以归一化匹配路径为 key 的倒排索引，供两端匹配查询使用
     */
    private class SideSnapshot(
        val byQualifier: Map<String, HttpMappingInfo> = emptyMap(),
    ) {

        val byMatchUrl: Map<String, List<HttpMappingInfo>> =
            byQualifier.values.groupBy { it.matchUrl }

        /**
         * 查找与给定映射归一化路径相同的候选项，只命中少数几条而非遍历全部缓存。
         *
         * @param source 用于提供 matchUrl 的源映射
         * @return 同归一化路径的候选映射列表，无命中时返回空列表
         */
        fun candidatesOf(source: HttpMappingInfo): List<HttpMappingInfo> =
            byMatchUrl[source.matchUrl].orEmpty()

        /**
         * 新增或覆盖一条映射，返回新快照。
         *
         * @param info 要写入的映射
         * @return 写入后的新快照
         */
        fun plus(info: HttpMappingInfo): SideSnapshot =
            SideSnapshot(byQualifier + (info.qualifier to info))

        /**
         * 按 qualifier 前缀批量移除，返回新快照。
         *
         * @param prefix qualifier 前缀，例如 "类全限定名#"
         * @return 移除后的新快照；无命中时返回当前快照本身
         */
        fun removeByQualifierPrefix(prefix: String): SideSnapshot {
            if (byQualifier.keys.none { it.startsWith(prefix) }) return this
            return SideSnapshot(byQualifier.filterKeys { !it.startsWith(prefix) })
        }

        /**
         * 移除指定 qualifier，返回新快照。
         *
         * @param qualifier 要移除的映射 qualifier
         * @return 移除后的新快照；无命中时返回当前快照本身
         */
        fun removeByQualifier(qualifier: String): SideSnapshot {
            if (!byQualifier.containsKey(qualifier)) return this
            return SideSnapshot(byQualifier - qualifier)
        }

        companion object {
            val EMPTY = SideSnapshot()
        }
    }

    /**
     * 客户端侧缓存快照，整体原子替换，读侧无需加锁
     **/
    @Volatile
    private var clientSide: SideSnapshot = SideSnapshot.EMPTY

    /**
     * Controller 侧缓存快照，整体原子替换，读侧无需加锁
     **/
    @Volatile
    private var controllerSide: SideSnapshot = SideSnapshot.EMPTY

    /**
     * 增量写入锁，保证并发 upsert / remove 时快照更新不丢失。
     * 全量 replace 不参与该锁，后到的 replace 直接覆盖即可。
     **/
    private val mutationLock = Any()

    private val controllerRefreshLock = Any()
    private var controllerRefreshJob: Job? = null
    private val controllerRefreshGeneration = AtomicLong()

    /**
     * 全量重建客户端侧缓存，整体原子替换，不存在清空后的空窗期。
     *
     * @param mappings 新的客户端映射集合
     */
    fun replaceClient(mappings: Collection<HttpMappingInfo>) {
        clientSide = SideSnapshot(mappings.associateBy { it.qualifier })
    }

    /**
     * 全量重建 Controller 侧缓存，整体原子替换，不存在清空后的空窗期。
     *
     * @param mappings 新的 Controller 映射集合
     */
    fun replaceController(mappings: Collection<HttpMappingInfo>) {
        controllerSide = SideSnapshot(mappings.associateBy { it.qualifier })
    }

    /**
     * 增量覆盖，根据 EndpointKind 决定写入哪一侧。
     *
     * @param info 单条映射
     */
    fun upsert(info: HttpMappingInfo) {
        synchronized(mutationLock) {
            if (info.kind == EndpointKind.CONTROLLER) {
                controllerSide = controllerSide.plus(info)
            } else {
                clientSide = clientSide.plus(info)
            }
        }
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
        synchronized(mutationLock) {
            clientSide = clientSide.removeByQualifier(key)
            controllerSide = controllerSide.removeByQualifier(key)
        }
    }

    /**
     * 移除整个类下的所有方法映射，按类全限定名前缀匹配。
     *
     * @param qualifiedName 类全限定名
     */
    fun removeByClassQualifiedName(qualifiedName: String) {
        val prefix = "$qualifiedName#"
        synchronized(mutationLock) {
            clientSide = clientSide.removeByQualifierPrefix(prefix)
            controllerSide = controllerSide.removeByQualifierPrefix(prefix)
        }
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
            controllerSide.candidatesOf(source).any { it.matches(source) && it.resolveMethod() != null }
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
            clientSide.candidatesOf(source).any { it.matches(source) && it.resolveMethod() != null }
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
                clientSide.byQualifier[key]?.takeIf { it.resolveMethod() != null }
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
            controllerSide.candidatesOf(source).filter { it.matches(source) && it.resolveMethod() != null }
        }
        if (targets.isNotEmpty() || controllerSide.byQualifier.isNotEmpty()) return targets

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
                controllerSide.byQualifier[key]?.takeIf { it.resolveMethod() != null }
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
            clientSide.candidatesOf(source).filter { it.matches(source) && it.resolveMethod() != null }
        }
        if (targets.isNotEmpty() || clientSide.byQualifier.isNotEmpty()) return targets

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
            clientSide.byQualifier[key]?.takeIf { it.resolveMethod() != null }
        }?.let { return it }
        readAction {
            controllerSide.byQualifier[key]?.takeIf { it.resolveMethod() != null }
        }?.let { return it }
        return computeFreshClientMapping(method) ?: computeFreshControllerMapping(method)
    }

    /**
     * 获取当前所有有效的客户端侧映射快照。
     *
     * @return 有效的客户端映射列表
     */
    fun getAllClientMappings(): List<HttpMappingInfo> = readAction {
        clientSide.byQualifier.values.filter { it.resolveMethod() != null }
    }

    /**
     * 获取当前所有有效的 Controller 侧映射快照。
     *
     * @return 有效的 Controller 映射列表
     */
    fun getAllControllerMappings(): List<HttpMappingInfo> = readAction {
        controllerSide.byQualifier.values.filter { it.resolveMethod() != null }
    }

    /**
     * 清空缓存，通常仅在测试或异常恢复路径调用。
     */
    fun clear() {
        clientSide = SideSnapshot.EMPTY
        controllerSide = SideSnapshot.EMPTY
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
            clientSide.byQualifier[key]?.takeIf { it.resolveMethod() != null }
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
            controllerSide.byQualifier[key]?.takeIf { it.resolveMethod() != null }
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
