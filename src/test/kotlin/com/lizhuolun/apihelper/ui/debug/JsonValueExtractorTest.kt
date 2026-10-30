package com.lizhuolun.apihelper.ui.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonValueExtractorTest {

    private val json = """
        {
          "code": 0,
          "data": {
            "token": "abc123",
            "list": [ {"id": 1}, {"id": 2} ],
            "nested": { "deep": true }
          }
        }
    """.trimIndent()

    @Test
    fun `extracts nested string field`() {
        assertEquals("abc123", JsonValueExtractor.extract(json, "data.token"))
    }

    @Test
    fun `extracts array element field`() {
        assertEquals("2", JsonValueExtractor.extract(json, "data.list[1].id"))
    }

    @Test
    fun `extracts top level primitive`() {
        assertEquals("0", JsonValueExtractor.extract(json, "code"))
    }

    @Test
    fun `extracts object as compact json`() {
        assertEquals("{\"deep\":true}", JsonValueExtractor.extract(json, "data.nested"))
    }

    @Test
    fun `returns null for missing path`() {
        assertNull(JsonValueExtractor.extract(json, "data.missing"))
        assertNull(JsonValueExtractor.extract(json, "data.list[9].id"))
        assertNull(JsonValueExtractor.extract(json, "code.name"))
    }

    @Test
    fun `returns null for invalid json or blank path`() {
        assertNull(JsonValueExtractor.extract("not json", "data.token"))
        assertNull(JsonValueExtractor.extract(json, ""))
    }

    @Test
    fun `extracts from root array`() {
        assertEquals("7", JsonValueExtractor.extract("[{\"id\": 7}]", "[0].id"))
    }
}
