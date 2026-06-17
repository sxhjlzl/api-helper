package com.lizhuolun.apihelper.ui

import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPsiElementPointer
import javax.swing.tree.DefaultMutableTreeNode

/**
 * 工具窗口树节点，可表示一个类分组或一个具体端点。
 *
 * @property title 节点展示文本
 * @property item 端点展示数据；为 null 时表示类分组节点
 * @property pointer 指向 PsiMethod 的智能指针；为 null 时表示分组节点
 * @author lizhuolun
 * @date 2026/6/12
 */
class EndpointNode(
    val title: String,
    val item: EndpointTreeItem? = null,
    val pointer: SmartPsiElementPointer<PsiMethod>? = null,
) : DefaultMutableTreeNode() {

    override fun toString(): String = title

    companion object {

        /**
         * 从 EndpointTreeItem 创建端点叶子节点，标题包含 HTTP 方法、URL 与方法名。
         *
         * @param item 端点展示数据
         * @return 叶子节点
         */
        fun from(item: EndpointTreeItem): EndpointNode {
            val display = "[${item.httpMethod.name}] ${item.url}  →  ${item.methodName}"
            return EndpointNode(display, item, item.pointer)
        }

        /**
         * 创建类分组节点。
         *
         * @param className 类全限定名
         * @return 分组节点
         */
        fun group(className: String): EndpointNode = EndpointNode(className)
    }
}
