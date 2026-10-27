package com.lizhuolun.apihelper.core.contract.rules

import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.core.contract.ContractCheckContext
import com.lizhuolun.apihelper.core.contract.ContractIssue
import com.lizhuolun.apihelper.core.contract.ContractRule
import com.lizhuolun.apihelper.core.contract.ContractSeverity
import com.lizhuolun.apihelper.core.contract.classSimpleName
import com.lizhuolun.apihelper.core.contract.methodName

/**
 * 路径变量缺失规则：URL 模板里声明了 `{orderId}`，但方法参数中没有任何 `@PathVariable` 能接收它。
 *
 * 两端各自独立检查——路径变量名不需要跨服务一致（客户端发出去的是替换后的字面量），
 * 但**任何一侧**的 URL 模板与自身方法签名对不上，运行期都会失败，
 * 因此这条规则只比较「本侧模板 vs 本侧参数」，不做跨侧比较，误报率最低。
 *
 * 误报控制：
 * 1. 模板中没有变量时直接跳过；
 * 2. 方法存在 `Map` 类型参数时跳过，兼容 `@PathVariable Map<String, String>` 这类兜底写法；
 * 3. 参数没有显式名字时用源码参数名参与匹配，覆盖 `-parameters` 编译场景。
 *
 * @author lizhuolun
 * @date 2026/10/27
 */
class PathVariableRule : ContractRule {

    override val id: String = "path-variable-missing"

    override fun check(context: ContractCheckContext): List<ContractIssue> {
        val issues = ArrayList<ContractIssue>()
        for (pair in context.pairs) {
            checkSide(pair.client, context, issues)
            pair.controllers.forEach { controller -> checkSide(controller, context, issues) }
        }
        return issues
    }

    /**
     * 检查单个端点自身：模板变量是否都能在方法参数中找到对应项。
     */
    private fun checkSide(
        mapping: HttpMappingInfo,
        context: ContractCheckContext,
        issues: MutableList<ContractIssue>,
    ) {
        val template = mapping.pathTemplate
        if (!template.hasVariables) return

        val signature = context.signature(mapping) ?: return
        if (signature.params.any { it.typeText.startsWith(MAP_TYPE_PREFIX) }) return

        val declaredNames = signature.pathParams.map { it.effectiveName }.toSet()
        for (variableName in template.variableNames) {
            if (variableName in declaredNames) continue
            issues += ContractIssue(
                ruleId = id,
                severity = ContractSeverity.ERROR,
                message = context.message(
                    "contract.issue.path-variable-missing",
                    listOf(
                        "${mapping.classSimpleName()}#${mapping.methodName()}",
                        variableName,
                        mapping.url,
                    ),
                ),
                client = mapping,
            )
        }
    }

    companion object {

        /**
         * Map 类型前缀，命中即视为兜底写法并跳过检查。
         */
        private const val MAP_TYPE_PREFIX = "java.util.Map"
    }
}