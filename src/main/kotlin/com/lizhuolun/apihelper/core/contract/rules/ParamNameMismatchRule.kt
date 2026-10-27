package com.lizhuolun.apihelper.core.contract.rules

import com.lizhuolun.apihelper.core.contract.ContractCheckContext
import com.lizhuolun.apihelper.core.contract.ContractFix
import com.lizhuolun.apihelper.core.contract.ContractFixKind
import com.lizhuolun.apihelper.core.contract.ContractIssue
import com.lizhuolun.apihelper.core.contract.ContractParam
import com.lizhuolun.apihelper.core.contract.ContractRule
import com.lizhuolun.apihelper.core.contract.ContractSeverity
import com.lizhuolun.apihelper.core.contract.classSimpleName
import com.lizhuolun.apihelper.core.contract.methodName

/**
 * Query 参数名不一致规则：客户端声明的参数名与服务端期望的参数名不同。
 *
 * 这是本版最有价值的一条规则：编译能通过、单测能过，
 * 只有跨服务联调或线上才暴露为 400，且报错信息往往看不出是哪个参数名错了。
 *
 * 误报控制（宁可漏报）：
 * 1. 只比较**两端都显式声明了注解名字**的参数，依赖 `-parameters` 的隐式命名不参与；
 * 2. 两端 Query 参数数量不同时直接跳过，避免签名结构差异带来一对多错配；
 * 3. 按声明顺序配对，与 Feign 接口照抄 Controller 签名的常见写法一致。
 *
 * @author lizhuolun
 * @date 2026/10/27
 */
class ParamNameMismatchRule : ContractRule {

    override val id: String = "param-name-mismatch"

    override fun check(context: ContractCheckContext): List<ContractIssue> {
        val issues = ArrayList<ContractIssue>()
        for (pair in context.pairs) {
            if (!pair.isPaired) continue
            val clientSignature = context.signature(pair.client) ?: continue
            val clientParams = clientSignature.queryParams.explicitlyNamed()
            if (clientParams.isEmpty()) continue

            for (controller in pair.controllers) {
                val controllerSignature = context.signature(controller) ?: continue
                val controllerParams = controllerSignature.queryParams.explicitlyNamed()
                if (controllerParams.size != clientParams.size) continue

                clientParams.zip(controllerParams).forEach { (clientParam, controllerParam) ->
                    if (clientParam.explicitName == controllerParam.explicitName) return@forEach
                    val expected = controllerParam.explicitName ?: return@forEach
                    issues += ContractIssue(
                        ruleId = id,
                        severity = ContractSeverity.ERROR,
                        message = context.message(
                            "contract.issue.param-name",
                            listOf(
                                display(pair.client.classSimpleName(), pair.client.methodName()),
                                clientParam.explicitName.orEmpty(),
                                display(controller.classSimpleName(), controller.methodName()),
                                expected,
                            ),
                        ),
                        client = pair.client,
                        controllers = pair.controllers,
                        fixes = listOf(
                            ContractFix(
                                kind = ContractFixKind.SYNC_PARAM_NAME,
                                targetName = expected,
                                sourceName = clientParam.explicitName.orEmpty(),
                            ),
                        ),
                    )
                }
            }
        }
        return issues
    }

    /**
     * 过滤出显式声明名字的参数。
     */
    private fun List<ContractParam>.explicitlyNamed(): List<ContractParam> =
        filter { !it.explicitName.isNullOrBlank() }

    /**
     * 拼装 `类名#方法名` 展示文本。
     */
    private fun display(className: String, methodName: String): String = "$className#$methodName"
}