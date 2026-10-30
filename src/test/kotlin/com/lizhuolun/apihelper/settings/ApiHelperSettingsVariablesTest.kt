package com.lizhuolun.apihelper.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiHelperSettingsVariablesTest {

    @Test
    fun `global variables apply without environment`() {
        val settings = settingsWith(
            globalVariables = """
                token=global-token
                [disabled] off=x
            """.trimIndent(),
        )

        assertEquals(mapOf("token" to "global-token"), settings.resolveVariables(""))
    }

    @Test
    fun `environment variables override global variables`() {
        val settings = settingsWith(
            globalVariables = """
                token=global-token
                tenant=1001
            """.trimIndent(),
            environmentVariables = """
                Dev.token=dev-token
                Test.token=test-token
            """.trimIndent(),
        )

        assertEquals(
            mapOf("token" to "dev-token", "tenant" to "1001"),
            settings.resolveVariables("Dev"),
        )
        assertEquals(
            mapOf("token" to "test-token", "tenant" to "1001"),
            settings.resolveVariables("Test"),
        )
        assertEquals(
            mapOf("token" to "global-token", "tenant" to "1001"),
            settings.resolveVariables("Unknown"),
        )
    }

    @Test
    fun `disabled environment variable does not override`() {
        val settings = settingsWith(
            globalVariables = "token=global-token",
            environmentVariables = "[disabled] Dev.token=dev-token",
        )

        assertEquals(mapOf("token" to "global-token"), settings.resolveVariables("Dev"))
    }

    @Test
    fun `upsert variable appends and overrides`() {
        val settings = ApiHelperSettings()

        settings.upsertVariable("token", "v1")
        settings.upsertVariable("token", "v2")
        settings.upsertVariable("token", "dev-v3", environmentName = "Dev")

        assertEquals("token=v2", settings.state.globalVariablesText)
        assertEquals("Dev.token=dev-v3", settings.state.environmentVariablesText)
        assertEquals(mapOf("token" to "dev-v3"), settings.resolveVariables("Dev"))
    }

    private fun settingsWith(
        globalVariables: String = "",
        environmentVariables: String = "",
    ): ApiHelperSettings {
        val state = ApiHelperSettings.SettingsState()
        state.globalVariablesText = globalVariables
        state.environmentVariablesText = environmentVariables
        return ApiHelperSettings().apply { loadState(state) }
    }
}
