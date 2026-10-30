package com.lizhuolun.apihelper.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.cache.BilateralMappingCacheService
import com.lizhuolun.apihelper.ui.EndpointToolWindowService
import com.lizhuolun.apihelper.ui.component.ApiHelperUiStyle
import com.lizhuolun.apihelper.ui.debug.EnvironmentManagePanel
import com.lizhuolun.apihelper.ui.debug.GlobalParamsPanel
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * ApiHelper 设置页面，挂在 Settings -> Tools -> ApiHelper 下。
 *
 * 采用清晰的三大功能分组布局，并直接复用可视化表格组件管理“前置 URL”与“全局公共参数”，
 * 告别手写多行纯文本的繁琐体验，且与工具窗口内的标签页完全双向同步。
 *
 * @author lizhuolun
 * @date 2026/6/9
 */
class ApiHelperConfigurable : Configurable {

    private val profileTextField = JBTextField().apply {
        emptyText.text = ApiHelperBundle.message("settings.active.profile.placeholder")
        ApiHelperUiStyle.applyTextField(this)
    }

    private val connectTimeoutSpinner = JBIntSpinner(CONNECT_TIMEOUT_DEFAULT, CONNECT_TIMEOUT_MIN, CONNECT_TIMEOUT_MAX)
    private val requestTimeoutSpinner = JBIntSpinner(REQUEST_TIMEOUT_DEFAULT, REQUEST_TIMEOUT_MIN, REQUEST_TIMEOUT_MAX)

    private val envManagePanel = EnvironmentManagePanel(project = null, autoPersist = false).apply {
        preferredSize = Dimension(JBUI.scale(520), JBUI.scale(150))
        border = BorderFactory.createLineBorder(ApiHelperUiStyle.subtleGridColor())
    }

    private val globalParamsPanel = GlobalParamsPanel(autoPersist = false).apply {
        preferredSize = Dimension(JBUI.scale(520), JBUI.scale(180))
        border = BorderFactory.createLineBorder(ApiHelperUiStyle.subtleGridColor())
    }

    override fun getDisplayName(): String = ApiHelperBundle.message("settings.title")

    override fun createComponent(): JComponent {
        reset()

        val timeoutRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
            isOpaque = false
            add(connectTimeoutSpinner)
            add(JBLabel(ApiHelperBundle.message("settings.timeout.seconds.unit")).apply {
                foreground = UIUtil.getContextHelpForeground()
                font = ApiHelperUiStyle.smallFont()
            })
            add(JBLabel("   ${ApiHelperBundle.message("settings.request.timeout.label")}:").apply {
                border = BorderFactory.createEmptyBorder(0, JBUI.scale(12), 0, 0)
            })
            add(requestTimeoutSpinner)
            add(JBLabel(ApiHelperBundle.message("settings.timeout.seconds.unit")).apply {
                foreground = UIUtil.getContextHelpForeground()
                font = ApiHelperUiStyle.smallFont()
            })
            add(JButton(ApiHelperBundle.message("settings.action.restore.defaults")).apply {
                ApiHelperUiStyle.applyToolbarPillButton(this)
                addActionListener {
                    profileTextField.text = ""
                    connectTimeoutSpinner.number = CONNECT_TIMEOUT_DEFAULT
                    requestTimeoutSpinner.number = REQUEST_TIMEOUT_DEFAULT
                }
            })
        }

        val generalSection = FormBuilder.createFormBuilder()
            .addLabeledComponent(ApiHelperBundle.message("settings.active.profile.label"), profileTextField, 1, false)
            .addLabeledComponent(ApiHelperBundle.message("settings.connect.timeout.label"), timeoutRow, 1, false)
            .panel.apply {
                border = IdeBorderFactory.createTitledBorder(ApiHelperBundle.message("settings.section.general"), false)
            }

        val envSection = JPanel(BorderLayout()).apply {
            border = IdeBorderFactory.createTitledBorder(ApiHelperBundle.message("settings.section.environments"), false)
            add(envManagePanel, BorderLayout.CENTER)
        }

        val globalParamsSection = JPanel(BorderLayout()).apply {
            border = IdeBorderFactory.createTitledBorder(ApiHelperBundle.message("settings.section.global.params"), false)
            add(globalParamsPanel, BorderLayout.CENTER)
        }

        return FormBuilder.createFormBuilder()
            .addComponent(generalSection)
            .addVerticalGap(4)
            .addComponent(envSection)
            .addVerticalGap(4)
            .addComponent(globalParamsSection)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val state = ApiHelperSettings.getInstance().state
        return profileTextField.text.trim() != state.manualActiveProfile ||
            connectTimeoutSpinner.number != state.connectTimeoutSeconds ||
            requestTimeoutSpinner.number != state.requestTimeoutSeconds ||
            envManagePanel.exportEnvironmentsText() != state.environmentsText.trim() ||
            envManagePanel.exportSelectedEnvironmentName() != state.selectedEnvironmentName ||
            globalParamsPanel.exportGlobalHeadersText() != state.globalHeadersText.trim() ||
            globalParamsPanel.exportGlobalQueriesText() != state.globalQueriesText.trim() ||
            globalParamsPanel.exportGlobalCookiesText() != state.globalCookiesText.trim() ||
            globalParamsPanel.exportGlobalVariablesText() != state.globalVariablesText.trim() ||
            globalParamsPanel.exportEnvironmentVariablesText() != state.environmentVariablesText.trim()
    }

    override fun apply() {
        val state = ApiHelperSettings.getInstance().state
        val newProfile = profileTextField.text.trim()
        val profileChanged = state.manualActiveProfile != newProfile
        state.manualActiveProfile = newProfile
        state.connectTimeoutSeconds = connectTimeoutSpinner.number
        state.requestTimeoutSeconds = requestTimeoutSpinner.number
        envManagePanel.persistToSettings()
        globalParamsPanel.persistToSettings()

        ProjectManager.getInstance().openProjects.forEach { project ->
            if (profileChanged) {
                BilateralMappingCacheService.of(project).scheduleControllerRefresh(delayMillis = 0)
            }
            EndpointToolWindowService.of(project).reloadDebugEnvironments()
        }
    }

    override fun reset() {
        val state = ApiHelperSettings.getInstance().state
        profileTextField.text = state.manualActiveProfile
        connectTimeoutSpinner.number = state.connectTimeoutSeconds
        requestTimeoutSpinner.number = state.requestTimeoutSeconds
        envManagePanel.loadFromSettings()
        globalParamsPanel.loadFromSettings()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MIN = 1
        private const val CONNECT_TIMEOUT_MAX = 300
        private const val CONNECT_TIMEOUT_DEFAULT = 30
        private const val REQUEST_TIMEOUT_MIN = 1
        private const val REQUEST_TIMEOUT_MAX = 600
        private const val REQUEST_TIMEOUT_DEFAULT = 60
    }
}
