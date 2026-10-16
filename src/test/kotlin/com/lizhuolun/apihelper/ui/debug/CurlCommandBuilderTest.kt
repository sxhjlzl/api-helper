package com.lizhuolun.apihelper.ui.debug

import com.lizhuolun.apihelper.settings.ApiHelperSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurlCommandBuilderTest {

    @Test
    fun `merges global headers with endpoint override`() {
        val global = ApiHelperSettings.parseKeyValueLines(
            """
                # comment line
                Authorization: Bearer global-token
                X-Tenant-Id: 1001
            """.trimIndent(),
            charArrayOf(':', '='),
        )
        val endpoint = listOf("authorization" to "Bearer endpoint-token", "X-Trace-Id" to "abc")

        val merged = CurlCommandBuilder.mergeHeaders(global, endpoint)

        assertEquals(
            listOf(
                "X-Tenant-Id" to "1001",
                "authorization" to "Bearer endpoint-token",
                "X-Trace-Id" to "abc",
            ),
            merged,
        )
    }

    @Test
    fun `builds curl with path query headers cookies and json body`() {
        val curl = CurlCommandBuilder.build(
            method = "POST",
            rawUrl = "http://localhost:8080/api/orders/{id}",
            pathParams = listOf("id" to "42"),
            queryParams = listOf("source" to "web app"),
            headers = listOf("Authorization" to "Bearer token"),
            cookies = listOf("sid" to "xyz"),
            contentType = "application/json; charset=UTF-8",
            bodyText = "{\"name\":\"demo\"}",
        )

        assertTrue(curl.contains("curl -X POST 'http://localhost:8080/api/orders/42?source=web%20app'"))
        assertTrue(curl.contains("-H 'Authorization: Bearer token'"))
        assertTrue(curl.contains("-H 'Content-Type: application/json; charset=UTF-8'"))
        assertTrue(curl.contains("-H 'Cookie: sid=xyz'"))
        assertTrue(curl.contains("-d '{\"name\":\"demo\"}'"))
    }
}
