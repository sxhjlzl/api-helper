package com.lizhuolun.apihelper.core.contract

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiMethod
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.core.contract.rules.DuplicateMappingRule
import com.lizhuolun.apihelper.core.contract.rules.OrphanEndpointRule
import com.lizhuolun.apihelper.core.contract.rules.ParamNameMismatchRule
import com.lizhuolun.apihelper.core.contract.rules.PathVariableRule
import com.lizhuolun.apihelper.core.contract.rules.RequiredMismatchRule

/**
 * 契约体检引擎：把双边快照装配成配对上下文，依次执行规则并汇总结果。
 *
 * 设计要点：
 * 1. 规则实现无状态，规则列表可注入，便于单测与后续扩展；
 * 2. 单条规则抛异常只记日志并跳过，不影响整份报告；
 * 3. 结果按「规则 + 两端方法」去重，避免多条规则重复报告同一处问题。
 *
 * @param rules 参与本次体检的规则列表，默认是 P0 规则集合
 * @author lizhuolun
 * @date 2026/10/27
 */
class ContractChecker(
    private val rules: List<ContractRule> = DEFAULT_RULES,
) {

    private val log = thisLogger()

    /**
     * 执行一次体检。
     *
     * @param context 体检上下文
     * @return 去重并按严重级排序后的问题列表
     */
    fun check(context: ContractCheckContext): List<ContractIssue> {
        val issues = ArrayList<ContractIssue>()
        for (rule in rules) {
            val ruleIssues = try {
                rule.check(context)
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                log.warn("ApiHelper: 契约规则执行失败, rule=${rule.id}", e)
                continue
            }
            issues += ruleIssues
        }
        return issues
            .distinctBy { it.dedupKey }
            .sortedWith(ISSUE_ORDER)
    }

    companion object {

        /**
         * 默认规则集合，顺序即报告中的展示顺序。
         */
        val DEFAULT_RULES: List<ContractRule> = listOf(
            OrphanEndpointRule(),
            DuplicateMappingRule(),
            ParamNameMismatchRule(),
            PathVariableRule(),
            RequiredMismatchRule(),
        )

        /**
         * 严重级优先、其次按客户端限定名排序，保证报告顺序稳定。
         */
        private val ISSUE_ORDER: Comparator<ContractIssue> = compareBy(
            { it.severity.ordinal },
            { it.client?.qualifier.orEmpty() },
        )

        /**
         * 把快照装配成配对上下文。
         *
         * 配对的判定完全复用 [HttpMappingInfo.matches]，与 gutter 跳转使用同一套语义，
         * 避免出现「图标说能跳，体检说没配对」的不一致。
         *
         * @param snapshot 双边快照
         * @param signatures 签名提供者
         * @return 体检上下文
         */
        fun buildContext(
            snapshot: BilateralSnapshot,
            signatures: ContractSignatureProvider,
        ): ContractCheckContext {
            val pairs = snapshot.clients.map { client ->
                val candidates = snapshot.controllersByMatchUrl[client.matchUrl].orEmpty()
                ContractPair(
                    client = client,
                    controllers = candidates.filter { it.matches(client) },
                )
            }
            return ContractCheckContext(
                pairs = pairs,
                snapshot = snapshot,
                signatures = signatures,
                message = { key, params -> ApiHelperBundle.message(key, *params.toTypedArray()) },
            )
        }
    }
}

/**
 * 在 read action 内过滤掉已失效的方法，避免后续拿到悬空指针。
 *
 * @param mappings 待过滤的映射
 * @return 方法仍然有效的映射列表
 */
internal fun List<HttpMappingInfo>.filterValid(): List<HttpMappingInfo> =
    filter { it.resolveMethod() != null }

/**
 * 解析映射对应的方法名，用于消息文案。
 *
 * @return 方法名；无法解析时返回空串
 */
internal fun HttpMappingInfo.methodName(): String = resolveMethod()?.name.orEmpty()

/**
 * 解析映射所属类的简单名，用于消息文案。
 */
internal fun HttpMappingInfo.classSimpleName(): String =
    resolveMethod()?.containingClass?.name.orEmpty()

/**
 * 便捷方法：判断方法是否属于客户端侧。
 */
internal fun PsiMethod.isClientMethod(): Boolean = containingClass?.let {
    com.lizhuolun.apihelper.core.annotation.AnnotationParser.isClientInterface(it)
} == true