package com.lizhuolun.apihelper.inspection

import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.lizhuolun.apihelper.cache.BilateralMappingCacheService
import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.core.annotation.AnnotationParser
import com.lizhuolun.apihelper.core.contract.ContractCheckContext
import com.lizhuolun.apihelper.core.contract.ContractChecker
import com.lizhuolun.apihelper.core.contract.ContractIssue
import com.lizhuolun.apihelper.core.contract.ContractPair
import com.lizhuolun.apihelper.core.contract.ContractRule
import com.lizhuolun.apihelper.core.contract.ContractSignatureCache
import com.lizhuolun.apihelper.core.contract.rules.OrphanEndpointRule
import com.lizhuolun.apihelper.core.contract.rules.ParamNameMismatchRule
import com.lizhuolun.apihelper.core.contract.rules.PathVariableRule
import com.lizhuolun.apihelper.core.contract.rules.RequiredMismatchRule
import com.lizhuolun.apihelper.inspection.quickfix.SyncParamNameFix
import org.jetbrains.uast.UMethod

/**
 * 契约一致性编辑器检查。
 *
 * 只做「本地 O(1) 查询 + 单方法规则」，绝不在高亮期间触发全工程扫描：
 * 先从双边快照按 matchUrl 取对端，再只跑与单个方法相关的三条规则。
 *
 * @author lizhuolun
 * @date 2026/10/27
 */
class ContractInspection : AbstractBaseUastLocalInspectionTool(UMethod::class.java) {

    override fun checkMethod(
        method: UMethod,
        manager: InspectionManager,
        isOnTheFly: Boolean,
    ): Array<ProblemDescriptor>? {
        val psiMethod: PsiMethod = method.javaPsi
        if (!psiMethod.isValid) return null

        val project = psiMethod.project
        if (project.isDisposed) return null

        val snapshot = BilateralMappingCacheService.of(project).snapshot()
        if (snapshot.clients.isEmpty() || snapshot.controllers.isEmpty()) return null

        val mapping = resolveMapping(project, psiMethod) ?: return null
        val pairs = buildPairs(mapping, snapshot)
        if (pairs.isEmpty()) return null

        val context = ContractCheckContext(
            pairs = pairs,
            snapshot = snapshot,
            signatures = ContractSignatureCache.of(project),
            message = { key, params ->
                com.lizhuolun.apihelper.ApiHelperBundle.message(key, *params.toTypedArray())
            },
        )
        val issues = ContractChecker(INSPECTION_RULES).check(context)
        if (issues.isEmpty()) return null

        val anchor = anchorOf(psiMethod)
        val fixesEnabled = mapping.kind != EndpointKind.CONTROLLER
        return issues.map { issue ->
            descriptorOf(manager, anchor, issue, isOnTheFly, fixesEnabled)
        }.toTypedArray()
    }

    /**
     * 把单个方法映射成对端配对；服务端侧可能对应多个客户端实现。
     */
    private fun buildPairs(
        mapping: HttpMappingInfo,
        snapshot: com.lizhuolun.apihelper.core.contract.BilateralSnapshot,
    ): List<ContractPair> {
        val isClient = mapping.kind != EndpointKind.CONTROLLER
        return if (isClient) {
            val counterparts = snapshot.controllersByMatchUrl[mapping.matchUrl].orEmpty()
                .filter { it.matches(mapping) }
            listOf(ContractPair(client = mapping, controllers = counterparts))
        } else {
            snapshot.clientsByMatchUrl[mapping.matchUrl].orEmpty()
                .filter { it.matches(mapping) }
                .map { ContractPair(client = it, controllers = listOf(mapping)) }
        }
    }

    /**
     * 解析方法当前对应的映射，优先用缓存，未命中时即时计算。
     */
    private fun resolveMapping(project: Project, method: PsiMethod): HttpMappingInfo? =
        BilateralMappingCacheService.of(project).resolveMapping(method)

    /**
     * 高亮锚点优先选方法上的 REST 注解，与 gutter 保持一致。
     */
    private fun anchorOf(method: PsiMethod): PsiElement =
        AnnotationParser.findRestfulAnnotation(method)
            ?: (method.nameIdentifier as? PsiElement)
            ?: method

    /**
     * 把契约问题转换成 IDE 的 ProblemDescriptor。
     */
    private fun descriptorOf(
        manager: InspectionManager,
        anchor: PsiElement,
        issue: ContractIssue,
        isOnTheFly: Boolean,
        fixesEnabled: Boolean,
    ): ProblemDescriptor {
        val fixes = if (fixesEnabled) {
            issue.fixes.map { fix -> SyncParamNameFix(sourceName = fix.sourceName, targetName = fix.targetName) }
        } else {
            emptyList()
        }
        return manager.createProblemDescriptor(
            anchor,
            TextRange(0, anchor.textLength),
            issue.message,
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            isOnTheFly,
            *fixes.toTypedArray(),
        )
    }

    private companion object {

        /**
         * 高亮期间只跑与单方法相关的规则；重复映射是工程级问题，交给体检面板。
         */
        val INSPECTION_RULES: List<ContractRule> = listOf(
            OrphanEndpointRule(),
            ParamNameMismatchRule(),
            PathVariableRule(),
            RequiredMismatchRule(),
        )
    }
}