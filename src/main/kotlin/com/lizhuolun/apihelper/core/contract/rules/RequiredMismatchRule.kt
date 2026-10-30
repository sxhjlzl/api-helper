package com.lizhuolun.apihelper.core.contract.rules

import com.lizhuolun.apihelper.core.contract.ContractCheckContext
import com.lizhuolun.apihelper.core.contract.ContractIssue
import com.lizhuolun.apihelper.core.contract.ContractRule
import com.lizhuolun.apihelper.core.contract.ContractSeverity
import com.lizhuolun.apihelper.core.contract.classSimpleName
import com.lizhuolun.apihelper.core.contract.methodName

/**
 * 必填性不一致规则：客户端把 Query 参数显式声明为可选（required=false），服务端却显式要求必填。
 *
 * 这种不一致编译与启动都不会报错，只在调用方漏传参数时以 400 暴露。
 *
 * 误报控制（宁可漏报）：
 * 1. 只比较两侧都**显式声明了 required 属性**的参数，注解缺省值不参与判定；
 * 2. 按协议名（effectiveName）匹配参数，匹配不上直接跳过；
 * 3. 仅检查 Query 参数（`@RequestParam`），路径变量与请求体不参与。
 *
 * @author lizhuolun
 * @date 2026/10/29
 */
class RequiredMismatchRule : ContractRule {

    override val id: String = "required-mismatch"

    override fun check(context: ContractCheckContext): List<ContractIssue> {
        val issues = ArrayList<ContractIssue>()
        for (pair in context.pairs) {
            if (!pair.isPaired) continue
            val clientSignature = context.signature(pair.client) ?: continue
            val clientOptionals = clientSignature.queryParams.filter { it.requiredDeclared && !it.required }
            if (clientOptionals.isEmpty()) continue

            for (controller in pair.controllers) {
                val controllerSignature = context.signature(controller) ?: continue
                for (clientParam in clientOptionals) {
                    val controllerParam = controllerSignature.queryParams
                        .firstOrNull { it.effectiveName == clientParam.effectiveName }
                        ?: continue
                    if (!controllerParam.requiredDeclared || !controllerParam.required) continue
                    issues += ContractIssue(
                        ruleId = id,
                        severity = ContractSeverity.WARNING,
                        message = context.message(
                            "contract.issue.required-mismatch",
                            listOf(
                                display(pair.client.classSimpleName(), pair.client.methodName()),
                                clientParam.effectiveName,
                                display(controller.classSimpleName(), controller.methodName()),
                            ),
                        ),
                        client = pair.client,
                        controllers = pair.controllers,
                    )
                }
            }
        }
        return issues
    }

    /**
     * 拼装 `类名#方法名` 展示文本。
     */
    private fun display(className: String, methodName: String): String = "$className#$methodName"
}
