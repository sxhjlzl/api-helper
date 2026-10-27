package com.lizhuolun.apihelper.ui.contract

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.lizhuolun.apihelper.cache.BilateralMappingCacheService
import com.lizhuolun.apihelper.core.contract.ContractChecker
import com.lizhuolun.apihelper.core.contract.ContractReport
import com.lizhuolun.apihelper.core.contract.ContractSignatureCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 契约体检的项目级调度服务。
 *
 * 职责：
 * 1. 在后台线程执行体检，避免阻塞 EDT；
 * 2. 复用缓存版本号做结果复用，版本未变时直接回调已有报告；
 * 3. 连续触发时取消上一次任务，避免重复计算。
 *
 * @param project 当前工程
 * @param scope 工程级协程作用域，由平台注入并在工程销毁时自动取消
 * @author lizhuolun
 * @date 2026/10/27
 */
@Service(Service.Level.PROJECT)
class ContractScanService(
    private val project: Project,
    private val scope: CoroutineScope,
) {

    private val log = thisLogger()

    /**
     * 最近一次体检结果。
     */
    @Volatile
    private var currentReport: ContractReport? = null

    /**
     * 正在执行的体检任务。
     */
    @Volatile
    private var scanJob: Job? = null

    /**
     * 获取最近一次体检结果。
     *
     * @return 报告；从未体检过时返回 null
     */
    fun report(): ContractReport? = currentReport

    /**
     * 判断当前报告是否已过期：缓存发生过写入即视为过期。
     *
     * @return 报告存在且版本落后时返回 true
     */
    fun isStale(): Boolean {
        val report = currentReport ?: return false
        val currentVersion = BilateralMappingCacheService.of(project).snapshot().version
        return report.snapshotVersion != currentVersion
    }

    /**
     * 触发一次体检。
     *
     * 若缓存版本未变化，会同步回调已有结果，不做重复计算。
     *
     * @param onComplete 结果回调，始终在 EDT 上执行
     */
    fun refresh(onComplete: (ContractReport) -> Unit) {
        if (project.isDisposed) return

        if (DumbService.isDumb(project)) {
            log.info("ApiHelper: 索引未就绪，跳过契约体检")
            onComplete(ContractReport.empty())
            return
        }

        val snapshot = BilateralMappingCacheService.of(project).snapshot()
        val cached = currentReport
        if (cached != null && cached.snapshotVersion == snapshot.version) {
            onComplete(cached)
            return
        }

        scanJob?.cancel()
        scanJob = scope.launch(Dispatchers.Default) {
            val report = try {
                val issues = smartReadAction(project) {
                    ContractChecker().check(
                        ContractChecker.buildContext(snapshot, ContractSignatureCache.of(project)),
                    )
                }
                ContractReport(
                    issues = issues,
                    snapshotVersion = snapshot.version,
                    endpointCount = snapshot.clients.size + snapshot.controllers.size,
                    generatedAt = System.currentTimeMillis(),
                )
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                log.warn("ApiHelper: 契约体检失败", e)
                ContractReport.empty(snapshot.version)
            }
            currentReport = report
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) onComplete(report)
            }
        }
    }

    /**
     * 清空已有结果并取消在途任务。
     */
    fun invalidate() {
        scanJob?.cancel()
        scanJob = null
        currentReport = null
    }

    companion object {

        /**
         * 获取工程级体检服务。
         *
         * @param project 当前工程
         * @return 体检调度服务
         */
        fun of(project: Project): ContractScanService =
            project.getService(ContractScanService::class.java)
    }
}