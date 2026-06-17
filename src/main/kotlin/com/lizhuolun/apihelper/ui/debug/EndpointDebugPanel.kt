package com.lizhuolun.apihelper.ui.debug

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.config.ApplicationConfigReader
import com.lizhuolun.apihelper.core.HttpMethod
import com.lizhuolun.apihelper.settings.ApiHelperSettings
import com.lizhuolun.apihelper.ui.EndpointTreeItem
import com.lizhuolun.apihelper.ui.component.ApiHelperUiStyle
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.io.ByteArrayOutputStream
import java.net.HttpCookie
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.time.Duration
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.DefaultCellEditor
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JTable
import javax.swing.JToolBar
import javax.swing.ListSelectionModel
import javax.swing.UIManager
import javax.swing.table.TableCellRenderer

/**
 * 接口调试面板，提供轻量请求发送与响应查看能力。
 *
 * @param project 当前工程
 * @author lizhuolun
 * @date 2026/6/16
 */
class EndpointDebugPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val log = thisLogger()
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val methodBox = ComboBox(arrayOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")).apply {
        selectedItem = "GET"
        ApiHelperUiStyle.applyComboBox(this, width = 82, bold = true)
    }
    private val urlField = JBTextField().apply {
        emptyText.text = ApiHelperBundle.message("debug.url.placeholder")
        ApiHelperUiStyle.applyTextField(this)
    }
    private val sendButton = SendButton(ApiHelperBundle.message("debug.action.send")).apply {
        addActionListener { sendRequest() }
    }
    private val requestStatusLabel = JBLabel(ApiHelperBundle.message("debug.status.ready")).apply {
        border = BorderFactory.createEmptyBorder(3, 0, 0, 0)
        foreground = UIUtil.getContextHelpForeground()
        font = font?.deriveFont(12f)
    }

    private val queryModel = DebugParameterTableModel(showDeleteColumn = true)
    private val pathModel = DebugParameterTableModel(showDeleteColumn = true)
    private val headerModel = DebugParameterTableModel(showDeleteColumn = true)
    private val cookieModel = DebugParameterTableModel(showDeleteColumn = true)
    private val formDataModel = BodyFormDataTableModel()
    private val urlEncodedModel = DebugParameterTableModel(showDeleteColumn = true)

    private val bodyTypeGroup = ButtonGroup()
    private val bodyCardLayout = CardLayout()
    private val bodyContentPanel = JPanel(bodyCardLayout)
    private var currentServerPort = DEFAULT_SERVER_PORT
    private var selectedBodyType = BodyType.NONE
    private val requestBodyArea = JBTextArea().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = false
    }
    private var binaryPath: String = ""
    private val binaryPathLabel = JBLabel(ApiHelperBundle.message("debug.body.binary.placeholder")).apply {
        foreground = UIUtil.getContextHelpForeground()
        font = font?.deriveFont(Font.PLAIN, 12f)
    }
    private val responseBodyArea = readOnlyArea()
    private val responseHeaderArea = readOnlyArea()
    private val responseCookieArea = readOnlyArea()
    private val responseBodyTypeBox = ComboBox(ResponseBodyType.entries.toTypedArray()).apply {
        selectedItem = ResponseBodyType.JSON
        ApiHelperUiStyle.applyComboBox(this, width = 108, bold = true)
        addActionListener { renderResponseBody() }
    }
    private val contentTabs = JBTabbedPane()
    private var lastResponseBody: String = ""

    init {
        border = BorderFactory.createEmptyBorder()
        add(buildRequestBar(), BorderLayout.NORTH)

        val requestTabs = JBTabbedPane().apply {
            addTab(ApiHelperBundle.message("debug.tab.query"), tablePanel(queryModel))
            addTab(ApiHelperBundle.message("debug.tab.body"), bodyPanel())
            addTab(ApiHelperBundle.message("debug.tab.path"), tablePanel(pathModel))
            addTab(ApiHelperBundle.message("debug.tab.headers"), tablePanel(headerModel))
            addTab(ApiHelperBundle.message("debug.tab.cookies"), tablePanel(cookieModel))
        }

        val responseTabs = JBTabbedPane().apply {
            addTab(ApiHelperBundle.message("debug.tab.body"), responseBodyPanel())
            addTab(ApiHelperBundle.message("debug.tab.cookies"), JBScrollPane(responseCookieArea))
            addTab(ApiHelperBundle.message("debug.tab.headers"), JBScrollPane(responseHeaderArea))
        }

        contentTabs.apply {
            addTab(ApiHelperBundle.message("debug.section.request"), requestTabs)
            addTab(ApiHelperBundle.message("debug.section.response"), responseTabs)
        }
        add(contentTabs, BorderLayout.CENTER)
    }

    /**
     * 从端点树加载一个接口到调试面板。
     *
     * @param item 端点展示项
     */
    fun loadEndpoint(item: EndpointTreeItem) {
        methodBox.selectedItem = if (item.httpMethod == HttpMethod.ANY) "GET" else item.httpMethod.name
        currentServerPort = resolveServerPort(item)
        urlField.text = normalizeInitialUrl(item.url, currentServerPort)
        pathModel.setNames(extractPathVariables(item.url))
        queryModel.clear()
        headerModel.clear()
        cookieModel.clear()
        requestStatusLabel.text = ApiHelperBundle.message("debug.status.loaded", item.methodName)
        contentTabs.selectedIndex = 0
    }

    private fun buildRequestBar(): JPanel {
        return JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.borderColor()),
                BorderFactory.createEmptyBorder(5, 8, 5, 8),
            )
            add(methodBox, BorderLayout.WEST)
            add(urlField, BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
            add(requestStatusLabel, BorderLayout.SOUTH)
        }
    }

    private fun bodyPanel(): JPanel {
        val typeBar = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(14), JBUI.scale(6))).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.borderColor())
            for (type in BodyType.entries) {
                add(bodyTypeRadio(type))
            }
        }

        bodyContentPanel.apply {
            add(emptyBodyPanel(ApiHelperBundle.message("debug.body.none.hint")), BodyType.NONE.card)
            add(formDataPanel(), BodyType.FORM_DATA.card)
            add(urlEncodedPanel(), BodyType.URL_ENCODED.card)
            add(textBodyPanel(), BODY_TEXT_CARD)
            add(binaryBodyPanel(), BodyType.BINARY.card)
        }

        return JPanel(BorderLayout()).apply {
            add(typeBar, BorderLayout.NORTH)
            add(bodyContentPanel, BorderLayout.CENTER)
        }
    }

    private fun bodyTypeRadio(type: BodyType): JRadioButton {
        return JRadioButton(type.label).apply {
            isOpaque = false
            font = font?.deriveFont(Font.PLAIN, 12f)
            isSelected = type == selectedBodyType
            bodyTypeGroup.add(this)
            addActionListener {
                selectedBodyType = type
                bodyCardLayout.show(bodyContentPanel, type.card)
            }
        }
    }

    private fun formDataPanel(): JPanel {
        val table = parameterTable(formDataModel).apply {
            columnModel.getColumn(0).maxWidth = JBUI.scale(42)
            columnModel.getColumn(0).preferredWidth = JBUI.scale(42)
            columnModel.getColumn(3).preferredWidth = JBUI.scale(120)
            columnModel.getColumn(3).cellEditor = DefaultCellEditor(
                JComboBox(arrayOf(BodyFormDataTableModel.TYPE_TEXT, BodyFormDataTableModel.TYPE_FILE)),
            )
            columnModel.getColumn(4).maxWidth = JBUI.scale(48)
            columnModel.getColumn(4).preferredWidth = JBUI.scale(48)
            columnModel.getColumn(4).cellRenderer = RowDeleteRenderer()
            columnModel.getColumn(2).cellRenderer = FormDataValueRenderer(formDataModel)
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
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
        return tablePanel(table, formDataModel::clear)
    }

    private fun urlEncodedPanel(): JPanel {
        val table = parameterTable(urlEncodedModel).apply {
            configureDeleteColumn(this) { row -> urlEncodedModel.removeRow(row) }
        }
        return tablePanel(table, urlEncodedModel::clear)
    }

    private fun textBodyPanel(): JPanel {
        val toolbar = JToolBar().apply {
            isFloatable = false
            border = BorderFactory.createEmptyBorder(3, 4, 3, 4)
            background = UIUtil.getPanelBackground()
            add(JButton(ApiHelperBundle.message("debug.action.format")).apply {
                addActionListener { formatJsonBody() }
            })
            add(JButton(ApiHelperBundle.message("debug.action.clear")).apply {
                addActionListener { requestBodyArea.text = "" }
            })
        }
        return JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(JBScrollPane(requestBodyArea), BorderLayout.CENTER)
        }
    }

    private fun emptyBodyPanel(text: String): JPanel {
        return JPanel(BorderLayout()).apply {
            add(JBLabel(text).apply {
                foreground = UIUtil.getContextHelpForeground()
                border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
            }, BorderLayout.NORTH)
        }
    }

    private fun binaryBodyPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
            val uploadButton = JButton(ApiHelperBundle.message("debug.action.upload")).apply {
                ApiHelperUiStyle.applyOutlinedButton(this, width = 84)
                addActionListener {
                    chooseLocalFile()?.let {
                        binaryPath = it
                        binaryPathLabel.text = it
                        binaryPathLabel.foreground = UIUtil.getLabelForeground()
                    }
                }
            }
            val deleteLabel = JLabel(AllIcons.Actions.GC).apply {
                horizontalAlignment = JLabel.CENTER
                verticalAlignment = JLabel.CENTER
                preferredSize = Dimension(JBUI.scale(32), JBUI.scale(30))
                addMouseListener(object : java.awt.event.MouseAdapter() {
                    override fun mouseClicked(e: java.awt.event.MouseEvent) {
                        binaryPath = ""
                        binaryPathLabel.text = ApiHelperBundle.message("debug.body.binary.placeholder")
                        binaryPathLabel.foreground = UIUtil.getContextHelpForeground()
                    }
                })
            }
            val row = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ApiHelperUiStyle.borderColor()),
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

    private fun tablePanel(model: DebugParameterTableModel): JPanel {
        val table = parameterTable(model)
        configureDeleteColumn(table) { row -> model.removeRow(row) }
        return tablePanel(table, model::clear)
    }

    private fun parameterTable(model: javax.swing.table.TableModel): JTable =
        JTable(model).apply {
            fillsViewportHeight = true
            rowHeight = JBUI.scale(30)
            font = font?.deriveFont(12f)
            tableHeader.font = tableHeader.font?.deriveFont(Font.PLAIN, 12f)
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            tableHeader.reorderingAllowed = false
        }

    private fun configureDeleteColumn(table: JTable, deleteAction: (row: Int) -> Unit) {
        val deleteColumn = table.columnModel.getColumn(table.columnCount - 1)
        deleteColumn.maxWidth = JBUI.scale(48)
        deleteColumn.preferredWidth = JBUI.scale(48)
        deleteColumn.cellRenderer = RowDeleteRenderer()
        table.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                val row = table.rowAtPoint(e.point)
                val column = table.columnAtPoint(e.point)
                if (row >= 0 && column == table.columnCount - 1) {
                    deleteAction(row)
                }
            }
        })
    }

    private fun tablePanel(table: JTable, clearAction: () -> Unit): JPanel {
        val toolbar = JToolBar().apply {
            isFloatable = false
            border = BorderFactory.createEmptyBorder(3, 4, 3, 4)
            background = UIUtil.getPanelBackground()
            add(JButton(ApiHelperBundle.message("debug.action.clear")).apply {
                addActionListener { clearAction() }
            })
        }
        return JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(JBScrollPane(table), BorderLayout.CENTER)
        }
    }

    private fun responseBodyPanel(): JPanel {
        val toolbar = JToolBar().apply {
            isFloatable = false
            border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
            background = UIUtil.getPanelBackground()
            add(responseBodyTypeBox)
        }
        return JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(JBScrollPane(responseBodyArea), BorderLayout.CENTER)
        }
    }

    private fun readOnlyArea(): JBTextArea =
        JBTextArea().apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            lineWrap = false
        }

    private fun sendRequest() {
        val input = DebugRequestInput(
            method = methodBox.selectedItem?.toString().orEmpty(),
            url = urlField.text.trim(),
            query = queryModel.toPairs(),
            path = pathModel.toPairs(),
            headers = headerModel.toPairs(),
            cookies = cookieModel.toPairs(),
            bodyType = selectedBodyType,
            body = requestBodyArea.text,
            binaryPath = binaryPath,
            formData = formDataModel.toRows(),
            urlEncoded = urlEncodedModel.toPairs(),
        )

        sendButton.isEnabled = false
        requestStatusLabel.text = ApiHelperBundle.message("debug.status.sending")
        requestStatusLabel.foreground = UIUtil.getContextHelpForeground()
        lastResponseBody = ""
        responseBodyArea.text = ""
        responseHeaderArea.text = ""
        responseCookieArea.text = ""

        ApplicationManager.getApplication().executeOnPooledThread {
            val started = System.nanoTime()
            try {
                val request = buildRequest(input)
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                val elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis()
                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) return@invokeLater
                    showResponse(response, elapsedMs)
                    contentTabs.selectedIndex = 1
                    sendButton.isEnabled = true
                }
            } catch (e: Exception) {
                val elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis()
                log.warn("ApiHelper: 接口调试请求失败, url=${input.url}", e)
                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) return@invokeLater
                    requestStatusLabel.text = ApiHelperBundle.message("debug.status.failed", elapsedMs)
                    requestStatusLabel.foreground = UIUtil.getErrorForeground()
                    lastResponseBody = e.message ?: e::class.java.name
                    renderResponseBody()
                    contentTabs.selectedIndex = 1
                    sendButton.isEnabled = true
                }
            }
        }
    }

    private fun buildRequest(input: DebugRequestInput): HttpRequest {
        val method = input.method.ifBlank { "GET" }.uppercase()
        val requestUri = URI.create(appendQuery(applyPathParams(normalizeInitialUrl(input.url, currentServerPort), input.path), input.query))
        val builder = HttpRequest.newBuilder(requestUri)
            .timeout(Duration.ofSeconds(60))

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
                HttpRequest.BodyPublishers.ofByteArray(Files.readAllBytes(Path.of(input.binaryPath.trim())))
            }
            input.bodyType == BodyType.FORM_DATA -> {
                if (!hasHeader(input.headers, "Content-Type")) {
                    builder.setHeader("Content-Type", input.bodyType.contentType)
                }
                HttpRequest.BodyPublishers.ofByteArray(multipartBytes(input.formData))
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

    private fun showResponse(response: HttpResponse<String>, elapsedMs: Long) {
        requestStatusLabel.text = ApiHelperBundle.message(
            "debug.status.done",
            response.statusCode(),
            elapsedMs,
        )
        requestStatusLabel.foreground = UIUtil.getContextHelpForeground()
        lastResponseBody = response.body().orEmpty()
        renderResponseBody()
        responseHeaderArea.text = response.headers().map().entries
            .sortedBy { it.key }
            .joinToString("\n") { (name, values) -> "$name: ${values.joinToString(", ")}" }
        responseHeaderArea.caretPosition = 0
        responseCookieArea.text = response.headers().allValues("set-cookie")
            .flatMap { value -> runCatching { HttpCookie.parse(value) }.getOrDefault(emptyList()) }
            .joinToString("\n") { cookie -> "${cookie.name}=${cookie.value}" }
        responseCookieArea.caretPosition = 0
    }

    private fun renderResponseBody() {
        val type = responseBodyTypeBox.selectedItem as? ResponseBodyType ?: ResponseBodyType.JSON
        responseBodyArea.text = when (type) {
            ResponseBodyType.JSON -> formatResponseJson(lastResponseBody)
            ResponseBodyType.XML, ResponseBodyType.HTML, ResponseBodyType.TEXT -> lastResponseBody
        }
        responseBodyArea.caretPosition = 0
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

    private fun multipartBytes(rows: List<DebugParameterRow>): ByteArray {
        val output = ByteArrayOutputStream()
        for (row in rows) {
            output.writeString("--$MULTIPART_BOUNDARY\r\n")
            if (row.type == BodyFormDataTableModel.TYPE_FILE) {
                val path = Path.of(row.value)
                val fileName = path.fileName?.toString().orEmpty()
                output.writeString("Content-Disposition: form-data; name=\"${row.name}\"; filename=\"$fileName\"\r\n\r\n")
                output.write(Files.readAllBytes(path))
                output.writeString("\r\n")
            } else {
                output.writeString("Content-Disposition: form-data; name=\"${row.name}\"\r\n\r\n")
                output.writeString("${row.value}\r\n")
            }
        }
        output.writeString("--$MULTIPART_BOUNDARY--\r\n")
        return output.toByteArray()
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
     * 主操作按钮，使用 IDE 默认按钮样式。
     */
    private class SendButton(text: String) : JButton(text) {

        init {
            icon = AllIcons.Actions.Execute
            ApiHelperUiStyle.applyOutlinedButton(this, width = 86, bold = true)
            iconTextGap = JBUI.scale(4)
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
        ): java.awt.Component {
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
        private const val BODY_TEXT_CARD = "text"
        private const val MULTIPART_BOUNDARY = "ApiHelperBoundary"
        private const val DEFAULT_SERVER_PORT = 8080

        private fun chooseLocalFile(): String? {
            val chooser = JFileChooser()
            return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile.absolutePath
            } else {
                null
            }
        }

        private fun ByteArrayOutputStream.writeString(value: String) {
            write(value.toByteArray(StandardCharsets.UTF_8))
        }
    }
}
