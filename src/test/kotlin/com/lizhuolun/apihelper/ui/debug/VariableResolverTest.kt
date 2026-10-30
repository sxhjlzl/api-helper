package com.lizhuolun.apihelper.ui.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VariableResolverTest {

    @Test
    fun `replaces single placeholder`() {
        val result = VariableResolver.resolve(
            "Authorization: Bearer {{token}}",
            mapOf("token" to "abc123"),
        )

        assertEquals("Authorization: Bearer abc123", result.text)
        assertTrue(result.fullyResolved)
    }

    @Test
    fun `replaces multiple placeholders and ignores spaces`() {
        val result = VariableResolver.resolve(
            "{{host}}/api/{{ version }}/users",
            mapOf("host" to "http://dev.example.com", "version" to "v2"),
        )

        assertEquals("http://dev.example.com/api/v2/users", result.text)
        assertTrue(result.fullyResolved)
    }

    @Test
    fun `keeps unresolved placeholder and reports name`() {
        val result = VariableResolver.resolve(
            "{{host}}/api/{{missing}}",
            mapOf("host" to "http://localhost:8080"),
        )

        assertEquals("http://localhost:8080/api/{{missing}}", result.text)
        assertFalse(result.fullyResolved)
        assertEquals(listOf("missing"), result.unresolved)
    }

    @Test
    fun `does not touch single brace path variables`() {
        val result = VariableResolver.resolve(
            "/api/users/{id}?token={{token}}",
            mapOf("token" to "t1"),
        )

        assertEquals("/api/users/{id}?token=t1", result.text)
    }

    @Test
    fun `expands nested variable references`() {
        val result = VariableResolver.resolve(
            "{{auth}}",
            mapOf("rawToken" to "abc", "auth" to "Bearer {{rawToken}}"),
        )

        assertEquals("Bearer abc", result.text)
        assertTrue(result.fullyResolved)
    }

    @Test
    fun `keeps text on circular reference`() {
        val result = VariableResolver.resolve(
            "{{a}}",
            mapOf("a" to "{{b}}", "b" to "{{a}}"),
        )

        assertFalse(result.fullyResolved)
    }

    @Test
    fun `resolves pair values only`() {
        val (pairs, unresolved) = VariableResolver.resolvePairs(
            listOf("X-Token" to "{{token}}", "X-Env" to "{{env}}"),
            mapOf("token" to "t1"),
        )

        assertEquals(listOf("X-Token" to "t1", "X-Env" to "{{env}}"), pairs)
        assertEquals(listOf("env"), unresolved)
    }

    @Test
    fun `extracts variable names in order`() {
        assertEquals(
            listOf("host", "token"),
            VariableResolver.extractVariableNames("{{host}}/x?a={{token}}&b={{host}}"),
        )
    }

    @Test
    fun `returns original text when no placeholder present`() {
        val result = VariableResolver.resolve("plain text", mapOf("a" to "1"))

        assertEquals("plain text", result.text)
        assertTrue(result.fullyResolved)
    }
}
