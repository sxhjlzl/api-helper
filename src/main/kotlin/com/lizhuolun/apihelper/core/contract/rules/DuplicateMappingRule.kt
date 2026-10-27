package com.lizhuolun.apihelper.core.contract.rules

import com.lizhuolun.apihelper.core.HttpMethod
import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.core.contract.ContractCheckContext
import com.lizhuolun.apihelper.core.contract.ContractIssue
import com.lizhuolun.apihelper.core.contract.ContractRule
import com.lizhuolun.apihelper.core.contract.ContractSeverity
import com.lizhuolun.apihelper.core.contract.classSimpleName
import com.lizhuolun.apihelper.core.contract.filterValid
import com.lizhuolun.apihelper.core.contract.methodName

/**
 * 重复映射规则：两个 Controller 方法映射到同一个路径与 HTTP 方法。
 *
 * 这类问题不是「运行期偶发」，而是 Spring 启动阶段直接抛
 * `Ambiguous mapping. Cannot map ...` 导致应用起不来，因此定为 ERROR。
 *
 * 误报控制：`@RequestMapping` 未指定 method（[HttpMethod.ANY]）时，
 * 两个不同路径的方法不会互相命中；只有路径完全一致才参与比较。
 *
 * @author lizhuolun
 * @date 2026/10/27
 */
class DuplicateMappingRule : ContractRule {

    override val id: String = "duplicate-mapping"

    override fun check(context: ContractCheckContext): List<ContractIssue> {
        val controllers = context.snapshot.controllers.filterValid()
        if (controllers.size < 2) return emptyList()

        val issues = ArrayList<ContractIssue>()
        val reported = HashSet<String>()

        val byPath = controllers.groupBy { it.matchUrl }
        for ((_, group) in byPath) {
            if (group.size < 2) continue
            for (i in group.indices) {
                for (j in i + 1 until group.size) {
                    val first = group[i]
                    val second = group[j]
                    if (first.qualifier == second.qualifier) continue
                    if (!methodsConflict(first, second)) continue

                    val key = listOf(first.qualifier, second.qualifier).sorted().joinToString("|")
                    if (!reported.add(key)) continue

                    issues += ContractIssue(
                        ruleId = id,
                        severity = ContractSeverity.ERROR,
                        message = context.message(
                            "contract.issue.duplicate",
                            listOf(
                                display(first),
                                display(second),
                                first.matchUrl,
                            ),
                        ),
                        controllers = listOf(first, second),
                    )
                }
            }
        }
        return issues
    }

    /**
     * 判断两个映射的 HTTP 方法是否冲突：相同，或任一侧为 ANY 通配。
     */
    private fun methodsConflict(first: HttpMappingInfo, second: HttpMappingInfo): Boolean =
        first.httpMethod == second.httpMethod ||
                first.httpMethod == HttpMethod.ANY ||
                second.httpMethod == HttpMethod.ANY

    /**
     * 拼装 `类名#方法名` 展示文本。
     */
    private fun display(mapping: HttpMappingInfo): String =
        "${mapping.classSimpleName()}#${mapping.methodName()}"
}