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
     * 使用新的展示项列表重建树，支持“按类分组”与“按模块 + 类分组”两种视图。
     *
     * @param items 端点展示项列表
     * @param groupByModule 是否先按模块分组
     */
    fun refresh(items: List<EndpointTreeItem>, groupByModule: Boolean = false) {
        val root = EndpointNode.group("Root")
        val sorted = items.sortedWith(
            compareBy<EndpointTreeItem> { if (groupByModule) it.moduleName else "" }
                .thenBy { it.className }
                .thenBy { it.moduleName }
                .thenBy { it.url }
                .thenBy { it.httpMethod.name }
                .thenBy { it.methodName },
        )

        if (!groupByModule) {
            appendClassGroups(root, sorted)
        } else {
            val byModule = sorted.groupBy { it.moduleName.ifBlank { "default" } }
            for ((modName, modItems) in byModule) {
                val modNode = EndpointNode.moduleGroup("$modName (${modItems.size})", modName)
                appendClassGroups(modNode, modItems)
                root.add(modNode)
            }
        }
        setRoot(root)
    }

    private fun appendClassGroups(parent: EndpointNode, items: List<EndpointTreeItem>) {
        val byClass = items.groupBy { it.className to it.moduleName }
        for ((key, list) in byClass) {
            val (className, moduleName) = key
            val group = EndpointNode.group("$className (${list.size})", moduleName)
            for (item in list) {
                group.add(EndpointNode.from(item))
            }
            parent.add(group)
        }
    }
}
