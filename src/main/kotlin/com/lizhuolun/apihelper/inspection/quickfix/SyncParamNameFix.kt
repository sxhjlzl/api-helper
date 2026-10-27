package com.lizhuolun.apihelper.inspection.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierList
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.codeStyle.CodeStyleManager
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.core.annotation.SpringAnnotations

/**
 * 把客户端侧参数注解上的名字同步为服务端侧期望的名字。
 *
 * 这是本版唯一的快速修复：它是「体检发现问题」到「一键修好」的闭环演示点，
 * 也是最不会引入语义风险的一种改写（只动注解属性，不动方法签名与调用方）。
 *
 * @param sourceName 当前注解上的名字，用于定位要修改的参数
 * @param targetName 目标名字
 * @author lizhuolun
 * @date 2026/10/27
 */
class SyncParamNameFix(
    private val sourceName: String,
    private val targetName: String,
) : LocalQuickFix {

    override fun getFamilyName(): String =
        ApiHelperBundle.message("quickfix.sync.param.name")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java) ?: return

        WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null, {
            val annotation = findAnnotation(method) ?: return@runWriteCommandAction
            val attributeName = attributeHolding(annotation) ?: return@runWriteCommandAction
            val factory = JavaPsiFacade.getElementFactory(project)
            val literal = factory.createExpressionFromText("\"$targetName\"", annotation)
            annotation.setDeclaredAttributeValue(attributeName, literal)
            CodeStyleManager.getInstance(project).reformat(annotation)
        }, method.containingFile)
    }

    /**
     * 在方法参数上查找名字等于 [sourceName] 的请求参数注解。
     */
    private fun findAnnotation(method: PsiMethod): PsiAnnotation? {
        for (parameter in method.parameterList.parameters) {
            val modifierList: PsiModifierList = parameter.modifierList ?: continue
            for (fqn in PARAMETER_ANNOTATIONS) {
                val annotation = modifierList.findAnnotation(fqn) ?: continue
                if (attributeHolding(annotation) != null) return annotation
            }
        }
        return null
    }

    /**
     * 判断注解的哪个属性承载了当前名字。
     *
     * @return `value`、`name` 或 null（表示不是这次要改的目标）
     */
    private fun attributeHolding(annotation: PsiAnnotation): String? {
        for (attributeName in NAME_ATTRIBUTES) {
            val current = annotation.findAttributeValue(attributeName) as? com.intellij.psi.PsiLiteralExpression
            if (current?.value == sourceName) return attributeName
        }
        return null
    }

    private companion object {

        /**
         * 参与同步的参数级注解。
         */
        val PARAMETER_ANNOTATIONS = listOf(
            SpringAnnotations.REQUEST_PARAM,
            SpringAnnotations.REQUEST_HEADER,
            SpringAnnotations.PATH_VARIABLE,
            SpringAnnotations.COOKIE_VALUE,
        )

        /**
         * 名字可能存放的属性，与提取器保持一致。
         */
        val NAME_ATTRIBUTES = listOf("value", "name")
    }
}