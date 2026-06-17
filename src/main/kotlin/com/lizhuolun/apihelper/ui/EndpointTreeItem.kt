package com.lizhuolun.apihelper.ui

import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPsiElementPointer
import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.core.HttpMethod
import com.lizhuolun.apihelper.core.HttpMappingInfo

/**
 * 工具窗口树中展示的一条端点记录，所有 PSI 属性已在构造时提取完毕，
 * 后续过滤、渲染不再访问 PSI，避免 read action 相关问题。
 *
 * @property url 完整 URL
 * @property httpMethod HTTP 方法
 * @property methodName 方法名
 * @property className 类全限定名
 * @property kind 端点类别
 * @property pointer 指向 PsiMethod 的智能指针，用于跳转
 * @author lizhuolun
 * @date 2026/6/12
 */
data class EndpointTreeItem(
    val url: String,
    val httpMethod: HttpMethod,
    val methodName: String,
    val className: String,
    val kind: EndpointKind,
    val pointer: SmartPsiElementPointer<PsiMethod>?,
) {
    companion object {

        /**
         * 从 HttpMappingInfo 与已解析方法创建展示项。
         *
         * 必须在 read action 内调用，因为会访问 PSI。
         *
         * @param info 端点映射
         * @param method 已从智能指针解析出的有效方法
         * @return 展示项
         */
        fun from(info: HttpMappingInfo, method: PsiMethod): EndpointTreeItem =
            EndpointTreeItem(
                url = info.url,
                httpMethod = info.httpMethod,
                methodName = method.name,
                className = method.containingClass?.qualifiedName ?: "Unknown",
                kind = info.kind,
                pointer = info.methodPointer,
            )
    }
}
