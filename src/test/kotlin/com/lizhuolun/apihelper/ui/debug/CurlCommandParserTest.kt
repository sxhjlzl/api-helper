package com.lizhuolun.apihelper.ui.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurlCommandParserTest {

    @Test
    fun `parses simple get request`() {
        val parsed = CurlCommandParser.parse("curl https://api.example.com/users")!!

        assertEquals("GET", parsed.method)
        assertEquals("https://api.example.com/users", parsed.url)
        assertTrue(parsed.headers.isEmpty())
    }

    @Test
    fun `parses method headers and query`() {
        val parsed = CurlCommandParser.parse(
            "curl -X POST 'https://api.example.com/users?page=1&size=20' -H 'Authorization: Bearer abc'",
        )!!

        assertEquals("POST", parsed.method)
        assertEquals("https://api.example.com/users", parsed.url)
        assertEquals(listOf("page" to "1", "size" to "20"), parsed.query)
        assertEquals(listOf("Authorization" to "Bearer abc"), parsed.headers)
    }

    @Test
    fun `parses multiline command with json body`() {
        val parsed = CurlCommandParser.parse(
            """
            curl -X POST https://api.example.com/orders \
              -H 'Content-Type: application/json' \
              -d '{"name":"foo","count":2}'
            """.trimIndent(),
        )!!

        assertEquals("POST", parsed.method)
        assertEquals("{\"name\":\"foo\",\"count\":2}", parsed.bodyText)
        assertEquals(listOf("Content-Type" to "application/json"), parsed.headers)
    }

    @Test
    fun `defaults to post when body present without explicit method`() {
        val parsed = CurlCommandParser.parse("curl https://a.com/x -d 'a=1'")!!

        assertEquals("POST", parsed.method)
        assertEquals("a=1", parsed.bodyText)
    }

    @Test
    fun `parses cookie header and cookie option`() {
        val parsed = CurlCommandParser.parse(
            "curl https://a.com -H 'Cookie: sid=1; theme=dark' -b 'token=xyz'",
        )!!

        assertEquals(listOf("sid" to "1", "theme" to "dark", "token" to "xyz"), parsed.cookies)
        assertTrue(parsed.headers.none { it.first.equals("Cookie", ignoreCase = true) })
    }

    @Test
    fun `parses form data with file field`() {
        val parsed = CurlCommandParser.parse(
            "curl -X POST https://a.com/upload -F 'name=foo' -F 'file=@/tmp/a.png'",
        )!!

        assertEquals(2, parsed.formData.size)
        assertEquals(BodyFormDataTableModel.TYPE_TEXT, parsed.formData[0].type)
        assertEquals(BodyFormDataTableModel.TYPE_FILE, parsed.formData[1].type)
        assertEquals("/tmp/a.png", parsed.formData[1].value)
    }

    @Test
    fun `parses data binary file`() {
        val parsed = CurlCommandParser.parse("curl -X POST https://a.com/bin --data-binary '@/tmp/payload.bin'")!!

        assertEquals("/tmp/payload.bin", parsed.binaryFilePath)
    }

    @Test
    fun `parses basic auth option`() {
        val parsed = CurlCommandParser.parse("curl https://a.com -u user:pass")!!

        assertEquals(listOf("Authorization" to "Basic dXNlcjpwYXNz"), parsed.headers)
    }

    @Test
    fun `parses get with data option`() {
        val parsed = CurlCommandParser.parse("curl -G https://a.com/s -d 'q=kotlin' -d 'page=2'")!!

        assertEquals("GET", parsed.method)
        assertEquals(listOf("q" to "kotlin", "page" to "2"), parsed.query)
    }

    @Test
    fun `returns null for non curl text`() {
        assertNull(CurlCommandParser.parse("wget https://a.com"))
        assertNull(CurlCommandParser.parse(""))
    }
}
