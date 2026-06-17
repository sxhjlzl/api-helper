package com.lizhuolun.apihelper.listener

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.PsiClass
import com.lizhuolun.apihelper.cache.BilateralMappingCacheService
import com.lizhuolun.apihelper.cache.CacheChangeListener
import com.lizhuolun.apihelper.cache.PsiClassCacheService
import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.scanner.EndpointScanner
import com.lizhuolun.apihelper.settings.ApiHelperSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 工程打开后异步预热缓存。
 *
 * 设计要点：
 * - 实现 ProjectActivity（Kotlin 协程 friendly），取代老的 ProjectComponent
 * - 等待 DumbMode 结束后再扫描（smartReadAction 自带等待索引就绪的语义）
 * - 在 Dispatchers.Default 上执行 CPU 密集型工作，不阻塞 UI 与 IO 线程
 * - 所有 PSI 访问（包括 PsiMethod.containingClass、SmartPointer 构建）
 *   必须在同一段 smartReadAction 内完成，避免出 read action 后再触碰 PSI
 *   导致 "Read access is allowed from inside read-action only" 异常
 */
class ApiHelperStartupActivity : ProjectActivity {

    private val log = thisLogger()

    override suspend fun execute(project: Project) {
        if (project.isDisposed) return
        log.info("ApiHelper: 启动预热缓存, project=${project.name}")

        DumbService.getInstance(project).waitForSmartMode()
        if (project.isDisposed) return

        withContext(Dispatchers.Default) {
            val manualProfile = ApiHelperSettings.getInstance().state.manualActiveProfile
            val classCache = PsiClassCacheService.of(project)
            val mappingCache = BilateralMappingCacheService.of(project)

            // 整段 PSI 操作（扫描 + containingClass 提取 + SmartPointer 写入缓存）
            // 必须包在同一段 smartReadAction 内，确保始终持有读权限。
            val (clientEndpoints, controllerEndpoints) = smartReadAction(project) {
                val clientList = EndpointScanner.scanClientEndpoints(project)
                val controllerList = EndpointScanner.scanControllerEndpoints(project, manualProfile)

                val feignClasses = collectDistinctClasses(clientList, EndpointKind.FEIGN)
                val exchangeClasses = collectDistinctClasses(clientList, EndpointKind.HTTP_EXCHANGE)
                val controllerClasses = collectDistinctClasses(controllerList, null)

                classCache.replaceAll(EndpointKind.FEIGN, feignClasses)
                classCache.replaceAll(EndpointKind.HTTP_EXCHANGE, exchangeClasses)
                classCache.replaceAll(EndpointKind.CONTROLLER, controllerClasses)

                clientList to controllerList
            }

            // 下面只往 ConcurrentHashMap 写入已经构建好的 HttpMappingInfo，
            // 不再触发 PSI 访问，可以安全地在 read action 外运行。
            mappingCache.replaceClient(clientEndpoints)
            mappingCache.replaceController(controllerEndpoints)

            log.info(
                "ApiHelper: 预热完成, clientEndpoints=${clientEndpoints.size}, " +
                    "controllerEndpoints=${controllerEndpoints.size}",
            )

            // 缓存就绪后通知 DaemonCodeAnalyzer 重新渲染行内图标，
            // 并通过消息总线通知工具窗口刷新。
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    DaemonCodeAnalyzer.getInstance(project).settingsChanged()
                    project.messageBus.syncPublisher(CacheChangeListener.TOPIC).onCacheChanged()
                }
            }
        }
    }

    /**
     * 从端点列表中提取去重后的 containingClass。
     *
     * @param endpoints 端点列表
     * @param filterKind 仅保留指定 kind 的端点；为 null 时不过滤
     * @return 去重后的 PsiClass 列表
     */
    private fun collectDistinctClasses(
        endpoints: List<HttpMappingInfo>,
        filterKind: EndpointKind?,
    ): List<PsiClass> {
        val seen = LinkedHashSet<PsiClass>()
        for (info in endpoints) {
            if (filterKind != null && info.kind != filterKind) continue
            val cls = info.resolveMethod()?.containingClass ?: continue
            seen += cls
        }
        return seen.toList()
    }
}
