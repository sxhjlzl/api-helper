package com.lizhuolun.apihelper.ui.contract

import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.core.contract.ContractReport
import com.lizhuolun.apihelper.core.contract.ContractSeverity

/**
 * 体检报告导出器：把结果整理成可直接贴进 PR 描述或 CI 日志的 Markdown。
 *
 * @author lizhuolun
 * @date 2026/10/27
 */
object ContractMarkdownExporter {

    /**
     * 导出 Markdown 文本。
     *
     * @param report 体检报告
     * @return Markdown 文本
     */
    fun export(report: ContractReport): String {
        val builder = StringBuilder()
        builder.appendLine("# ${ApiHelperBundle.message("contract.doc.title")}")
        builder.appendLine()
        builder.appendLine(
            ApiHelperBundle.message(
                "contract.doc.summary",
                report.errorCount,
                report.warningCount,
                report.endpointCount,
            ),
        )
        builder.appendLine()

        if (report.isHealthy) {
            builder.appendLine(ApiHelperBundle.message("contract.empty.healthy"))
            return builder.toString()
        }

        for ((ruleId, issues) in report.issuesByRule()) {
            builder.appendLine("## ${ApiHelperBundle.message("contract.rule.$ruleId")}")
            builder.appendLine()
            for (issue in issues) {
                builder.append("- [").append(severityText(issue.severity)).append("] ")
                builder.appendLine(issue.message)
            }
            builder.appendLine()
        }
        return builder.toString()
    }

    /**
     * 严重级的文本表示，保持报告可在纯文本环境阅读。
     */
    private fun severityText(severity: ContractSeverity): String = when (severity) {
        ContractSeverity.ERROR -> "ERROR"
        ContractSeverity.WARNING -> "WARN"
        ContractSeverity.INFO -> "INFO"
    }
}