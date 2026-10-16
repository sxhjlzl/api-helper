package com.lizhuolun.apihelper.ui

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Computable
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.cache.BilateralMappingCacheService
import com.lizhuolun.apihelper.config.ApplicationConfigReader
import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.core.HttpMethod
import com.lizhuolun.apihelper.core.annotation.SpringAnnotations
import com.lizhuolun.apihelper.settings.ApiHelperSettings
import com.lizhuolun.apihelper.ui.debug.CurlCommandBuilder
import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.tree.TreePath

/**
 * 端点列表树组件，仅负责树形展示与交互。
 *
 * 搜索、工具栏等外层容器由 [EndpointToolWindowPanel] 统一组织，
 * 避免每个树实例重复创建搜索框，并保证顶部布局与参考设计一致。
 *
 * @param project 当前工程
 * @param kind 本树展示的端点类别，用于计算对端跳转
 * @author lizhuolun
 * @date 2026/6/16
 */
class EndpointTree(
    private val project: Project,
    private val kind: EndpointKind,
) : JPanel(BorderLayout()) {

    private val treeModel = EndpointTreeModel()
    private val tree = Tree(treeModel).apply {
        isRootVisible = false
        showsRootHandles = true
        emptyText.text = ApiHelperBundle.message("toolwindow.empty.text")
        cellRenderer = EndpointTreeCellRenderer()
        rowHeight = 0
    }

    /**
     * 当前完整端点数据，用于过滤时重建树。
     */
    private var allItems: List<EndpointTreeItem> = emptyList()

    /**
     * 当前过滤文本。
     */
    private var currentFilterText: String = ""

    /**
     * 是否先按模块分组。
     */
    private var groupByModule: Boolean = false

    /**
     * 配对状态过滤条件。
     */
    private var pairingFilter: EndpointPairingFilter = EndpointPairingFilter.ALL

    /**
     * 当前过滤后的条目数量。
     */
    private var filteredCount: Int = 0

    /**
     * 数量变化回调，参数为 (总数, 过滤后数量)。
     */
    var onCountsChanged: ((total: Int, filtered: Int) -> Unit)? = null

    /**
     * 调试接口回调，由工具窗口主面板切换到调试 Tab 并预填请求。
     */
    var onDebugRequested: ((item: EndpointTreeItem) -> Unit)? = null

    init {
        isOpaque = false
        TreeSpeedSearch.installOn(tree, true) { path ->
            val node = path.lastPathComponent as? EndpointNode ?: return@installOn ""
            val item = node.item
            if (item != null) {
                "${item.httpMethod.name} ${item.url} ${item.methodName} ${item.className}"
            } else {
                node.title
            }
        }
        add(JBScrollPane(tree).apply {
            border = BorderFactory.createEmptyBorder()
        }, BorderLayout.CENTER)
        setupListeners()
    }

    /**
     * 刷新整棵树。
     *
     * 调用方应确保在 read action 内传入 endpoints，或保证其中的 PSI 属性已可安全访问。
     * 本方法会在 read action 内一次性解析智能指针并提取展示所需字段，
     * 后续过滤与渲染不再触碰 PSI。
     *
     * @param endpoints 新的端点列表
     */
    fun refresh(endpoints: List<HttpMappingInfo>) {
        val cache = BilateralMappingCacheService.of(project)
        val items = ApplicationManager.getApplication().runReadAction(Computable {
            endpoints.mapNotNull { info ->
                val method = info.resolveMethod() ?: return@mapNotNull null
                EndpointTreeItem.from(info, method, hasCounterpart = cache.hasCounterpart(info))
            }
        })
        allItems = items
        rebuildFilteredTree()
    }

    /**
     * 使用指定文本过滤树。
     *
     * @param text 过滤文本
     */
    fun applyFilter(text: String) {
        currentFilterText = text
        rebuildFilteredTree()
    }

    /**
     * 设置是否先按模块分组并刷新树。
     *
     * @param enabled 为 true 时按模块 -> 类两级分组
     */
    fun setGroupByModule(enabled: Boolean) {
        if (groupByModule == enabled) return
        groupByModule = enabled
        rebuildFilteredTree()
    }

    /**
     * 设置配对状态过滤模式并刷新树。
     *
     * @param filter 配对状态过滤枚举
     */
    fun setPairingFilter(filter: EndpointPairingFilter) {
        if (pairingFilter == filter) return
        pairingFilter = filter
        rebuildFilteredTree()
    }

    private fun rebuildFilteredTree() {
        val query = EndpointSearchQuery.parse(currentFilterText)
        val filtered = allItems.filter { item ->
            val matchesQuery = query.isBlank || query.matches(item)
            val matchesPairing = when (pairingFilter) {
                EndpointPairingFilter.ALL -> true
                EndpointPairingFilter.PAIRED_ONLY -> item.hasCounterpart
                EndpointPairingFilter.UNPAIRED_ONLY -> !item.hasCounterpart
            }
            matchesQuery && matchesPairing
        }
        filteredCount = filtered.size
        onCountsChanged?.invoke(allItems.size, filteredCount)
        treeModel.refresh(filtered, groupByModule = groupByModule)
        expandAll()
    }

    private fun setupListeners() {
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    navigateSelected()
                    e.consume()
                }
            }
        })

        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                navigateSelected()
                return true
            }
        }.installOn(tree)

        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                showEndpointPopup(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                showEndpointPopup(e)
            }
        })
    }

    /**
     * 展开树中所有分组节点。
     */
    fun expandAll() {
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row++
        }
    }

    /**
     * 收起树中所有类分组节点。
     */
    fun collapseAll() {
        for (i in tree.rowCount - 1 downTo 0) {
            tree.collapseRow(i)
        }
    }

    /**
     * 获取当前 Tab 下完整的端点总数。
     *
     * @return 端点总数
     */
    fun getTotalCount(): Int = allItems.size

    /**
     * 获取当前过滤后显示的端点数量。
     *
     * @return 过滤后端点数量
     */
    fun getFilteredCount(): Int = filteredCount

    /**
     * 判断当前树数据中是否包含给定方法。
     *
     * @param method 目标 PSI 方法
     * @return 树中包含该方法时返回 true
     */
    fun containsMethod(method: PsiMethod): Boolean =
        ApplicationManager.getApplication().runReadAction(Computable {
            val className = method.containingClass?.qualifiedName
            allItems.any { item ->
                val resolved = item.pointer?.element
                resolved == method || (className != null && item.className == className && item.methodName == method.name)
            }
        })

    /**
     * 在当前可见树节点中定位并选中指定方法。
     *
     * @param method 目标 PSI 方法
     * @return 成功定位并选中时返回 true
     */
    fun selectMethod(method: PsiMethod): Boolean {
        val root = treeModel.root as? EndpointNode ?: return false
        val targetClassName = ApplicationManager.getApplication().runReadAction(Computable {
            method.containingClass?.qualifiedName
        })
        val matchedPath = findMethodPathRecursive(listOf(root), method, targetClassName) ?: return false
        val treePath = TreePath(matchedPath.toTypedArray())
        if (matchedPath.size > 1) {
            tree.expandPath(TreePath(matchedPath.dropLast(1).toTypedArray()))
        }
        tree.selectionPath = treePath
        tree.scrollPathToVisible(treePath)
        tree.requestFocusInWindow()
        return true
    }

    private fun findMethodPathRecursive(
        currentChain: List<EndpointNode>,
        method: PsiMethod,
        targetClassName: String?,
    ): List<EndpointNode>? {
        val current = currentChain.last()
        if (current.item != null) {
            val matched = ApplicationManager.getApplication().runReadAction(Computable {
                val resolved = current.pointer?.element
                resolved == method ||
                    (targetClassName != null &&
                        current.item.className == targetClassName &&
                        current.item.methodName == method.name)
            })
            return if (matched) currentChain else null
        }
        for (i in 0 until current.childCount) {
            val child = current.getChildAt(i) as? EndpointNode ?: continue
            val found = findMethodPathRecursive(currentChain + child, method, targetClassName)
            if (found != null) return found
        }
        return null
    }

    private fun navigateSelected() {
        val pointer = (tree.lastSelectedPathComponent as? EndpointNode)?.pointer ?: return
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val method = ApplicationManager.getApplication().runReadAction(Computable {
                pointer.element?.takeIf { it.isValid }
            }) ?: return@invokeLater
            navigateToMethod(method)
        }
    }

    private fun showEndpointPopup(e: MouseEvent) {
        if (!e.isPopupTrigger) return
        val path = tree.getPathForLocation(e.x, e.y) ?: return
        val node = path.lastPathComponent as? EndpointNode ?: return
        if (node.item == null) return
        tree.selectionPath = path
        buildPopupMenu().show(tree, e.x, e.y)
    }

    private fun buildPopupMenu(): JPopupMenu {
        val group = DefaultActionGroup()
        if (kind == EndpointKind.CONTROLLER) {
            group.add(popupAction(
                ApiHelperBundle.message("toolwindow.action.debug.endpoint"),
                AllIcons.Actions.Execute,
            ) { debugSelectedEndpoint() })
            group.addSeparator()
        }
        group.add(popupAction(
            ApiHelperBundle.message("toolwindow.action.navigate.source"),
            AllIcons.Actions.EditSource,
        ) { navigateSelected() })
        group.add(popupAction(
            ApiHelperBundle.message("toolwindow.action.navigate.counterpart"),
            AllIcons.Actions.Forward,
        ) { navigateToCounterpart() })
        group.addSeparator()
        group.add(popupAction(
            ApiHelperBundle.message("toolwindow.action.copy.url"),
            AllIcons.Actions.Copy,
        ) { copySelectedUrl() })
        group.add(popupAction(
            ApiHelperBundle.message("toolwindow.action.copy.curl"),
            AllIcons.Actions.Copy,
        ) { copySelectedCurl() })
        group.add(popupAction(
            ApiHelperBundle.message("toolwindow.action.copy.api.doc"),
            AllIcons.Actions.Copy,
        ) { copySelectedAiMarkdown() })
        return ActionManager.getInstance()
            .createActionPopupMenu(ActionPlaces.POPUP, group)
            .component
    }

    private fun popupAction(
        text: String,
        icon: javax.swing.Icon,
        action: () -> Unit,
    ): AnAction =
        object : AnAction(text, null, icon) {
            override fun actionPerformed(e: AnActionEvent) {
                action()
            }
        }

    private fun debugSelectedEndpoint() {
        val node = tree.lastSelectedPathComponent as? EndpointNode ?: return
        val item = node.item ?: return
        onDebugRequested?.invoke(item)
    }

    private fun copySelectedUrl() {
        val node = tree.lastSelectedPathComponent as? EndpointNode ?: return
        val url = node.item?.url ?: return
        copyText(url)
        notifyBalloon(ApiHelperBundle.message("notification.url.copied", url))
    }

    private fun copySelectedCurl() {
        val node = tree.lastSelectedPathComponent as? EndpointNode ?: return
        val item = node.item ?: return
        val pointer = node.pointer
        ApplicationManager.getApplication().executeOnPooledThread {
            val curl = ApplicationManager.getApplication().runReadAction(Computable {
                if (project.isDisposed) return@Computable null
                val method = pointer?.element?.takeIf { it.isValid }
                buildCurlForEndpoint(item, method)
            }) ?: return@executeOnPooledThread
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                copyText(curl)
                notifyBalloon(ApiHelperBundle.message("debug.notification.curl.copied"))
            }
        }
    }

    internal fun buildCurlForEndpoint(item: EndpointTreeItem, method: PsiMethod?): String =
        Companion.buildCurlForEndpoint(item, method)

    private fun copySelectedAiMarkdown() {
        val node = tree.lastSelectedPathComponent as? EndpointNode ?: return
        val item = node.item ?: return
        val pointer = node.pointer
        ApplicationManager.getApplication().executeOnPooledThread {
            val markdown = ApplicationManager.getApplication().runReadAction(Computable {
                if (project.isDisposed) return@Computable null
                val method = pointer?.element?.takeIf { it.isValid }
                buildAiMarkdown(item, method)
            }) ?: return@executeOnPooledThread
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                copyText(markdown)
                notifyBalloon(ApiHelperBundle.message("toolwindow.notification.api.doc.copied"))
            }
        }
    }

    private fun copyText(text: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }

    private fun notifyBalloon(content: String) {
        if (project.isDisposed) return
        NotificationGroupManager.getInstance()
            .getNotificationGroup("ApiHelper")
            .createNotification(content, NotificationType.INFORMATION)
            .notify(project)
    }

    private fun navigateToCounterpart() {
        val node = tree.lastSelectedPathComponent as? EndpointNode ?: return
        val pointer = node.pointer ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            val method = ApplicationManager.getApplication().runReadAction(Computable {
                pointer.element?.takeIf { it.isValid }
            }) ?: return@executeOnPooledThread

            val targets = when (kind) {
                EndpointKind.CONTROLLER -> BilateralMappingCacheService.of(project).findClientTargets(method)
                else -> BilateralMappingCacheService.of(project).findControllerTargets(method)
            }

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                val valid = ApplicationManager.getApplication().runReadAction(Computable {
                    targets.mapNotNull { it.resolveMethod() }
                        .filterIsInstance<NavigatablePsiElement>()
                })
                when (valid.size) {
                    0 -> notifyBalloon(ApiHelperBundle.message("notification.no.targets"))
                    1 -> valid[0].navigate(true)
                    else -> JBPopupFactory.getInstance()
                        .createPopupChooserBuilder(valid)
                        .setTitle(ApiHelperBundle.message("toolwindow.action.navigate.counterpart"))
                        .setItemSelectedCallback { it?.navigate(true) }
                        .createPopup()
                        .showInFocusCenter()
                }
            }
        }
    }

    private fun navigateToMethod(method: PsiMethod) {
        ApplicationManager.getApplication().runReadAction(Computable {
            val file = method.containingFile?.virtualFile ?: return@Computable null
            val offset = method.textOffset
            OpenFileDescriptor(project, file, offset)
        })?.let {
            it.navigate(true)
        }
    }

    internal fun buildAiMarkdown(item: EndpointTreeItem, method: PsiMethod?): String =
        Companion.buildAiMarkdown(item, method)

    companion object {

        private fun effectiveHttpMethod(item: EndpointTreeItem): String =
            if (item.httpMethod.name == "ANY") "GET" else item.httpMethod.name

        internal fun buildCurlForEndpoint(item: EndpointTreeItem, method: PsiMethod?): String {
            val settings = ApiHelperSettings.getInstance()
            val selectedEnv = settings.parseEnvironments()
                .firstOrNull { it.name == settings.state.selectedEnvironmentName }
            val baseUrl = selectedEnv?.baseUrl?.trimEnd('/') ?: run {
                val port = method?.containingClass?.let { cls ->
                    val props = ApplicationConfigReader.readConfigForClass(cls, settings.state.manualActiveProfile)
                    ApplicationConfigReader.readServerPort(props)
                } ?: 8080
                "http://localhost:$port"
            }
            val pathPart = if (item.url.startsWith("/")) item.url else "/${item.url}"
            val fullUrl = if (item.url.startsWith("http://") || item.url.startsWith("https://")) {
                item.url
            } else {
                "$baseUrl$pathPart"
            }

            val pathParams = mutableListOf<Pair<String, String>>()
            val queryParams = mutableListOf<Pair<String, String>>()
            val headerParams = mutableListOf<Pair<String, String>>()
            val cookieParams = mutableListOf<Pair<String, String>>()
            val bodyCandidateParams = mutableListOf<PsiParameter>()
            val useQueryForUnannotated = item.httpMethod == HttpMethod.GET ||
                    item.httpMethod == HttpMethod.DELETE ||
                    item.httpMethod == HttpMethod.HEAD ||
                    item.httpMethod == HttpMethod.ANY

            if (method != null) {
                for (parameter in method.parameterList.parameters) {
                    when {
                        EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.PATH_VARIABLE) -> {
                            val p = EndpointParameterResolver.annotatedParameter(parameter, SpringAnnotations.PATH_VARIABLE)
                            pathParams += p.name to p.sampleValue
                        }
                        EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.REQUEST_PARAM) -> {
                            val p = EndpointParameterResolver.annotatedParameter(parameter, SpringAnnotations.REQUEST_PARAM)
                            queryParams += p.name to p.sampleValue
                        }
                        EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.REQUEST_HEADER) -> {
                            val p = EndpointParameterResolver.annotatedParameter(parameter, SpringAnnotations.REQUEST_HEADER)
                            headerParams += p.name to p.sampleValue
                        }
                        EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.COOKIE_VALUE) -> {
                            val p = EndpointParameterResolver.annotatedParameter(parameter, SpringAnnotations.COOKIE_VALUE)
                            cookieParams += p.name to p.sampleValue
                        }
                        EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.REQUEST_BODY) -> {
                            bodyCandidateParams += parameter
                        }
                        EndpointParameterResolver.hasQueryMapAnnotation(parameter) -> {
                            queryParams += EndpointParameterResolver.queryParameters(parameter)
                                .filter { !it.name.contains('.') && !it.name.contains('[') }
                                .map { it.name to it.sampleValue }
                        }
                        useQueryForUnannotated -> {
                            queryParams += EndpointParameterResolver.queryParameters(parameter)
                                .filter { !it.name.contains('.') && !it.name.contains('[') }
                                .map { it.name to it.sampleValue }
                        }
                        else -> {
                            bodyCandidateParams += parameter
                        }
                    }
                }
            }
            val bodyJson = EndpointParameterResolver.buildJsonSampleForParameters(bodyCandidateParams)
            val mergedHeaders = CurlCommandBuilder.mergeHeaders(settings.parseGlobalHeaders(), headerParams)
            return CurlCommandBuilder.build(
                method = effectiveHttpMethod(item),
                rawUrl = fullUrl,
                pathParams = pathParams,
                queryParams = queryParams,
                headers = mergedHeaders,
                cookies = cookieParams,
                contentType = if (bodyJson.isNotBlank()) "application/json; charset=UTF-8" else "",
                bodyText = bodyJson,
            )
        }

        internal fun buildAiMarkdown(item: EndpointTreeItem, method: PsiMethod?): String {
            val pathParams = linkedMapOf<String, EndpointDocParam>()
            extractPathVariables(item.url).forEach {
                pathParams[it] = EndpointDocParam(it, "", true, "")
            }
            val queryParams = mutableListOf<EndpointDocParam>()
            val headerParams = mutableListOf<EndpointDocParam>()
            val cookieParams = mutableListOf<EndpointDocParam>()
            val bodyParams = mutableListOf<EndpointDocParam>()
            val description = method?.let(EndpointParameterResolver::methodDescription).orEmpty()
            val responseParams = method?.returnType
                ?.let(EndpointParameterResolver::responseParameters)
                ?.map(::docParam)
                .orEmpty()
            val useQueryForUnannotated = item.httpMethod == HttpMethod.GET ||
                    item.httpMethod == HttpMethod.DELETE ||
                    item.httpMethod == HttpMethod.HEAD ||
                    item.httpMethod == HttpMethod.ANY

            if (method != null) {
                for (parameter in method.parameterList.parameters) {
                    when {
                        EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.PATH_VARIABLE) -> {
                            val param = docParam(parameter, SpringAnnotations.PATH_VARIABLE)
                            pathParams[param.name] = param
                        }
                        EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.REQUEST_PARAM) -> {
                            queryParams += docParam(parameter, SpringAnnotations.REQUEST_PARAM)
                        }
                        EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.REQUEST_HEADER) -> {
                            headerParams += docParam(parameter, SpringAnnotations.REQUEST_HEADER)
                        }
                        EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.COOKIE_VALUE) -> {
                            cookieParams += docParam(parameter, SpringAnnotations.COOKIE_VALUE)
                        }
                        EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.REQUEST_BODY) -> {
                            bodyParams += EndpointParameterResolver
                                .bodyParameters(parameter, SpringAnnotations.REQUEST_BODY)
                                .map(::docParam)
                        }
                        EndpointParameterResolver.hasQueryMapAnnotation(parameter) -> {
                            queryParams += EndpointParameterResolver.queryParameters(parameter).map(::docParam)
                        }
                        useQueryForUnannotated -> {
                            queryParams += EndpointParameterResolver.queryParameters(parameter).map(::docParam)
                        }
                        else -> {
                            bodyParams += EndpointParameterResolver.bodyParameters(parameter).map(::docParam)
                        }
                    }
                }
            }

            return buildString {
                appendLine("# ${ApiHelperBundle.message("toolwindow.ai.doc.title")}")
                appendLine()
                appendLine("## ${ApiHelperBundle.message("toolwindow.ai.doc.basic")}")
                appendLine()
                appendLine("- ${ApiHelperBundle.message("toolwindow.ai.doc.http.method")}: `${effectiveHttpMethod(item)}`")
                appendLine("- ${ApiHelperBundle.message("toolwindow.ai.doc.url")}: `${item.url}`")
                appendLine("- ${ApiHelperBundle.message("toolwindow.ai.doc.backend.method")}: `${item.className}#${item.methodName}`")
                if (item.moduleName.isNotBlank()) {
                    appendLine("- ${ApiHelperBundle.message("toolwindow.ai.doc.module")}: `${item.moduleName}`")
                }
                appendLine()
                if (description.isNotBlank()) {
                    appendLine("## ${ApiHelperBundle.message("toolwindow.ai.doc.description")}")
                    appendLine()
                    appendLine(description)
                    appendLine()
                }
                appendParamTable(ApiHelperBundle.message("toolwindow.ai.doc.path.params"), pathParams.values.toList())
                appendParamTable(ApiHelperBundle.message("toolwindow.ai.doc.query.params"), queryParams)
                appendParamTable(ApiHelperBundle.message("toolwindow.ai.doc.header.params"), headerParams)
                appendParamTable(ApiHelperBundle.message("toolwindow.ai.doc.cookie.params"), cookieParams)
                appendParamTable(ApiHelperBundle.message("toolwindow.ai.doc.request.body"), bodyParams)
                appendParamTable(ApiHelperBundle.message("toolwindow.ai.doc.response.body"), responseParams)
            }
        }

        private fun StringBuilder.appendParamTable(title: String, params: List<EndpointDocParam>) {
            appendLine("## $title")
            appendLine()
            if (params.isEmpty()) {
                appendLine(ApiHelperBundle.message("toolwindow.ai.doc.none"))
                appendLine()
                return
            }
            appendLine(ApiHelperBundle.message("toolwindow.ai.doc.param.header"))
            appendLine("| --- | --- | --- | --- |")
            for (param in params) {
                val type = param.type.ifBlank { ApiHelperBundle.message("toolwindow.ai.doc.unknown") }
                val required = if (param.required) {
                    ApiHelperBundle.message("toolwindow.ai.doc.required.yes")
                } else {
                    ApiHelperBundle.message("toolwindow.ai.doc.required.no")
                }
                appendLine("| `${param.name}` | `$type` | $required | ${escapeMarkdownTable(param.description)} |")
            }
            appendLine()
        }

        private fun docParam(
            parameter: PsiParameter,
            annotationFqn: String,
        ): EndpointDocParam =
            docParam(EndpointParameterResolver.annotatedParameter(parameter, annotationFqn))

        private fun docParam(parameter: EndpointParameterDescriptor): EndpointDocParam =
            EndpointDocParam(parameter.name, parameter.type, parameter.required, parameter.description)

        private fun escapeMarkdownTable(text: String): String =
            text.replace("|", "\\|")
                .replace("\r", " ")
                .replace("\n", " ")

        private fun extractPathVariables(url: String): List<String> =
            Regex("\\{([^}/]+)}")
                .findAll(url)
                .map { it.groupValues[1] }
                .toList()
    }

    private data class EndpointDocParam(
        val name: String,
        val type: String,
        val required: Boolean,
        val description: String,
    )
}

/**
 * 端点配对状态过滤模式。
 */
enum class EndpointPairingFilter {
    /**
     * 展示全部端点
     **/
    ALL,

    /**
     * 仅展示存在对端匹配的端点
     **/
    PAIRED_ONLY,

    /**
     * 仅展示无对端匹配的孤儿端点
     **/
    UNPAIRED_ONLY,
}
