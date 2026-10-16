package com.lizhuolun.apihelper.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.cache.BilateralMappingCacheService
import com.lizhuolun.apihelper.ui.EndpointToolWindowService
import com.lizhuolun.apihelper.ui.component.ApiHelperUiStyle
import java.awt.Dimension
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * ApiHelper 设置页面，挂在 Settings -> Tools 下。
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

    private val environmentsArea = JBTextArea(4, 40).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        emptyText.text = ApiHelperBundle.message("settings.environments.placeholder")
    }

    private val globalHeadersArea = JBTextArea(5, 40).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        emptyText.text = ApiHelperBundle.message("settings.global.headers.placeholder")
    }

    override fun getDisplayName(): String = ApiHelperBundle.message("settings.title")

    override fun createComponent(): JComponent {
        reset()
        val envScroll = JBScrollPane(environmentsArea).apply {
            preferredSize = Dimension(JBUI.scale(420), JBUI.scale(90))
        }
        val headersScroll = JBScrollPane(globalHeadersArea).apply {
            preferredSize = Dimension(JBUI.scale(420), JBUI.scale(110))
        }
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(ApiHelperBundle.message("settings.active.profile.label"), profileTextField, 1, false)
            .addLabeledComponent(ApiHelperBundle.message("settings.connect.timeout.label"), connectTimeoutSpinner)
            .addLabeledComponent(ApiHelperBundle.message("settings.request.timeout.label"), requestTimeoutSpinner)
            .addLabeledComponent(ApiHelperBundle.message("settings.environments.label"), envScroll, 1, true)
            .addLabeledComponent(ApiHelperBundle.message("settings.global.headers.label"), headersScroll, 1, true)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val state = ApiHelperSettings.getInstance().state
        return profileTextField.text.trim() != state.manualActiveProfile ||
                connectTimeoutSpinner.number != state.connectTimeoutSeconds ||
                requestTimeoutSpinner.number != state.requestTimeoutSeconds ||
                environmentsArea.text.trim() != state.environmentsText.trim() ||
                globalHeadersArea.text.trim() != state.globalHeadersText.trim()
    }

    override fun apply() {
        val state = ApiHelperSettings.getInstance().state
        val newProfile = profileTextField.text.trim()
        val profileChanged = state.manualActiveProfile != newProfile
        val envChanged = state.environmentsText.trim() != environmentsArea.text.trim()
        state.manualActiveProfile = newProfile
        state.connectTimeoutSeconds = connectTimeoutSpinner.number
        state.requestTimeoutSeconds = requestTimeoutSpinner.number
        state.environmentsText = environmentsArea.text.trim()
        state.globalHeadersText = globalHeadersArea.text.trim()
        ProjectManager.getInstance().openProjects.forEach { project ->
            if (profileChanged) {
                BilateralMappingCacheService.of(project).scheduleControllerRefresh(delayMillis = 0)
            }
            if (envChanged) {
                EndpointToolWindowService.of(project).reloadDebugEnvironments()
            }
        }
    }

    override fun reset() {
        val state = ApiHelperSettings.getInstance().state
        profileTextField.text = state.manualActiveProfile
        connectTimeoutSpinner.number = state.connectTimeoutSeconds
        requestTimeoutSpinner.number = state.requestTimeoutSeconds
        environmentsArea.text = state.environmentsText
        globalHeadersArea.text = state.globalHeadersText
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
