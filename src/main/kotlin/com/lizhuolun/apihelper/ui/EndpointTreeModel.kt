package com.lizhuolun.apihelper.ui

import javax.swing.tree.DefaultTreeModel

/**
 * 工具窗口端点树模型。
 *
 * 按类全限定名分组，同一类下按 URL + HTTP 方法排序。
 *
 * @author lizhuolun
 * @date 2026/6/12
 */
class EndpointTreeModel : DefaultTreeModel(EndpointNode.group("Root")) {

    /**
     * 使用新的展示项列表重建树，保留展开状态由调用方处理。
     *
     * @param items 端点展示项列表
     */
    fun refresh(items: List<EndpointTreeItem>) {
        val root = EndpointNode.group("Root")
        val byClass = items
            .sortedWith(
                compareBy<EndpointTreeItem> { it.className }
                    .thenBy { it.url }
                    .thenBy { it.httpMethod.name }
                    .thenBy { it.methodName },
            )
            .groupBy { it.className }

        for ((className, list) in byClass) {
            val group = EndpointNode.group("$className (${list.size})")
            for (item in list) {
                group.add(EndpointNode.from(item))
            }
            root.add(group)
        }
        setRoot(root)
    }
}
