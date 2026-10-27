package com.lizhuolun.apihelper.core.contract

/**
 * 一次契约体检的完整结果。
 *
 * @property issues 命中的问题列表，已按严重级排序并去重
 * @property snapshotVersion 产出该报告时的缓存版本号，用于判断结果是否过期
 * @property endpointCount 参与体检的端点总数（两侧之和）
 * @property generatedAt 报告生成时间戳，毫秒
 * @author lizhuolun
 * @date 2026/10/27
 */
data class ContractReport(
    val issues: List<ContractIssue>,
    val snapshotVersion: Long,
    val endpointCount: Int,
    val generatedAt: Long,
) {

    /**
     * 致命问题数量（会直接导致运行时失败）。
     */
    val errorCount: Int
        get() = issues.count { it.severity == ContractSeverity.ERROR }

    /**
     * 警告数量。
     */
    val warningCount: Int
        get() = issues.count { it.severity == ContractSeverity.WARNING }

    /**
     * 是否全部通过。
     */
    val isHealthy: Boolean
        get() = issues.isEmpty()

    /**
     * 按规则分组，保持原顺序。
     */
    fun issuesByRule(): Map<String, List<ContractIssue>> =
        issues.groupBy { it.ruleId }

    companion object {

        /**
         * 空报告，用于索引未就绪或尚未体检的场景。
         *
         * @param version 对应的缓存版本号
         */
        fun empty(version: Long = 0L): ContractReport = ContractReport(
            issues = emptyList(),
            snapshotVersion = version,
            endpointCount = 0,
            generatedAt = 0L,
        )
    }
}