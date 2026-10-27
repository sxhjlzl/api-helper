package com.lizhuolun.apihelper.core.contract.rules

import com.lizhuolun.apihelper.core.contract.ContractCheckContext
import com.lizhuolun.apihelper.core.contract.ContractIssue
import com.lizhuolun.apihelper.core.contract.ContractRule
import com.lizhuolun.apihelper.core.contract.ContractSeverity
import com.lizhuolun.apihelper.core.contract.classSimpleName
import com.lizhuolun.apihelper.core.contract.methodName

/**
 * 孤儿客户端接口规则：客户端声明的接口在工程内找不到任何 Controller 对端。
 *
 * 这是最老的一类问题，但报告里仍然有价值：它能按「最近改动」帮用户快速定位
 * 自己刚刚改坏的那一个，而不是给出一个几百条的平铺列表。
 *
 * 误报控制：工程内一个 Controller 都没有时整条规则跳过，
 * 避免纯客户端 SDK 模块被全量标红。
 *
 * @author lizhuolun
 * @date 2026/10/27
 */
class OrphanEndpointRule : ContractRule {

    override val id: String = "orphan-client"

    override fun check(context: ContractCheckContext): List<ContractIssue> {
        if (!context.snapshot.hasControllers) return emptyList()
        return context.pairs
            .filter { !it.isPaired && it.client.resolveMethod() != null }
            .map { pair ->
                val client = pair.client
                ContractIssue(
                    ruleId = id,
                    severity = ContractSeverity.WARNING,
                    message = context.message(
                        "contract.issue.orphan",
                        listOf(
                            "${client.classSimpleName()}#${client.methodName()}",
                            client.httpMethod.name,
                            client.url,
                        ),
                    ),
                    client = client,
                )
            }
    }
}