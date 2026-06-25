package com.lizhuolun.apihelper.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.cache.BilateralMappingCacheService
import com.lizhuolun.apihelper.ui.component.ApiHelperUiStyle
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

    override fun getDisplayName(): String = ApiHelperBundle.message("settings.title")

    override fun createComponent(): JComponent {
        reset()
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(ApiHelperBundle.message("settings.active.profile.label"), profileTextField, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val state = ApiHelperSettings.getInstance().state
        return profileTextField.text.trim() != state.manualActiveProfile
    }

    override fun apply() {
        val state = ApiHelperSettings.getInstance().state
        val newProfile = profileTextField.text.trim()
        val profileChanged = state.manualActiveProfile != newProfile
        state.manualActiveProfile = newProfile
        if (profileChanged) {
            ProjectManager.getInstance().openProjects.forEach { project ->
                BilateralMappingCacheService.of(project).scheduleControllerRefresh(delayMillis = 0)
            }
        }
    }

    override fun reset() {
        val state = ApiHelperSettings.getInstance().state
        profileTextField.text = state.manualActiveProfile
    }
}
