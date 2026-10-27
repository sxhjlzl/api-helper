package com.lizhuolun.apihelper.core.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 路径模板解析的纯工具类测试，不依赖 IDE 测试夹具。
 */
class PathTemplateTest {

    @Test
    fun testParsesSingleVariable() {
        val template = PathTemplate.parse("/api/user/{orderId}/detail")

        assertEquals(listOf("api", "user", "{}", "detail"), template.segments)
        assertEquals(listOf("orderId"), template.variableNames)
        assertTrue(template.hasVariables)
    }

    @Test
    fun testParsesMultipleVariablesInOrder() {
        val template = PathTemplate.parse("/user/{userId}/order/{orderId}")

        assertEquals(listOf("userId", "orderId"), template.variableNames)
        assertEquals(listOf("user", "{}", "order", "{}"), template.segments)
    }

    @Test
    fun testNormalizedPlaceholderYieldsNoVariableName() {
        val template = PathTemplate.parse("/user/{}")

        assertFalse(template.hasVariables)
        assertEquals(listOf("user", "{}"), template.segments)
    }

    @Test
    fun testRootAndEmptyUrlProduceEmptyTemplate() {
        assertFalse(PathTemplate.parse("/").hasVariables)
        assertTrue(PathTemplate.parse("/").segments.isEmpty())
        assertTrue(PathTemplate.parse("").segments.isEmpty())
        assertTrue(PathTemplate.parse("   ").segments.isEmpty())
    }

    @Test
    fun testMissingLeadingSlashIsTolerated() {
        val template = PathTemplate.parse("user/{id}")

        assertEquals(listOf("user", "{}"), template.segments)
        assertEquals(listOf("id"), template.variableNames)
    }

    @Test
    fun testMixedStaticAndVariableSegments() {
        val template = PathTemplate.parse("/api/{version}/users/{id}/orders")

        assertEquals(listOf("api", "{}", "users", "{}", "orders"), template.segments)
        assertEquals(listOf("version", "id"), template.variableNames)
    }
}