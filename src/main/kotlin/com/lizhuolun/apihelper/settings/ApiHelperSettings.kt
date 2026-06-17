package com.lizhuolun.apihelper.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * ApiHelper 全局设置，应用级 Service，通过 PersistentStateComponent 持久化到 IDE 配置目录。
 *
 * 业务代码通过 ApiHelperSettings.getInstance().state 读取或修改设置项。
 *
 * @author lizhuolun
 * @date 2026/6/9
 */
@Service(Service.Level.APP)
@State(
    name = "ApiHelperSettings",
    storages = [Storage("ApiHelperSettings.xml")],
)
class ApiHelperSettings : PersistentStateComponent<ApiHelperSettings.SettingsState> {

    /**
     * 可序列化的设置状态。
     *
     * 字段必须是 var、public、有空构造器，XML 序列化器才能正确读写。
     */
    class SettingsState {
        /**
         * 手动覆盖 Spring 激活的 profile，逗号分隔；为空表示自动从配置推断
         **/
        var manualActiveProfile: String = ""
    }

    private var internalState: SettingsState = SettingsState()

    override fun getState(): SettingsState = internalState

    override fun loadState(state: SettingsState) {
        XmlSerializerUtil.copyBean(state, internalState)
    }

    companion object {
        fun getInstance(): ApiHelperSettings =
            ApplicationManager.getApplication().getService(ApiHelperSettings::class.java)
    }
}
