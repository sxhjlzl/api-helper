package com.lizhuolun.apihelper.ui.debug

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Computable
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.net.JdkProxyProvider
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.config.ApplicationConfigReader
import com.lizhuolun.apihelper.core.HttpMethod
import com.lizhuolun.apihelper.core.annotation.SpringAnnotations
import com.lizhuolun.apihelper.settings.ApiHelperSettings
import com.lizhuolun.apihelper.ui.EndpointParameterResolver
import com.lizhuolun.apihelper.ui.EndpointTreeItem
import com.lizhuolun.apihelper.ui.component.ApiHelperUiStyle
import com.lizhuolun.apihelper.ui.component.RoundedBadgeLabel
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpCookie
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import javax.swing.BorderFactory
import javax.swing.DefaultCellEditor
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.TableCellRenderer

/**
 * 接口调试面板（对应工具窗口一级标签页：调试）。
 *
 * 采用上下分栏结构：
 * - 顶部：紧凑请求地址栏（环境切换、HTTP 方法下拉、URL 输入框、历史/cURL/重置图标按钮、发送按钮）
 * - 上半区（请求配置）：胶囊式子标签栏（Query / Body / Path / Headers / Cookies）+ 带启用复选框的网格参数表
 * - 下半区（响应展示）：胶囊式子标签栏（Body / Cookies / Headers + 右侧状态与体积徽章）+ 快捷工具栏（清空 / 自动换行 / 保存 / 复制 / 格式下拉）+ 带行号代码区
 *
 * @param project 当前工程
 * @author lizhuolun
 * @date 2026/6/16
 */
class EndpointDebugPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val log = thisLogger()

    /**
     * HttpClient 按当前配置的连接超时懒加载缓存，超时配置变更后重建；
     * 代理选择器对接 IDE 全局代理配置，保证企业内网环境可达。
     **/
    private var cachedHttpClient: HttpClient? = null
    private var cachedHttpClientConnectTimeout: Int = -1

    /**
     * 当前在途请求的 Future，用于支持手动取消；为 null 表示空闲。
     **/
    @Volatile
    private var currentRequestFuture: CompletableFuture<HttpResponse<InputStream>>? = null

    /**
     * 是否正在发送请求，决定主按钮显示"发送"还是"停止"。
     **/
    private var isSending = false

    private val environmentBox = ComboBox<EnvironmentOption>().apply {
        ApiHelperUiStyle.applyComboBox(this, width = 90, bold = false)
        toolTipText = ApiHelperBundle.message("debug.env.tooltip")
        addActionListener { onEnvironmentSelected() }
    }
    private val methodBox = ComboBox(arrayOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")).apply {
        selectedItem = "GET"
        ApiHelperUiStyle.applyComboBox(this, width = 76, bold = false)
    }
    private val urlField = JBTextField().apply {
        emptyText.text = ApiHelperBundle.message("debug.url.placeholder")
        ApiHelperUiStyle.applyTextField(this)
        addActionListener { onPrimaryAction() }
    }
    private val historyButton = IconActionButton(AllIcons.Vcs.History, ApiHelperBundle.message("debug.history.title")).apply {
        addActionListener { showHistoryPopup() }
    }
    private val copyCurlButton = IconActionButton(AllIcons.Actions.Copy, ApiHelperBundle.message("toolwindow.action.copy.curl")).apply {
        addActionListener { copyCurrentCurl() }
    }
    private val importCurlButton = IconActionButton(AllIcons.Actions.MenuPaste, ApiHelperBundle.message("debug.action.import.curl")).apply {
        addActionListener { importCurlFromClipboard() }
    }
    private val resetDraftButton = IconActionButton(AllIcons.Actions.Rollback, ApiHelperBundle.message("debug.action.reset.draft")).apply {
        addActionListener { resetCurrentEndpointDraft() }
    }
    private val sendButton = SendButton(ApiHelperBundle.message("debug.action.send")).apply {
        addActionListener { onPrimaryAction() }
    }

    private val requestStatusLabel = JBLabel(ApiHelperBundle.message("debug.status.ready")).apply {
        foreground = UIUtil.getContextHelpForeground()
        font = ApiHelperUiStyle.smallFont(bold = true)
    }
    private val responseMetaBadge = RoundedBadgeLabel().apply {
        isVisible = false
    }

    private val queryModel = DebugParameterTableModel(showDeleteColumn = true, showEnabledColumn = true)
    private val pathModel = DebugParameterTableModel(showDeleteColumn = true, showEnabledColumn = true)
    private val headerModel = DebugParameterTableModel(showDeleteColumn = true, showEnabledColumn = true)
    private val cookieModel = DebugParameterTableModel(showDeleteColumn = true, showEnabledColumn = true)
    private val formDataModel = BodyFormDataTableModel()
    private val urlEncodedModel = DebugParameterTableModel(showDeleteColumn = true, showEnabledColumn = true)

    private val requestTabPills = LinkedHashMap<String, SectionTabPill>()
    private val requestCardLayout = CardLayout()
    private val requestCardPanel = JPanel(requestCardLayout).apply {
        isOpaque = false
    }

    private val responseTabPills = LinkedHashMap<String, SectionTabPill>()
    private val responseCardLayout = CardLayout()
    private val responseCardPanel = JPanel(responseCardLayout).apply {
        isOpaque = false
    }

    private val bodyTypePills = mutableMapOf<BodyType, BodyTypePill>()
    private val bodyCardLayout = CardLayout()
    private val bodyContentPanel = JPanel(bodyCardLayout)
    private var currentServerPort = DEFAULT_SERVER_PORT
    private var currentEndpointItem: EndpointTreeItem? = null
    private var currentEndpointRelativeUrl: String = ""
    private var updatingEnvironments: Boolean = false
    private var selectedBodyType = BodyType.NONE

    private val requestBodyArea = JBTextArea().apply {
        font = ApiHelperUiStyle.codeFont()
        lineWrap = false
        border = BorderFactory.createEmptyBorder(JBUI.scale(6), JBUI.scale(8), JBUI.scale(6), JBUI.scale(8))
    }
    private var binaryPath: String = ""
    private val binaryPathLabel = JBLabel(ApiHelperBundle.message("debug.body.binary.placeholder")).apply {
        foreground = UIUtil.getContextHelpForeground()
        font = ApiHelperUiStyle.labelFont()
    }

    private val responseBodyArea = readOnlyCodeArea()
    private val responseHeaderArea = readOnlyCodeArea()
    private val responseCookieArea = readOnlyCodeArea()
    private val responseBodyTypeBox = ComboBox(ResponseBodyType.entries.toTypedArray()).apply {
        selectedItem = ResponseBodyType.JSON
        ApiHelperUiStyle.applyComboBox(this, width = 88, bold = false)
        addActionListener { renderResponseBody() }
    }
    private var lastResponseBody: String = ""

    /**
     * 调试请求历史新增时的回调，用于通知顶部“请求历史”Tab 同步刷新。
     */
    var onHistoryUpdated: (() -> Unit)? = null

    init {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        border = BorderFactory.createEmptyBorder()

        add(buildRequestBar(), BorderLayout.NORTH)

        val splitter = OnePixelSplitter(true, "ApiHelper.DebugRequestResponseSplitter", 0.46f).apply {
            firstComponent = buildRequestSection()
            secondComponent = buildResponseSection()
        }
        add(splitter, BorderLayout.CENTER)

        queryModel.addTableModelListener { updateRequestTabTitles() }
        pathModel.addTableModelListener { updateRequestTabTitles() }
        headerModel.addTableModelListener { updateRequestTabTitles() }
        cookieModel.addTableModelListener { updateRequestTabTitles() }

        selectRequestTab(TAB_QUERY)
        selectResponseTab(RESP_TAB_BODY)
        selectBodyType(BodyType.NONE)
        reloadEnvironments()
    }

    /**
     * 重新加载环境选择器选项（Settings 应用后或初始化时调用）。
     */
    fun reloadEnvironments() {
        updatingEnvironments = true
        try {
            val settings = ApiHelperSettings.getInstance()
            val savedName = settings.state.selectedEnvironmentName
            val envs = settings.parseEnvironments()
            environmentBox.removeAllItems()
            val autoOption = EnvironmentOption("", ApiHelperBundle.message("debug.env.auto"), null)
            environmentBox.addItem(autoOption)
            var matched: EnvironmentOption = autoOption
            for (env in envs) {
                val option = EnvironmentOption(env.name, env.name, env.baseUrl)
                environmentBox.addItem(option)
                if (env.name == savedName) {
                    matched = option
                }
            }
            environmentBox.selectedItem = matched
            environmentBox.isVisible = envs.isNotEmpty()
        } finally {
            updatingEnvironments = false
        }
    }

    /**
     * 从端点树加载一个接口到调试面板。
     *
     * @param item 端点展示项
     */
    fun loadEndpoint(item: EndpointTreeItem) {
        saveCurrentDraftIfLoaded()
        ApplicationManager.getApplication().executeOnPooledThread {
            if (project.isDisposed) return@executeOnPooledThread
            val serverPort = resolveServerPort(item)
            val params = resolveDebugParams(item)
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                applyLoadedEndpoint(item, serverPort, params)
            }
        }
    }

    private fun applyLoadedEndpoint(item: EndpointTreeItem, serverPort: Int, params: DebugAutoParams) {
        currentEndpointItem = item
        currentEndpointRelativeUrl = item.url
        currentServerPort = serverPort
        reloadEnvironments()

        val savedDraft = DebugDraftService.of(project).getDraft(endpointDraftKey(item))
        if (savedDraft != null) {
            applySavedDraft(item, savedDraft)
            return
        }

        methodBox.selectedItem = if (item.httpMethod == HttpMethod.ANY) "GET" else item.httpMethod.name
        urlField.text = normalizeInitialUrl(item.url, currentServerPort)
        pathModel.setPairs(pathPairs(item.url, params.path))
        queryModel.setPairs(params.query)
        headerModel.setPairs(params.headers)
        cookieModel.setPairs(params.cookies)
        formDataModel.clear()
        urlEncodedModel.clear()
        binaryPath = ""
        binaryPathLabel.text = ApiHelperBundle.message("debug.body.binary.placeholder")
        binaryPathLabel.foreground = UIUtil.getContextHelpForeground()
        requestBodyArea.text = params.bodyText
        if (params.bodyText.isNotBlank()) {
            selectBodyType(BodyType.JSON)
            selectRequestTab(TAB_BODY)
        } else {
            selectBodyType(BodyType.NONE)
            selectRequestTab(TAB_QUERY)
        }
        requestStatusLabel.text = ApiHelperBundle.message("debug.status.loaded", item.methodName)
        requestStatusLabel.foreground = UIUtil.getContextHelpForeground()
        responseMetaBadge.isVisible = false
    }

    private fun applySavedDraft(item: EndpointTreeItem, draft: EndpointDebugDraft) {
        methodBox.selectedItem = draft.method.ifBlank { "GET" }
        urlField.text = draft.url
        pathModel.setPairs(draft.path)
        queryModel.setPairs(draft.query)
        headerModel.setPairs(draft.headers)
        cookieModel.setPairs(draft.cookies)
        formDataModel.clear()
        draft.formData.forEachIndexed { idx, row ->
            formDataModel.setValueAt(row.enabled, idx, 0)
            formDataModel.setValueAt(row.name, idx, 1)
            formDataModel.setValueAt(row.value, idx, 2)
            formDataModel.setValueAt(row.type, idx, 3)
        }
        urlEncodedModel.setPairs(draft.urlEncoded)
        binaryPath = draft.binaryPath
        if (binaryPath.isNotBlank()) {
            binaryPathLabel.text = binaryPath
            binaryPathLabel.foreground = UIUtil.getLabelForeground()
        } else {
            binaryPathLabel.text = ApiHelperBundle.message("debug.body.binary.placeholder")
            binaryPathLabel.foreground = UIUtil.getContextHelpForeground()
        }
        requestBodyArea.text = draft.bodyText
        val restoredType = BodyType.entries.firstOrNull { it.name == draft.bodyTypeName } ?: BodyType.NONE
        selectBodyType(restoredType)
        requestStatusLabel.text = ApiHelperBundle.message("debug.status.loaded.draft", item.methodName)
        requestStatusLabel.foreground = UIUtil.getContextHelpForeground()
        responseMetaBadge.isVisible = false
    }

    /**
     * 将当前面板中填写的参数保存至草稿服务。
     */
    private fun saveCurrentDraftIfLoaded() {
        val item = currentEndpointItem ?: return
        DebugDraftService.of(project).saveDraft(endpointDraftKey(item), snapshotCurrentDraft())
    }

    private fun snapshotCurrentDraft(): EndpointDebugDraft =
        EndpointDebugDraft(
            method = methodBox.selectedItem?.toString().orEmpty(),
            url = urlField.text.trim(),
            query = queryModel.toPairs(),
            path = pathModel.toPairs(),
            headers = headerModel.toPairs(),
            cookies = cookieModel.toPairs(),
            bodyTypeName = selectedBodyType.name,
            bodyText = requestBodyArea.text,
            binaryPath = binaryPath,
            formData = formDataModel.toRows().map { it.copy() },
            urlEncoded = urlEncodedModel.toPairs(),
        )

    private fun showHistoryPopup() {
        val entries = DebugDraftService.of(project).getHistory()
        if (entries.isEmpty()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("ApiHelper")
                .createNotification(
                    ApiHelperBundle.message("debug.history.empty"),
                    NotificationType.INFORMATION,
                )
                .notify(project)
            return
        }
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(entries)
            .setTitle(ApiHelperBundle.message("debug.history.title"))
            .setItemSelectedCallback { selected ->
                if (selected != null) {
                    restoreHistoryEntry(selected)
                }
            }
            .createPopup()
            .showUnderneathOf(historyButton)
    }

    /**
     * 回放一条历史调试记录到面板中。
     *
     * @param entry 历史记录条目
     */
    fun restoreHistoryEntry(entry: DebugHistoryEntry) {
        val draft = entry.draft
        methodBox.selectedItem = draft.method.ifBlank { "GET" }
        urlField.text = draft.url
        pathModel.setPairs(draft.path)
        queryModel.setPairs(draft.query)
        headerModel.setPairs(draft.headers)
        cookieModel.setPairs(draft.cookies)
        formDataModel.clear()
        draft.formData.forEachIndexed { idx, row ->
            formDataModel.setValueAt(row.enabled, idx, 0)
            formDataModel.setValueAt(row.name, idx, 1)
            formDataModel.setValueAt(row.value, idx, 2)
            formDataModel.setValueAt(row.type, idx, 3)
        }
        urlEncodedModel.setPairs(draft.urlEncoded)
        binaryPath = draft.binaryPath
        if (binaryPath.isNotBlank()) {
            binaryPathLabel.text = binaryPath
            binaryPathLabel.foreground = UIUtil.getLabelForeground()
        } else {
            binaryPathLabel.text = ApiHelperBundle.message("debug.body.binary.placeholder")
            binaryPathLabel.foreground = UIUtil.getContextHelpForeground()
        }
        requestBodyArea.text = draft.bodyText
        val restoredType = BodyType.entries.firstOrNull { it.name == draft.bodyTypeName } ?: BodyType.NONE
        selectBodyType(restoredType)

        lastResponseBody = entry.responseBody
        responseHeaderArea.text = entry.responseHeaders
        responseHeaderArea.caretPosition = 0
        responseCookieArea.text = entry.responseCookies
        responseCookieArea.caretPosition = 0
        renderResponseBody()

        if (entry.statusCode != null) {
            requestStatusLabel.text = "● ${ApiHelperBundle.message("debug.status.done", entry.statusCode, entry.elapsedMs)}"
            requestStatusLabel.foreground = statusForegroundColor(entry.statusCode)
            responseMetaBadge.text = formatByteSize(entry.responseBody.toByteArray(StandardCharsets.UTF_8).size)
            responseMetaBadge.isVisible = true
        } else {
            requestStatusLabel.text = "● ${ApiHelperBundle.message("debug.status.failed", entry.elapsedMs)}"
            requestStatusLabel.foreground = UIUtil.getErrorForeground()
            responseMetaBadge.isVisible = false
        }
        selectResponseTab(RESP_TAB_BODY)
    }

    /**
     * 重置当前加载接口的草稿，重新从源码注解与 DTO 生成默认请求参数。
     */
    private fun resetCurrentEndpointDraft() {
        val item = currentEndpointItem ?: return
        DebugDraftService.of(project).removeDraft(endpointDraftKey(item))
        ApplicationManager.getApplication().executeOnPooledThread {
            if (project.isDisposed) return@executeOnPooledThread
            val serverPort = resolveServerPort(item)
            val params = resolveDebugParams(item)
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                applyLoadedEndpoint(item, serverPort, params)
            }
        }
    }

    private fun endpointDraftKey(item: EndpointTreeItem): String =
        "${item.className}#${item.methodName}:${item.httpMethod.name}:${item.url}"

    private fun onEnvironmentSelected() {
        if (updatingEnvironments) return
        val option = environmentBox.selectedItem as? EnvironmentOption ?: return
        ApiHelperSettings.getInstance().state.selectedEnvironmentName = option.name
        val currentUrl = urlField.text.trim()
        val pathPart = extractPathFromUrl(currentUrl).ifBlank { currentEndpointRelativeUrl }
        if (pathPart.isNotBlank()) {
            urlField.text = normalizeInitialUrl(pathPart, currentServerPort)
        }
    }

    private fun extractPathFromUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        if (rawUrl.startsWith("/")) return rawUrl
        val schemeIdx = rawUrl.indexOf("://")
        if (schemeIdx < 0) return rawUrl
        val slashIdx = rawUrl.indexOf('/', schemeIdx + 3)
        return if (slashIdx >= 0) rawUrl.substring(slashIdx) else "/"
    }

    /**
     * 构建顶部请求地址栏：左侧 GET 下拉 + 中间 URL 输入框 + 右侧时钟历史图标与发送按钮。
     */
    private fun buildRequestBar(): JPanel {
        return JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createEmptyBorder(6, 8, 4, 8)
            val left = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
                isOpaque = false
                add(environmentBox)
                add(methodBox)
            }
            val right = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(3), 0)).apply {
                isOpaque = false
                add(historyButton)
                add(copyCurlButton)
                add(importCurlButton)
                add(resetDraftButton)
                add(sendButton)
            }
            add(left, BorderLayout.WEST)
            add(urlField, BorderLayout.CENTER)
            add(right, BorderLayout.EAST)
        }
    }

    /**
     * 构建上半区：请求配置（胶囊标签栏 + 参数表格/Body 面板）。
     */
    private fun buildRequestSection(): JPanel {
        requestTabPills.clear()
        val pillsPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))).apply {
            isOpaque = false
            addRequestTabPill(this, TAB_QUERY, ApiHelperBundle.message("debug.tab.query"))
            addRequestTabPill(this, TAB_BODY, ApiHelperBundle.message("debug.tab.body"))
            addRequestTabPill(this, TAB_PATH, ApiHelperBundle.message("debug.tab.path"))
            addRequestTabPill(this, TAB_HEADERS, ApiHelperBundle.message("debug.tab.headers"))
            addRequestTabPill(this, TAB_COOKIES, ApiHelperBundle.message("debug.tab.cookies"))
        }
        val authButton = IconActionButton(AllIcons.Nodes.SecurityRole, ApiHelperBundle.message("debug.action.auth.preset")).apply {
            addActionListener { event ->
                (event.source as? Component)?.let { showAuthPresetPopup(it) }
            }
        }
        val tabsRow = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.subtleGridColor()),
                BorderFactory.createEmptyBorder(0, 4, 0, 4),
            )
            add(pillsPanel, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), JBUI.scale(3))).apply {
                isOpaque = false
                add(authButton)
            }, BorderLayout.EAST)
        }

        requestCardPanel.add(tableScrollPane(queryModel), TAB_QUERY)
        requestCardPanel.add(bodyPanel(), TAB_BODY)
        requestCardPanel.add(tableScrollPane(pathModel), TAB_PATH)
        requestCardPanel.add(tableScrollPane(headerModel), TAB_HEADERS)
        requestCardPanel.add(tableScrollPane(cookieModel), TAB_COOKIES)

        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            add(tabsRow, BorderLayout.NORTH)
            add(requestCardPanel, BorderLayout.CENTER)
        }
    }

    /**
     * 构建下半区：响应展示（胶囊标签栏 + 工具栏 + 带行号响应内容区）。
     */
    private fun buildResponseSection(): JPanel {
        responseTabPills.clear()
        val leftTabs = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))).apply {
            isOpaque = false
            addResponseTabPill(this, RESP_TAB_BODY, ApiHelperBundle.message("debug.tab.body"))
            addResponseTabPill(this, RESP_TAB_COOKIES, ApiHelperBundle.message("debug.tab.cookies"))
            addResponseTabPill(this, RESP_TAB_HEADERS, ApiHelperBundle.message("debug.tab.headers"))
        }

        val rightStatus = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), JBUI.scale(5))).apply {
            isOpaque = false
            add(requestStatusLabel)
            add(responseMetaBadge)
        }

        val headerRow = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, ApiHelperUiStyle.subtleGridColor()),
                BorderFactory.createEmptyBorder(0, 4, 0, 8),
            )
            add(leftTabs, BorderLayout.WEST)
            add(rightStatus, BorderLayout.EAST)
        }

        responseCardPanel.add(responseBodyPanel(), RESP_TAB_BODY)
        responseCardPanel.add(LineNumberedCodePanel(responseCookieArea), RESP_TAB_COOKIES)
        responseCardPanel.add(LineNumberedCodePanel(responseHeaderArea), RESP_TAB_HEADERS)

        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            add(headerRow, BorderLayout.NORTH)
            add(responseCardPanel, BorderLayout.CENTER)
        }
    }

    private fun addRequestTabPill(container: JPanel, key: String, title: String) {
        val pill = SectionTabPill(title) { selectRequestTab(key) }
        requestTabPills[key] = pill
        container.add(pill)
    }

    private fun addResponseTabPill(container: JPanel, key: String, title: String) {
        val pill = SectionTabPill(title) { selectResponseTab(key) }
        responseTabPills[key] = pill
        container.add(pill)
    }

    private fun selectRequestTab(key: String) {
        requestTabPills.forEach { (k, pill) -> pill.selected = (k == key) }
        requestCardLayout.show(requestCardPanel, key)
    }

    private fun selectResponseTab(key: String) {
        responseTabPills.forEach { (k, pill) -> pill.selected = (k == key) }
        responseCardLayout.show(responseCardPanel, key)
    }

    private fun bodyPanel(): JPanel {
        val typeBar = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(3))).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.subtleGridColor()),
                BorderFactory.createEmptyBorder(0, 4, 0, 4),
            )
            for (type in BodyType.entries) {
                val pill = BodyTypePill(type) { selectBodyType(type) }
                bodyTypePills[type] = pill
                add(pill)
            }
        }

        bodyContentPanel.apply {
            add(emptyBodyPanel(ApiHelperBundle.message("debug.body.none.hint")), BodyType.NONE.card)
            add(formDataPanel(), BodyType.FORM_DATA.card)
            add(tableScrollPane(urlEncodedModel), BodyType.URL_ENCODED.card)
            add(textBodyPanel(), BODY_TEXT_CARD)
            add(binaryBodyPanel(), BodyType.BINARY.card)
        }

        return JPanel(BorderLayout()).apply {
            add(typeBar, BorderLayout.NORTH)
            add(bodyContentPanel, BorderLayout.CENTER)
        }
    }

    private fun formDataPanel(): JComponent {
        val table = parameterTable(formDataModel, nameColumnIndex = 1, valueColumnIndex = 2).apply {
            columnModel.getColumn(3).preferredWidth = JBUI.scale(96)
            columnModel.getColumn(3).maxWidth = JBUI.scale(120)
            columnModel.getColumn(3).cellEditor = DefaultCellEditor(
                JComboBox(arrayOf(BodyFormDataTableModel.TYPE_TEXT, BodyFormDataTableModel.TYPE_FILE)),
            )
            columnModel.getColumn(4).maxWidth = JBUI.scale(34)
            columnModel.getColumn(4).preferredWidth = JBUI.scale(34)
            columnModel.getColumn(4).cellRenderer = RowDeleteRenderer()
            columnModel.getColumn(2).cellRenderer = FormDataValueRenderer(formDataModel)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val row = rowAtPoint(e.point)
                    val column = columnAtPoint(e.point)
                    when {
                        row >= 0 && column == 4 -> formDataModel.removeRow(row)
                        row >= 0 && column == 2 && formDataModel.rowAt(row)?.type == BodyFormDataTableModel.TYPE_FILE -> {
                            chooseLocalFile()?.let { formDataModel.updateValue(row, it) }
                        }
                    }
                }
            })
        }
        return JBScrollPane(table).apply {
            border = BorderFactory.createEmptyBorder()
        }
    }

    private fun textBodyPanel(): JPanel {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(3))).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.subtleGridColor()),
                BorderFactory.createEmptyBorder(0, 4, 0, 4),
            )
            add(IconActionButton(AllIcons.Actions.GC, ApiHelperBundle.message("debug.action.clear")).apply {
                addActionListener { requestBodyArea.text = "" }
            })
            add(toolbarDivider())
            add(IconActionButton(AllIcons.Actions.ToggleSoftWrap, ApiHelperBundle.message("debug.action.soft.wrap")).apply {
                addActionListener {
                    requestBodyArea.lineWrap = !requestBodyArea.lineWrap
                    requestBodyArea.wrapStyleWord = requestBodyArea.lineWrap
                }
            })
            add(toolbarDivider())
            add(JButton(ApiHelperBundle.message("debug.action.format")).apply {
                ApiHelperUiStyle.applyToolbarPillButton(this)
                addActionListener { formatJsonBody() }
            })
        }
        return JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(LineNumberedCodePanel(requestBodyArea), BorderLayout.CENTER)
        }
    }

    private fun emptyBodyPanel(text: String): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            add(JBLabel(text).apply {
                foreground = UIUtil.getContextHelpForeground()
                font = ApiHelperUiStyle.labelFont()
                border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
            }, BorderLayout.NORTH)
        }
    }

    private fun binaryBodyPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
            val uploadButton = JButton(ApiHelperBundle.message("debug.action.upload")).apply {
                ApiHelperUiStyle.applyOutlinedButton(this, width = 76)
                addActionListener {
                    chooseLocalFile()?.let {
                        binaryPath = it
                        binaryPathLabel.text = it
                        binaryPathLabel.foreground = UIUtil.getLabelForeground()
                    }
                }
            }
            val deleteLabel = IconActionButton(AllIcons.Actions.GC, ApiHelperBundle.message("debug.action.clear")).apply {
                addActionListener {
                    binaryPath = ""
                    binaryPathLabel.text = ApiHelperBundle.message("debug.body.binary.placeholder")
                    binaryPathLabel.foreground = UIUtil.getContextHelpForeground()
                }
            }
            val row = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
                isOpaque = false
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ApiHelperUiStyle.subtleGridColor()),
                    BorderFactory.createEmptyBorder(4, 6, 4, 4),
                )
                add(uploadButton, BorderLayout.WEST)
                add(binaryPathLabel, BorderLayout.CENTER)
                add(deleteLabel, BorderLayout.EAST)
            }
            add(row, BorderLayout.NORTH)
        }
    }

    private fun formatJsonBody() {
        if (selectedBodyType != BodyType.JSON) return
        val text = requestBodyArea.text
        if (text.isBlank()) return
        runCatching {
            requestBodyArea.text = prettyJson(text)
            requestBodyArea.caretPosition = 0
        }.onFailure {
            requestStatusLabel.text = ApiHelperBundle.message("debug.status.format.failed")
            requestStatusLabel.foreground = UIUtil.getErrorForeground()
        }
    }

    private fun tableScrollPane(model: DebugParameterTableModel): JComponent {
        val nameCol = if (model.showEnabledColumn) 1 else 0
        val valCol = if (model.showEnabledColumn) 2 else 1
        val table = parameterTable(model, nameColumnIndex = nameCol, valueColumnIndex = valCol)
        configureDeleteColumn(table) { row -> model.removeRow(row) }
        return JBScrollPane(table).apply {
            border = BorderFactory.createEmptyBorder()
        }
    }

    private fun parameterTable(
        model: javax.swing.table.TableModel,
        nameColumnIndex: Int = 1,
        valueColumnIndex: Int = 2,
    ): JTable =
        JTable(model).apply {
            ApiHelperUiStyle.styleParameterTable(
                table = this,
                nameColumnIndex = nameColumnIndex,
                valueColumnIndex = valueColumnIndex,
                namePlaceholder = "",
                valuePlaceholder = "",
            )
        }

    private fun configureDeleteColumn(table: JTable, deleteAction: (row: Int) -> Unit) {
        val deleteColumn = table.columnModel.getColumn(table.columnCount - 1)
        deleteColumn.maxWidth = JBUI.scale(34)
        deleteColumn.preferredWidth = JBUI.scale(34)
        deleteColumn.minWidth = JBUI.scale(34)
        deleteColumn.cellRenderer = RowDeleteRenderer()
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = table.rowAtPoint(e.point)
                val column = table.columnAtPoint(e.point)
                if (row >= 0 && column == table.columnCount - 1) {
                    deleteAction(row)
                }
            }
        })
    }

    /**
     * 构建响应体面板：顶部紧凑工具条（清空 | 自动换行 | 下载保存 | 复制 | JSON 下拉框）+ 底部带行号代码区。
     */
    private fun responseBodyPanel(): JPanel {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(3))).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.subtleGridColor()),
                BorderFactory.createEmptyBorder(0, 4, 0, 4),
            )
            add(IconActionButton(AllIcons.Actions.GC, ApiHelperBundle.message("debug.action.clear")).apply {
                addActionListener {
                    lastResponseBody = ""
                    responseBodyArea.text = ""
                    responseMetaBadge.isVisible = false
                }
            })
            add(toolbarDivider())
            add(IconActionButton(AllIcons.Actions.ToggleSoftWrap, ApiHelperBundle.message("debug.action.soft.wrap")).apply {
                addActionListener {
                    responseBodyArea.lineWrap = !responseBodyArea.lineWrap
                    responseBodyArea.wrapStyleWord = responseBodyArea.lineWrap
                }
            })
            add(toolbarDivider())
            add(IconActionButton(AllIcons.Actions.Download, ApiHelperBundle.message("debug.action.download")).apply {
                addActionListener { saveResponseToFile() }
            })
            add(toolbarDivider())
            add(IconActionButton(AllIcons.Actions.Copy, ApiHelperBundle.message("debug.action.copy")).apply {
                addActionListener { copyResponseBody() }
            })
            add(toolbarDivider())
            add(IconActionButton(AllIcons.Nodes.Variable, ApiHelperBundle.message("debug.action.extract.variable")).apply {
                addActionListener { event ->
                    (event.source as? Component)?.let { showExtractVariablePopup(it) }
                }
            })
            add(toolbarDivider())
            add(responseBodyTypeBox)
        }
        return JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(LineNumberedCodePanel(responseBodyArea), BorderLayout.CENTER)
        }
    }

    private fun toolbarDivider(): JLabel =
        JLabel("|").apply {
            foreground = ApiHelperUiStyle.subtleGridColor()
            font = ApiHelperUiStyle.smallFont()
            border = BorderFactory.createEmptyBorder(0, JBUI.scale(1), 0, JBUI.scale(1))
        }

    private fun readOnlyCodeArea(): JBTextArea =
        JBTextArea().apply {
            isEditable = false
            font = ApiHelperUiStyle.codeFont()
            lineWrap = false
            border = BorderFactory.createEmptyBorder(JBUI.scale(6), JBUI.scale(8), JBUI.scale(6), JBUI.scale(8))
        }

    private fun copyResponseBody() {
        val text = responseBodyArea.text
        if (text.isEmpty()) return
        CopyPasteManager.getInstance().setContents(StringSelection(text))
        if (!project.isDisposed) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("ApiHelper")
                .createNotification(
                    ApiHelperBundle.message("debug.notification.response.copied"),
                    NotificationType.INFORMATION,
                )
                .notify(project)
        }
    }

    /**
     * 认证预设：一键向 Headers 写入常见认证头，值引用调试变量（如 {{token}}），随环境变量自动切换。
     */
    private data class AuthPreset(
        val label: String,
        val headerName: String,
        val headerValue: String,
    ) {
        override fun toString(): String = label
    }

    /**
     * 弹出认证方式预设列表。
     *
     * @param anchorComponent 弹层锚点组件
     */
    private fun showAuthPresetPopup(anchorComponent: Component) {
        val presets = listOf(
            AuthPreset("Bearer Token", "Authorization", "Bearer {{token}}"),
            AuthPreset("Basic Auth", "Authorization", "Basic {{basicAuth}}"),
            AuthPreset("API Key", "X-API-Key", "{{apiKey}}"),
        )
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(presets)
            .setTitle(ApiHelperBundle.message("debug.auth.preset.title"))
            .setItemChosenCallback { preset -> applyAuthPreset(preset) }
            .createPopup()
            .showUnderneathOf(anchorComponent)
    }

    /**
     * 把选中的认证预设写入 Headers 表，同名 Header 覆盖原值并启用。
     */
    private fun applyAuthPreset(preset: AuthPreset) {
        val rows = headerModel.toAllRows().toMutableList()
        val index = rows.indexOfFirst { it.name.equals(preset.headerName, ignoreCase = true) }
        if (index >= 0) {
            rows[index] = rows[index].copy(value = preset.headerValue, enabled = true)
        } else {
            rows += DebugParameterRow(enabled = true, name = preset.headerName, value = preset.headerValue)
        }
        headerModel.setRows(rows)
        selectRequestTab(TAB_HEADERS)
    }

    /**
     * 弹出“从响应提取变量”小面板：按 JSON 路径取值并保存为调试变量（全局或当前环境）。
     *
     * @param anchorComponent 弹层锚点组件
     */
    private fun showExtractVariablePopup(anchorComponent: Component) {
        val body = lastResponseBody.trim()
        if (!body.startsWith("{") && !body.startsWith("[")) {
            if (!project.isDisposed) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("ApiHelper")
                    .createNotification(
                        ApiHelperBundle.message("debug.notification.variables.not.json"),
                        NotificationType.WARNING,
                    )
                    .notify(project)
            }
            return
        }

        val nameField = JBTextField("token").apply { ApiHelperUiStyle.applyTextField(this) }
        val pathField = JBTextField("data.token").apply { ApiHelperUiStyle.applyTextField(this) }
        val envName = (environmentBox.selectedItem as? EnvironmentOption)?.name.orEmpty()
        val envScopeBox = JCheckBox(
            ApiHelperBundle.message("debug.variables.popup.env.scope", envName.ifEmpty { "-" }),
        ).apply {
            isEnabled = envName.isNotEmpty()
            isOpaque = false
            font = ApiHelperUiStyle.labelFont()
        }
        val previewLabel = JBLabel().apply {
            foreground = UIUtil.getContextHelpForeground()
            font = ApiHelperUiStyle.smallFont()
        }

        fun currentValue(): String? = JsonValueExtractor.extract(body, pathField.text.trim())
        fun refreshPreview() {
            val value = currentValue()
            previewLabel.text = value?.let { "→ $it" }
                ?: ApiHelperBundle.message("debug.variables.popup.preview.empty")
        }
        val docListener = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = refreshPreview()
            override fun removeUpdate(e: DocumentEvent) = refreshPreview()
            override fun changedUpdate(e: DocumentEvent) = refreshPreview()
        }
        nameField.document.addDocumentListener(docListener)
        pathField.document.addDocumentListener(docListener)
        refreshPreview()

        val form = JPanel().apply {
            isOpaque = false
            layout = java.awt.GridBagLayout()
            val gc = java.awt.GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                anchor = java.awt.GridBagConstraints.WEST
                insets = JBUI.insets(2, 4)
            }
            fun addRow(labelKey: String, field: JComponent) {
                add(JBLabel(ApiHelperBundle.message(labelKey)).apply { font = ApiHelperUiStyle.labelFont() }, gc)
                gc.gridx = 1
                gc.fill = java.awt.GridBagConstraints.HORIZONTAL
                gc.weightx = 1.0
                add(field, gc)
                gc.gridx = 0
                gc.weightx = 0.0
                gc.fill = java.awt.GridBagConstraints.NONE
                gc.gridy++
            }
            addRow("debug.variables.popup.name", nameField)
            addRow("debug.variables.popup.path", pathField)
            add(envScopeBox, gc)
            gc.gridy++
            add(previewLabel, gc)
        }

        val saveButton = JButton(ApiHelperBundle.message("debug.variables.popup.save")).apply {
            ApiHelperUiStyle.applyToolbarPillButton(this)
        }
        val panel = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createEmptyBorder(JBUI.scale(6), JBUI.scale(6), JBUI.scale(6), JBUI.scale(6))
            add(form, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                isOpaque = false
                add(saveButton)
            }, BorderLayout.SOUTH)
        }

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, pathField)
            .setTitle(ApiHelperBundle.message("debug.variables.popup.title"))
            .setMovable(true)
            .setRequestFocus(true)
            .createPopup()

        saveButton.addActionListener {
            val name = nameField.text.trim()
            val value = currentValue()
            if (name.isEmpty() || value == null) {
                if (!project.isDisposed) {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("ApiHelper")
                        .createNotification(
                            ApiHelperBundle.message("debug.notification.variables.extract.failed", pathField.text.trim()),
                            NotificationType.WARNING,
                        )
                        .notify(project)
                }
                return@addActionListener
            }
            val scopeEnv = if (envScopeBox.isSelected && envScopeBox.isEnabled) envName else ""
            ApiHelperSettings.getInstance().upsertVariable(name, value, scopeEnv)
            if (!project.isDisposed) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("ApiHelper")
                    .createNotification(
                        ApiHelperBundle.message("debug.notification.variables.saved", name),
                        NotificationType.INFORMATION,
                    )
                    .notify(project)
            }
            popup.cancel()
        }

        popup.showUnderneathOf(anchorComponent)
    }

    private fun saveResponseToFile() {
        val text = responseBodyArea.text
        if (text.isEmpty()) return
        val chooser = JFileChooser()
        val ext = when (responseBodyTypeBox.selectedItem as? ResponseBodyType) {
            ResponseBodyType.JSON -> "json"
            ResponseBodyType.XML -> "xml"
            ResponseBodyType.HTML -> "html"
            else -> "txt"
        }
        chooser.selectedFile = File("response.$ext")
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile
            runCatching {
                Files.writeString(file.toPath(), text, StandardCharsets.UTF_8)
                if (!project.isDisposed) {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("ApiHelper")
                        .createNotification(
                            ApiHelperBundle.message("debug.notification.response.saved", file.name),
                            NotificationType.INFORMATION,
                        )
                        .notify(project)
                }
            }.onFailure { e ->
                log.warn("ApiHelper: 保存响应文件失败, path=${file.absolutePath}", e)
            }
        }
    }

    private fun updateRequestTabTitles() {
        requestTabPills[TAB_QUERY]?.text = tabTitleWithCount(ApiHelperBundle.message("debug.tab.query"), queryModel.toPairs().size)
        requestTabPills[TAB_PATH]?.text = tabTitleWithCount(ApiHelperBundle.message("debug.tab.path"), pathModel.toPairs().size)
        requestTabPills[TAB_HEADERS]?.text = tabTitleWithCount(ApiHelperBundle.message("debug.tab.headers"), headerModel.toPairs().size)
        requestTabPills[TAB_COOKIES]?.text = tabTitleWithCount(ApiHelperBundle.message("debug.tab.cookies"), cookieModel.toPairs().size)
    }

    private fun tabTitleWithCount(base: String, count: Int): String =
        if (count > 0) "$base ($count)" else base

    /**
     * 主按钮点击入口：空闲时发送请求，发送中则取消在途请求。
     */
    private fun onPrimaryAction() {
        if (isSending) {
            currentRequestFuture?.cancel(true)
            return
        }
        sendRequest()
    }

    override fun removeNotify() {
        saveCurrentDraftIfLoaded()
        super.removeNotify()
        // 面板销毁时主动取消在途请求，避免后台线程继续占用连接
        currentRequestFuture?.cancel(true)
    }

    /**
     * 当前生效的调试变量表：全局变量为底，当前选中环境的同名变量覆盖全局值。
     */
    private fun currentVariables(): Map<String, String> {
        val settings = ApiHelperSettings.getInstance()
        val envName = (environmentBox.selectedItem as? EnvironmentOption)?.name.orEmpty()
        return settings.resolveVariables(envName)
    }

    /**
     * 对调试请求的各个部分执行 `{{var}}` 变量替换（form-data 的文件路径不替换）。
     *
     * @param input 原始请求参数
     * @return 替换后的请求参数与未解析变量名列表
     */
    private fun applyVariables(input: DebugRequestInput): Pair<DebugRequestInput, List<String>> {
        val variables = currentVariables()
        val unresolved = LinkedHashSet<String>()
        val resolvedUrl = VariableResolver.resolve(input.url, variables).also { unresolved += it.unresolved }.text
        val (path, pathUnresolved) = VariableResolver.resolvePairs(input.path, variables)
        val (query, queryUnresolved) = VariableResolver.resolvePairs(input.query, variables)
        val (headers, headerUnresolved) = VariableResolver.resolvePairs(input.headers, variables)
        val (cookies, cookieUnresolved) = VariableResolver.resolvePairs(input.cookies, variables)
        val (urlEncoded, urlEncodedUnresolved) = VariableResolver.resolvePairs(input.urlEncoded, variables)
        unresolved += pathUnresolved + queryUnresolved + headerUnresolved + cookieUnresolved + urlEncodedUnresolved
        val body = VariableResolver.resolve(input.body, variables).also { unresolved += it.unresolved }.text
        val formData = input.formData.map { row ->
            if (row.type == BodyFormDataTableModel.TYPE_FILE) {
                row
            } else {
                val result = VariableResolver.resolve(row.value, variables)
                unresolved += result.unresolved
                row.copy(value = result.text)
            }
        }
        return input.copy(
            url = resolvedUrl,
            path = path,
            query = query,
            headers = headers,
            cookies = cookies,
            body = body,
            formData = formData,
            urlEncoded = urlEncoded,
        ) to unresolved.toList()
    }

    /**
     * 存在未解析变量时弹出警告通知，请求仍按原文继续发送。
     *
     * @param unresolved 未解析的变量名列表
     */
    private fun notifyUnresolvedVariables(unresolved: List<String>) {
        if (unresolved.isEmpty() || project.isDisposed) return
        NotificationGroupManager.getInstance()
            .getNotificationGroup("ApiHelper")
            .createNotification(
                ApiHelperBundle.message("debug.notification.variables.unresolved", unresolved.joinToString(", ")),
                NotificationType.WARNING,
            )
            .notify(project)
    }

    private fun copyCurrentCurl() {
        saveCurrentDraftIfLoaded()
        val settings = ApiHelperSettings.getInstance()
        val mergedHeaders = CurlCommandBuilder.mergeHeaders(settings.parseGlobalHeaders(), headerModel.toPairs())
        val mergedQueries = CurlCommandBuilder.mergeHeaders(settings.parseGlobalQueries(), queryModel.toPairs())
        val mergedCookies = CurlCommandBuilder.mergeHeaders(settings.parseGlobalCookies(), cookieModel.toPairs())
        val rawUrl = normalizeInitialUrl(urlField.text.trim(), currentServerPort)
        val effectiveBodyText = when (selectedBodyType) {
            BodyType.NONE, BodyType.FORM_DATA, BodyType.BINARY -> ""
            BodyType.URL_ENCODED -> urlEncodedModel.toPairs()
                .filter { it.first.isNotBlank() }
                .joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }
            else -> requestBodyArea.text
        }
        val rawInput = DebugRequestInput(
            method = methodBox.selectedItem?.toString().orEmpty(),
            url = rawUrl,
            query = mergedQueries,
            path = pathModel.toPairs(),
            headers = mergedHeaders,
            cookies = mergedCookies,
            bodyType = selectedBodyType,
            body = effectiveBodyText,
            binaryPath = binaryPath,
            formData = if (selectedBodyType == BodyType.FORM_DATA) formDataModel.toRows() else emptyList(),
            urlEncoded = emptyList(),
        )
        val (input, unresolvedVariables) = applyVariables(rawInput)
        notifyUnresolvedVariables(unresolvedVariables)
        val curl = CurlCommandBuilder.build(
            method = input.method,
            rawUrl = input.url,
            pathParams = input.path,
            queryParams = input.query,
            headers = input.headers,
            cookies = input.cookies,
            contentType = selectedBodyType.contentType,
            bodyText = input.body,
            formDataRows = input.formData,
            binaryFilePath = if (selectedBodyType == BodyType.BINARY) binaryPath else "",
        )
        CopyPasteManager.getInstance().setContents(StringSelection(curl))
        if (!project.isDisposed) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("ApiHelper")
                .createNotification(
                    ApiHelperBundle.message("debug.notification.curl.copied"),
                    NotificationType.INFORMATION,
                )
                .notify(project)
        }
    }

    /**
     * 从剪贴板导入 cURL 命令并填充调试面板；已有同名参数保留，新参数追加。
     */
    private fun importCurlFromClipboard() {
        val text = runCatching {
            CopyPasteManager.getInstance().contents
                ?.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
        }.getOrNull()
        val parsed = text?.let(CurlCommandParser::parse)
        if (parsed == null) {
            if (!project.isDisposed) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("ApiHelper")
                    .createNotification(
                        ApiHelperBundle.message("debug.notification.curl.import.failed"),
                        NotificationType.WARNING,
                    )
                    .notify(project)
            }
            return
        }

        methodBox.selectedItem = parsed.method
        urlField.text = parsed.url
        mergePairsInto(queryModel, parsed.query)
        mergePairsInto(headerModel, parsed.headers)
        mergePairsInto(cookieModel, parsed.cookies)

        val contentType = parsed.headers
            .firstOrNull { it.first.equals("Content-Type", ignoreCase = true) }
            ?.second
            .orEmpty()
            .lowercase()
        when {
            parsed.binaryFilePath.isNotBlank() -> {
                binaryPath = parsed.binaryFilePath
                binaryPathLabel.text = parsed.binaryFilePath
                selectBodyType(BodyType.BINARY)
            }
            parsed.formData.isNotEmpty() -> {
                formDataModel.setRows(parsed.formData)
                selectBodyType(BodyType.FORM_DATA)
            }
            parsed.bodyText.isNotBlank() && contentType.contains("x-www-form-urlencoded") -> {
                val pairs = parsed.bodyText.split('&')
                    .filter { it.isNotBlank() }
                    .map { pair ->
                        val idx = pair.indexOf('=')
                        if (idx > 0) pair.substring(0, idx) to pair.substring(idx + 1) else pair to ""
                    }
                urlEncodedModel.setPairs(pairs)
                selectBodyType(BodyType.URL_ENCODED)
            }
            parsed.bodyText.isNotBlank() -> {
                requestBodyArea.text = parsed.bodyText
                val trimmed = parsed.bodyText.trimStart()
                selectBodyType(
                    when {
                        contentType.contains("xml") || trimmed.startsWith("<?xml") -> BodyType.XML
                        contentType.contains("json") || trimmed.startsWith("{") || trimmed.startsWith("[") -> BodyType.JSON
                        else -> BodyType.RAW
                    },
                )
            }
            else -> selectBodyType(BodyType.NONE)
        }

        requestStatusLabel.text = ApiHelperBundle.message("debug.status.curl.imported")
        requestStatusLabel.foreground = UIUtil.getContextHelpForeground()
    }

    /**
     * 把键值对合并进参数表，仅追加表内尚不存在的参数名。
     */
    private fun mergePairsInto(model: DebugParameterTableModel, pairs: List<Pair<String, String>>) {
        if (pairs.isEmpty()) return
        val rows = model.toAllRows().toMutableList()
        for ((name, value) in pairs) {
            if (name.isBlank() || rows.any { it.name.equals(name, ignoreCase = true) }) continue
            rows += DebugParameterRow(enabled = true, name = name, value = value)
        }
        model.setRows(rows)
    }

    private fun sendRequest() {
        val rawUrl = urlField.text.trim()
        if (rawUrl.isEmpty()) {
            requestStatusLabel.text = ApiHelperBundle.message("debug.status.url.empty")
            requestStatusLabel.foreground = UIUtil.getErrorForeground()
            urlField.requestFocusInWindow()
            return
        }
        if (selectedBodyType == BodyType.BINARY) {
            val filePath = binaryPath.trim()
            if (filePath.isEmpty() || !Files.isRegularFile(Path.of(filePath))) {
                requestStatusLabel.text = ApiHelperBundle.message("debug.status.file.missing", filePath)
                requestStatusLabel.foreground = UIUtil.getErrorForeground()
                return
            }
        }
        if (selectedBodyType == BodyType.FORM_DATA) {
            val missingFile = formDataModel.toRows()
                .firstOrNull { it.type == BodyFormDataTableModel.TYPE_FILE && (it.value.isBlank() || !Files.isRegularFile(Path.of(it.value.trim()))) }
            if (missingFile != null) {
                requestStatusLabel.text = ApiHelperBundle.message("debug.status.file.missing", missingFile.value)
                requestStatusLabel.foreground = UIUtil.getErrorForeground()
                return
            }
        }

        saveCurrentDraftIfLoaded()
        val requestDraftSnapshot = snapshotCurrentDraft()
        val settings = ApiHelperSettings.getInstance()
        val mergedHeaders = CurlCommandBuilder.mergeHeaders(settings.parseGlobalHeaders(), headerModel.toPairs())
        val mergedQueries = CurlCommandBuilder.mergeHeaders(settings.parseGlobalQueries(), queryModel.toPairs())
        val mergedCookies = CurlCommandBuilder.mergeHeaders(settings.parseGlobalCookies(), cookieModel.toPairs())
        val rawInput = DebugRequestInput(
            method = methodBox.selectedItem?.toString().orEmpty(),
            url = rawUrl,
            query = mergedQueries,
            path = pathModel.toPairs(),
            headers = mergedHeaders,
            cookies = mergedCookies,
            bodyType = selectedBodyType,
            body = requestBodyArea.text,
            binaryPath = binaryPath,
            formData = formDataModel.toRows(),
            urlEncoded = urlEncodedModel.toPairs(),
        )
        val (input, unresolvedVariables) = applyVariables(rawInput)
        notifyUnresolvedVariables(unresolvedVariables)

        enterSendingState()
        requestStatusLabel.text = "● ${ApiHelperBundle.message("debug.status.sending")}"
        requestStatusLabel.foreground = UIUtil.getContextHelpForeground()
        responseMetaBadge.isVisible = false
        lastResponseBody = ""
        responseBodyArea.text = ""
        responseHeaderArea.text = ""
        responseCookieArea.text = ""

        ApplicationManager.getApplication().executeOnPooledThread {
            val started = System.nanoTime()
            try {
                val request = buildRequest(input)
                val future = httpClient().sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                currentRequestFuture = future
                val response = future.join()
                val responseBody = response.body().use(::readLimitedText)
                val elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis()
                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) return@invokeLater
                    exitSendingState()
                    showResponse(
                        statusCode = response.statusCode(),
                        headers = response.headers(),
                        body = responseBody.text,
                        truncated = responseBody.truncated,
                        elapsedMs = elapsedMs,
                        requestDraft = requestDraftSnapshot,
                    )
                    selectResponseTab(RESP_TAB_BODY)
                }
            } catch (e: Exception) {
                val elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis()
                val cancelled = isCancellation(e)
                if (!cancelled) {
                    log.warn("ApiHelper: 接口调试请求失败, url=${input.url}", e)
                }
                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) return@invokeLater
                    exitSendingState()
                    if (cancelled) {
                        requestStatusLabel.text = ApiHelperBundle.message("debug.status.cancelled")
                        requestStatusLabel.foreground = UIUtil.getContextHelpForeground()
                    } else {
                        requestStatusLabel.text = "● ${ApiHelperBundle.message("debug.status.failed", elapsedMs)}"
                        requestStatusLabel.foreground = UIUtil.getErrorForeground()
                        lastResponseBody = e.message ?: e::class.java.name
                        renderResponseBody()
                        DebugDraftService.of(project).addHistory(
                            DebugHistoryEntry(
                                timestampText = DebugDraftService.currentTimeText(),
                                draft = requestDraftSnapshot,
                                statusCode = null,
                                elapsedMs = elapsedMs,
                                responseBody = lastResponseBody,
                                responseHeaders = "",
                                responseCookies = "",
                            ),
                        )
                        onHistoryUpdated?.invoke()
                        selectResponseTab(RESP_TAB_BODY)
                    }
                }
            }
        }
    }

    /**
     * 进入发送中状态，主按钮切换为"停止"，支持取消在途请求。
     */
    private fun enterSendingState() {
        isSending = true
        sendButton.setSendingMode(true)
        sendButton.text = ApiHelperBundle.message("debug.action.stop")
        sendButton.icon = AllIcons.Actions.Suspend
    }

    /**
     * 退出发送中状态，恢复主按钮为"发送"并清理在途请求引用。
     */
    private fun exitSendingState() {
        isSending = false
        currentRequestFuture = null
        sendButton.setSendingMode(false)
        sendButton.text = ApiHelperBundle.message("debug.action.send")
        sendButton.icon = AllIcons.Actions.Execute
    }

    /**
     * 获取按当前配置构建的 HttpClient；连接超时变更后重建，代理始终跟随 IDE 全局配置。
     *
     * @return 可复用的 HttpClient 实例
     */
    private fun httpClient(): HttpClient {
        val connectTimeout = ApiHelperSettings.getInstance().state.connectTimeoutSeconds
            .coerceIn(CONNECT_TIMEOUT_MIN_SECONDS, CONNECT_TIMEOUT_MAX_SECONDS)
        val cached = cachedHttpClient
        if (cached != null && cachedHttpClientConnectTimeout == connectTimeout) {
            return cached
        }
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(connectTimeout.toLong()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(IdeProxySelector)
            .build()
        cachedHttpClient = client
        cachedHttpClientConnectTimeout = connectTimeout
        return client
    }

    /**
     * 判断异常链是否由取消引起，取消不作为失败处理。
     *
     * @param error 发送或读取响应过程中抛出的异常
     * @return 异常链中包含 CancellationException 时返回 true
     */
    private fun isCancellation(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is CancellationException) return true
            current = current.cause
        }
        return false
    }

    /**
     * 代理选择器，对接 IDE 全局代理配置（Settings -> Appearance & Behavior -> System Settings -> HTTP Proxy）。
     */
    private object IdeProxySelector : ProxySelector() {

        override fun select(uri: URI): List<Proxy> =
            runCatching { JdkProxyProvider.getInstance().proxySelector.select(uri) }.getOrDefault(emptyList())

        override fun connectFailed(uri: URI, socketAddress: SocketAddress, exception: IOException) {
            // 交由 Java HttpClient 内置的重试与直连回退处理，不做额外干预
        }
    }

    private fun buildRequest(input: DebugRequestInput): HttpRequest {
        val method = input.method.ifBlank { "GET" }.uppercase()
        val requestUri = URI.create(appendQuery(applyPathParams(normalizeInitialUrl(input.url, currentServerPort), input.path), input.query))
        val requestTimeout = ApiHelperSettings.getInstance().state.requestTimeoutSeconds
            .coerceIn(REQUEST_TIMEOUT_MIN_SECONDS, REQUEST_TIMEOUT_MAX_SECONDS)
        val builder = HttpRequest.newBuilder(requestUri)
            .timeout(Duration.ofSeconds(requestTimeout.toLong()))

        for ((name, value) in input.headers) {
            if (name.isNotBlank()) builder.setHeader(name, value)
        }
        val cookieHeader = input.cookies
            .filter { it.first.isNotBlank() }
            .joinToString("; ") { "${it.first}=${it.second}" }
        if (cookieHeader.isNotBlank()) {
            builder.setHeader("Cookie", cookieHeader)
        }

        val bodyPublisher = when {
            method == "GET" || method == "HEAD" || input.bodyType == BodyType.NONE -> {
                HttpRequest.BodyPublishers.noBody()
            }
            input.bodyType == BodyType.BINARY -> {
                if (!hasHeader(input.headers, "Content-Type")) {
                    builder.setHeader("Content-Type", input.bodyType.contentType)
                }
                HttpRequest.BodyPublishers.ofFile(Path.of(input.binaryPath.trim()))
            }
            input.bodyType == BodyType.FORM_DATA -> {
                if (!hasHeader(input.headers, "Content-Type")) {
                    builder.setHeader("Content-Type", input.bodyType.contentType)
                }
                multipartPublisher(input.formData)
            }
            else -> {
                val requestBody = bodyText(input)
                if (!hasHeader(input.headers, "Content-Type") && input.bodyType.contentType.isNotBlank()) {
                    builder.setHeader("Content-Type", input.bodyType.contentType)
                }
                HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8)
            }
        }
        return builder.method(method, bodyPublisher).build()
    }

    private fun showResponse(
        statusCode: Int,
        headers: HttpHeaders,
        body: String,
        truncated: Boolean,
        elapsedMs: Long,
        requestDraft: EndpointDebugDraft,
    ) {
        requestStatusLabel.text = "● ${ApiHelperBundle.message(
            "debug.status.done",
            statusCode,
            elapsedMs,
        )}"
        requestStatusLabel.foreground = statusForegroundColor(statusCode)
        responseMetaBadge.text = formatByteSize(body.toByteArray(StandardCharsets.UTF_8).size)
        responseMetaBadge.isVisible = true
        lastResponseBody = if (truncated) {
            body + ApiHelperBundle.message("debug.response.truncated", MAX_RESPONSE_PREVIEW_BYTES / MB)
        } else {
            body
        }
        autoSelectResponseBodyType(headers, body)
        renderResponseBody()
        val headersText = headers.map().entries
            .sortedBy { it.key }
            .joinToString("\n") { (name, values) -> "$name: ${values.joinToString(", ")}" }
        responseHeaderArea.text = headersText
        responseHeaderArea.caretPosition = 0
        val cookiesText = headers.allValues("set-cookie")
            .flatMap { value -> runCatching { HttpCookie.parse(value) }.getOrDefault(emptyList()) }
            .joinToString("\n") { cookie -> "${cookie.name}=${cookie.value}" }
        responseCookieArea.text = cookiesText
        responseCookieArea.caretPosition = 0
        DebugDraftService.of(project).addHistory(
            DebugHistoryEntry(
                timestampText = DebugDraftService.currentTimeText(),
                draft = requestDraft,
                statusCode = statusCode,
                elapsedMs = elapsedMs,
                responseBody = lastResponseBody,
                responseHeaders = headersText,
                responseCookies = cookiesText,
            ),
        )
        onHistoryUpdated?.invoke()
    }

    private fun renderResponseBody() {
        val type = responseBodyTypeBox.selectedItem as? ResponseBodyType ?: ResponseBodyType.JSON
        responseBodyArea.text = when (type) {
            ResponseBodyType.JSON -> formatResponseJson(lastResponseBody)
            ResponseBodyType.XML, ResponseBodyType.HTML, ResponseBodyType.TEXT -> lastResponseBody
        }
        responseBodyArea.caretPosition = 0
    }

    private fun autoSelectResponseBodyType(headers: HttpHeaders, body: String) {
        val contentType = headers.firstValue("content-type").orElse("").lowercase()
        val trimmed = body.trimStart()
        val detected = when {
            contentType.contains("json") || trimmed.startsWith("{") || trimmed.startsWith("[") -> ResponseBodyType.JSON
            contentType.contains("html") || trimmed.startsWith("<!doctype html", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true) -> ResponseBodyType.HTML
            contentType.contains("xml") || trimmed.startsWith("<?xml", ignoreCase = true) -> ResponseBodyType.XML
            contentType.contains("text/plain") -> ResponseBodyType.TEXT
            else -> null
        }
        if (detected != null && responseBodyTypeBox.selectedItem != detected) {
            responseBodyTypeBox.selectedItem = detected
        }
    }

    private fun statusForegroundColor(statusCode: Int): Color =
        when (statusCode) {
            in 200..299 -> JBColor(Color(0x177245), Color(0x7ED69C))
            in 300..399 -> JBColor(Color(0x966313), Color(0xE0B56B))
            in 400..599 -> UIUtil.getErrorForeground()
            else -> UIUtil.getContextHelpForeground()
        }

    private fun formatResponseJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return raw
        return runCatching { prettyJson(trimmed) }.getOrDefault(raw)
    }

    private fun normalizeInitialUrl(rawUrl: String, port: Int = DEFAULT_SERVER_PORT): String {
        val value = rawUrl.trim()
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        val path = if (value.startsWith("/")) value else "/$value"
        val selectedOption = environmentBox.selectedItem as? EnvironmentOption
        val customBase = selectedOption?.baseUrl?.trim()?.trimEnd('/')
        if (!customBase.isNullOrEmpty()) {
            return "$customBase$path"
        }
        return "http://localhost:$port$path"
    }

    private fun resolveServerPort(item: EndpointTreeItem): Int {
        return ApplicationManager.getApplication().runReadAction(Computable {
            val method = item.pointer?.element?.takeIf { it.isValid } ?: return@Computable DEFAULT_SERVER_PORT
            val psiClass = method.containingClass ?: return@Computable DEFAULT_SERVER_PORT
            val manualProfile = ApiHelperSettings.getInstance().state.manualActiveProfile
            val properties = ApplicationConfigReader.readConfigForClass(psiClass, manualProfile)
            ApplicationConfigReader.readServerPort(properties) ?: DEFAULT_SERVER_PORT
        })
    }

    private fun applyPathParams(rawUrl: String, params: List<Pair<String, String>>): String {
        var url = rawUrl
        for ((name, value) in params) {
            if (name.isBlank()) continue
            url = url.replace("{$name}", encode(value))
        }
        return url
    }

    private fun appendQuery(rawUrl: String, query: List<Pair<String, String>>): String {
        val pairs = query.filter { it.first.isNotBlank() }
        if (pairs.isEmpty()) return rawUrl
        val separator = when {
            rawUrl.contains("?") && rawUrl.endsWith("?").not() && rawUrl.endsWith("&").not() -> "&"
            rawUrl.endsWith("?") || rawUrl.endsWith("&") -> ""
            else -> "?"
        }
        val queryText = pairs.joinToString("&") { (name, value) ->
            "${encode(name)}=${encode(value)}"
        }
        return "$rawUrl$separator$queryText"
    }

    private fun extractPathVariables(url: String): List<String> {
        return Regex("\\{([^}/]+)}")
            .findAll(url)
            .map { it.groupValues[1] }
            .toList()
    }

    private fun pathPairs(url: String, annotatedPath: List<Pair<String, String>>): List<Pair<String, String>> {
        val fromUrl = extractPathVariables(url).map { it to "" }
        return (annotatedPath + fromUrl).distinctBy { it.first }
    }

    private fun selectBodyType(type: BodyType) {
        selectedBodyType = type
        bodyTypePills.forEach { (k, pill) -> pill.selected = (k == type) }
        bodyCardLayout.show(bodyContentPanel, type.card)
    }

    private fun formatByteSize(bytes: Int): String =
        when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }

    private fun resolveDebugParams(item: EndpointTreeItem): DebugAutoParams {
        return ApplicationManager.getApplication().runReadAction(Computable {
            val method = item.pointer?.element?.takeIf { it.isValid } ?: return@Computable DebugAutoParams()
            val path = mutableListOf<Pair<String, String>>()
            val query = mutableListOf<Pair<String, String>>()
            val headers = mutableListOf<Pair<String, String>>()
            val cookies = mutableListOf<Pair<String, String>>()
            val bodyCandidateParams = mutableListOf<com.intellij.psi.PsiParameter>()

            val useQueryForUnannotated = item.httpMethod == HttpMethod.GET ||
                    item.httpMethod == HttpMethod.DELETE ||
                    item.httpMethod == HttpMethod.HEAD ||
                    item.httpMethod == HttpMethod.ANY

            for (parameter in method.parameterList.parameters) {
                when {
                    EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.PATH_VARIABLE) -> {
                        val param = EndpointParameterResolver.annotatedParameter(parameter, SpringAnnotations.PATH_VARIABLE)
                        path += param.name to param.sampleValue
                    }
                    EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.REQUEST_PARAM) -> {
                        val param = EndpointParameterResolver.annotatedParameter(parameter, SpringAnnotations.REQUEST_PARAM)
                        query += param.name to param.sampleValue
                    }
                    EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.REQUEST_HEADER) -> {
                        val param = EndpointParameterResolver.annotatedParameter(parameter, SpringAnnotations.REQUEST_HEADER)
                        headers += param.name to param.sampleValue
                    }
                    EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.COOKIE_VALUE) -> {
                        val param = EndpointParameterResolver.annotatedParameter(parameter, SpringAnnotations.COOKIE_VALUE)
                        cookies += param.name to param.sampleValue
                    }
                    EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.REQUEST_BODY) -> {
                        bodyCandidateParams += parameter
                    }
                    EndpointParameterResolver.hasQueryMapAnnotation(parameter) -> {
                        query += EndpointParameterResolver.queryParameters(parameter)
                            .filter { !it.name.contains('.') && !it.name.contains('[') }
                            .map { it.name to it.sampleValue }
                    }
                    useQueryForUnannotated -> {
                        query += EndpointParameterResolver.queryParameters(parameter)
                            .filter { !it.name.contains('.') && !it.name.contains('[') }
                            .map { it.name to it.sampleValue }
                    }
                    else -> {
                        bodyCandidateParams += parameter
                    }
                }
            }
            val bodyText = EndpointParameterResolver.buildJsonSampleForParameters(bodyCandidateParams)
            DebugAutoParams(
                path = path.distinctBy { it.first },
                query = query.distinctBy { it.first },
                headers = headers.distinctBy { it.first },
                cookies = cookies.distinctBy { it.first },
                bodyText = bodyText,
            )
        })
    }

    private data class EnvironmentOption(
        val name: String,
        val displayLabel: String,
        val baseUrl: String?,
    ) {
        override fun toString(): String = displayLabel
    }

    private data class DebugAutoParams(
        val path: List<Pair<String, String>> = emptyList(),
        val query: List<Pair<String, String>> = emptyList(),
        val headers: List<Pair<String, String>> = emptyList(),
        val cookies: List<Pair<String, String>> = emptyList(),
        val bodyText: String = "",
    )

    private fun hasHeader(headers: List<Pair<String, String>>, name: String): Boolean =
        headers.any { it.first.equals(name, ignoreCase = true) }

    private fun bodyText(input: DebugRequestInput): String {
        return when (input.bodyType) {
            BodyType.URL_ENCODED -> input.urlEncoded
                .filter { it.first.isNotBlank() }
                .joinToString("&") { (name, value) -> "${encode(name)}=${encode(value)}" }
            else -> input.body
        }
    }

    private fun multipartPublisher(rows: List<DebugParameterRow>): HttpRequest.BodyPublisher {
        val publishers = mutableListOf<HttpRequest.BodyPublisher>()
        for (row in rows) {
            publishers += HttpRequest.BodyPublishers.ofString("--$MULTIPART_BOUNDARY\r\n", StandardCharsets.UTF_8)
            if (row.type == BodyFormDataTableModel.TYPE_FILE) {
                val path = Path.of(row.value)
                val fileName = path.fileName?.toString().orEmpty()
                publishers += HttpRequest.BodyPublishers.ofString(
                    "Content-Disposition: form-data; name=\"${row.name}\"; filename=\"$fileName\"\r\n\r\n",
                    StandardCharsets.UTF_8,
                )
                publishers += HttpRequest.BodyPublishers.ofFile(path)
                publishers += HttpRequest.BodyPublishers.ofString("\r\n", StandardCharsets.UTF_8)
            } else {
                publishers += HttpRequest.BodyPublishers.ofString(
                    "Content-Disposition: form-data; name=\"${row.name}\"\r\n\r\n${row.value}\r\n",
                    StandardCharsets.UTF_8,
                )
            }
        }
        publishers += HttpRequest.BodyPublishers.ofString("--$MULTIPART_BOUNDARY--\r\n", StandardCharsets.UTF_8)
        return HttpRequest.BodyPublishers.concat(*publishers.toTypedArray())
    }

    private fun readLimitedText(input: InputStream): LimitedText {
        val buffer = ByteArray(MAX_RESPONSE_PREVIEW_BYTES + 1)
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) break
            offset += read
        }
        val truncated = offset > MAX_RESPONSE_PREVIEW_BYTES
        val length = offset.coerceAtMost(MAX_RESPONSE_PREVIEW_BYTES)
        return LimitedText(String(buffer, 0, length, StandardCharsets.UTF_8), truncated)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun prettyJson(raw: String): String {
        val result = StringBuilder()
        var indent = 0
        var inString = false
        var escaping = false
        for (ch in raw.trim()) {
            when {
                escaping -> {
                    result.append(ch)
                    escaping = false
                }
                ch == '\\' && inString -> {
                    result.append(ch)
                    escaping = true
                }
                ch == '"' -> {
                    result.append(ch)
                    inString = !inString
                }
                inString -> result.append(ch)
                ch == '{' || ch == '[' -> {
                    result.append(ch)
                    result.append('\n')
                    indent++
                    appendJsonIndent(result, indent)
                }
                ch == '}' || ch == ']' -> {
                    result.append('\n')
                    indent = (indent - 1).coerceAtLeast(0)
                    appendJsonIndent(result, indent)
                    result.append(ch)
                }
                ch == ',' -> {
                    result.append(ch)
                    result.append('\n')
                    appendJsonIndent(result, indent)
                }
                ch == ':' -> result.append(": ")
                ch.isWhitespace() -> Unit
                else -> result.append(ch)
            }
        }
        return result.toString()
    }

    private fun appendJsonIndent(builder: StringBuilder, indent: Int) {
        repeat(indent) {
            builder.append("  ")
        }
    }

    /**
     * 描边深色主题发送按钮（贴合参考设计：深底圆角描边 + 绿色发送图标 + 白字）。
     */
    private class SendButton(text: String) : JButton(text) {

        private var sendingMode: Boolean = false
        private var hovered: Boolean = false

        init {
            icon = AllIcons.Actions.Execute
            isFocusPainted = false
            isContentAreaFilled = false
            isBorderPainted = false
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            foreground = UIUtil.getLabelForeground()
            font = ApiHelperUiStyle.labelFont(bold = true)
            iconTextGap = JBUI.scale(4)
            val size = Dimension(JBUI.scale(70), JBUI.scale(28))
            preferredSize = size
            minimumSize = size
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    hovered = true
                    repaint()
                }

                override fun mouseExited(e: MouseEvent) {
                    hovered = false
                    repaint()
                }
            })
        }

        fun setSendingMode(sending: Boolean) {
            sendingMode = sending
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val arc = JBUI.scale(6)
                g2.color = when {
                    sendingMode -> JBColor(Color(0xFEE2E2), Color(0x3B1F23))
                    hovered -> ApiHelperUiStyle.hoverBackgroundColor()
                    else -> UIUtil.getPanelBackground()
                }
                g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
                g2.color = if (sendingMode) {
                    ApiHelperUiStyle.stopActionBackground()
                } else {
                    ApiHelperUiStyle.borderColor()
                }
                g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }

    /**
     * 紧凑无边框图标按钮（带悬停柔和圆角背景）。
     */
    private class IconActionButton(
        icon: Icon,
        tooltip: String,
    ) : JButton(icon) {

        init {
            toolTipText = tooltip
            ApiHelperUiStyle.applyIconActionButton(this)
        }

        override fun paintComponent(g: Graphics) {
            ApiHelperUiStyle.paintIconButtonHover(this, g)
            super.paintComponent(g)
        }
    }

    /**
     * 请求与响应分区的胶囊式子标签（如 Query / Body / Path / Headers / Cookies）。
     */
    private class SectionTabPill(
        text: String,
        onClick: () -> Unit,
    ) : JLabel(text, CENTER) {

        var selected: Boolean = false
            set(value) {
                field = value
                font = ApiHelperUiStyle.labelFont(bold = value)
                foreground = if (value) {
                    ApiHelperUiStyle.activeTabPillForeground()
                } else {
                    UIUtil.getLabelForeground()
                }
                repaint()
            }

        private var hovered: Boolean = false

        init {
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = BorderFactory.createEmptyBorder(JBUI.scale(4), JBUI.scale(10), JBUI.scale(4), JBUI.scale(10))
            selected = false
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) = onClick()
                override fun mouseEntered(e: MouseEvent) {
                    hovered = true
                    repaint()
                }

                override fun mouseExited(e: MouseEvent) {
                    hovered = false
                    repaint()
                }
            })
        }

        override fun paintComponent(g: Graphics) {
            if (selected || hovered) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    val arc = JBUI.scale(6)
                    if (selected) {
                        g2.color = ApiHelperUiStyle.activeTabPillBackground()
                        g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
                        g2.color = ApiHelperUiStyle.activeTabPillBorder()
                        g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
                    } else {
                        g2.color = ApiHelperUiStyle.hoverBackgroundColor()
                        g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
                    }
                } finally {
                    g2.dispose()
                }
            }
            super.paintComponent(g)
        }
    }

    /**
     * Body 子类型胶囊切换标签（none / form-data / x-www-form-urlencoded / json / xml / raw / binary）。
     */
    private class BodyTypePill(
        val type: BodyType,
        onClick: () -> Unit,
    ) : JLabel(type.label, CENTER) {

        var selected: Boolean = false
            set(value) {
                field = value
                font = ApiHelperUiStyle.smallFont(bold = value)
                foreground = if (value) {
                    ApiHelperUiStyle.activeTabPillForeground()
                } else {
                    UIUtil.getContextHelpForeground()
                }
                repaint()
            }

        private var hovered: Boolean = false

        init {
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = BorderFactory.createEmptyBorder(JBUI.scale(3), JBUI.scale(8), JBUI.scale(3), JBUI.scale(8))
            selected = false
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) = onClick()
                override fun mouseEntered(e: MouseEvent) {
                    hovered = true
                    repaint()
                }

                override fun mouseExited(e: MouseEvent) {
                    hovered = false
                    repaint()
                }
            })
        }

        override fun paintComponent(g: Graphics) {
            if (selected || hovered) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    val arc = JBUI.scale(6)
                    if (selected) {
                        g2.color = ApiHelperUiStyle.activeTabPillBackground()
                        g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
                        g2.color = ApiHelperUiStyle.activeTabPillBorder()
                        g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
                    } else {
                        g2.color = ApiHelperUiStyle.hoverBackgroundColor()
                        g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
                    }
                } finally {
                    g2.dispose()
                }
            }
            super.paintComponent(g)
        }
    }

    /**
     * 带左侧行号栏与垂直分隔线的代码滚动面板（贴合截图中响应/请求体代码区的 `1 | ...` 样式）。
     */
    private class LineNumberedCodePanel(private val textArea: JBTextArea) : JBScrollPane(textArea) {

        private val gutter = object : JComponent() {
            init {
                font = ApiHelperUiStyle.codeFont()
                border = BorderFactory.createMatteBorder(0, 0, 0, 1, ApiHelperUiStyle.subtleGridColor())
            }

            override fun getPreferredSize(): Dimension {
                val lines = textArea.lineCount.coerceAtLeast(1)
                val digits = lines.toString().length.coerceAtLeast(2)
                val fm = getFontMetrics(textArea.font)
                val width = JBUI.scale(16) + fm.charWidth('0') * digits
                return Dimension(width, textArea.preferredSize.height.coerceAtLeast(height))
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                    g2.color = textArea.background
                    g2.fillRect(0, 0, width, height)
                    g2.font = textArea.font
                    g2.color = UIUtil.getContextHelpForeground()

                    val fm = g2.fontMetrics
                    val lineHeight = fm.height
                    val topInset = textArea.insets.top
                    val lineCount = textArea.lineCount.coerceAtLeast(1)
                    val clip = g2.clipBounds
                    val startLine = ((clip.y - topInset) / lineHeight).coerceAtLeast(0)
                    val endLine = ((clip.y + clip.height - topInset) / lineHeight + 1).coerceAtMost(lineCount - 1)

                    for (line in startLine..endLine) {
                        val numStr = (line + 1).toString()
                        val y = topInset + line * lineHeight + fm.ascent
                        val x = width - JBUI.scale(10) - fm.stringWidth(numStr)
                        g2.drawString(numStr, x, y)
                    }
                } finally {
                    g2.dispose()
                }
            }
        }

        init {
            border = BorderFactory.createEmptyBorder()
            setRowHeaderView(gutter)
            textArea.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateGutter()
                override fun removeUpdate(e: DocumentEvent?) = updateGutter()
                override fun changedUpdate(e: DocumentEvent?) = updateGutter()

                private fun updateGutter() {
                    gutter.revalidate()
                    gutter.repaint()
                }
            })
        }
    }

    private class RowDeleteRenderer : JLabel(AllIcons.Actions.GC, CENTER), TableCellRenderer {

        init {
            horizontalAlignment = CENTER
            verticalAlignment = CENTER
            border = BorderFactory.createEmptyBorder()
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            background = if (isSelected) table.selectionBackground else table.background
            isOpaque = true
            return this
        }
    }

    private class FormDataValueRenderer(
        private val model: BodyFormDataTableModel,
    ) : JLabel(), TableCellRenderer {

        init {
            isOpaque = true
            font = ApiHelperUiStyle.labelFont()
            border = BorderFactory.createEmptyBorder(0, 8, 0, 8)
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val data = model.rowAt(row)
            val showUpload = data?.type == BodyFormDataTableModel.TYPE_FILE && data.value.isBlank()
            text = if (showUpload) {
                ApiHelperBundle.message("debug.action.upload")
            } else {
                value?.toString().orEmpty()
            }
            foreground = if (showUpload) {
                UIUtil.getContextHelpForeground()
            } else {
                table.foreground
            }
            border = if (showUpload) {
                BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ApiHelperUiStyle.borderColor()),
                    BorderFactory.createEmptyBorder(0, 8, 0, 8),
                )
            } else {
                BorderFactory.createEmptyBorder(0, 8, 0, 8)
            }
            background = if (isSelected) table.selectionBackground else table.background
            return this
        }
    }

    private data class DebugRequestInput(
        val method: String,
        val url: String,
        val query: List<Pair<String, String>>,
        val path: List<Pair<String, String>>,
        val headers: List<Pair<String, String>>,
        val cookies: List<Pair<String, String>>,
        val bodyType: BodyType,
        val body: String,
        val binaryPath: String,
        val formData: List<DebugParameterRow>,
        val urlEncoded: List<Pair<String, String>>,
    )

    private data class LimitedText(
        val text: String,
        val truncated: Boolean,
    )

    private enum class BodyType(
        val label: String,
        val card: String,
        val contentType: String,
    ) {
        NONE("none", "none", ""),
        FORM_DATA("form-data", "form-data", "multipart/form-data; boundary=$MULTIPART_BOUNDARY"),
        URL_ENCODED("x-www-form-urlencoded", "x-www-form-urlencoded", "application/x-www-form-urlencoded; charset=UTF-8"),
        JSON("json", BODY_TEXT_CARD, "application/json; charset=UTF-8"),
        XML("xml", BODY_TEXT_CARD, "application/xml; charset=UTF-8"),
        RAW("raw", BODY_TEXT_CARD, "text/plain; charset=UTF-8"),
        BINARY("binary", "binary", "application/octet-stream")
    }

    private enum class ResponseBodyType(private val labelKey: String) {
        JSON("debug.response.body.type.json"),
        XML("debug.response.body.type.xml"),
        HTML("debug.response.body.type.html"),
        TEXT("debug.response.body.type.text");

        override fun toString(): String = ApiHelperBundle.message(labelKey)
    }

    companion object {
        private const val TAB_QUERY = "query"
        private const val TAB_BODY = "body"
        private const val TAB_PATH = "path"
        private const val TAB_HEADERS = "headers"
        private const val TAB_COOKIES = "cookies"

        private const val RESP_TAB_BODY = "resp_body"
        private const val RESP_TAB_COOKIES = "resp_cookies"
        private const val RESP_TAB_HEADERS = "resp_headers"

        private const val BODY_TEXT_CARD = "text"
        private const val MULTIPART_BOUNDARY = "ApiHelperBoundary"
        private const val DEFAULT_SERVER_PORT = 8080
        private const val MB = 1024 * 1024
        private const val MAX_RESPONSE_PREVIEW_BYTES = 5 * MB
        private const val CONNECT_TIMEOUT_MIN_SECONDS = 1
        private const val CONNECT_TIMEOUT_MAX_SECONDS = 300
        private const val REQUEST_TIMEOUT_MIN_SECONDS = 1
        private const val REQUEST_TIMEOUT_MAX_SECONDS = 600

        private fun chooseLocalFile(): String? {
            val chooser = JFileChooser()
            return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile.absolutePath
            } else {
                null
            }
        }
    }
}
