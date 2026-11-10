package com.lizhuolun.apihelper.ui.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpRequestFileBuilderTest {

    @Test
    fun `build creates executable HTTP Client request`() {
        val request = EndpointRequestData(
            method = "POST",
            rawUrl = "http://localhost:8080/rpc-api/promotion/coupon/take-by-admin",
            queryParams = listOf("userId" to "1"),
            cookies = listOf("SESSION" to "abc"),
            contentType = "application/json; charset=UTF-8",
            bodyText = "{\n  \"1\": 2,\n  \"name\": \"O'Reilly\"\n}",
        )

        assertEquals(
            """
                POST http://localhost:8080/rpc-api/promotion/coupon/take-by-admin?userId=1
                Content-Type: application/json; charset=UTF-8
                Cookie: SESSION=abc

                {
                  "1": 2,
                  "name": "O'Reilly"
                }
            """.trimIndent() + "\n",
            HttpRequestFileBuilder.build(request),
        )
    }
}
