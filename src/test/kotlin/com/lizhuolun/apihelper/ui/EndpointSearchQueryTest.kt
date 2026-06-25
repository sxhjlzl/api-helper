package com.lizhuolun.apihelper.ui

import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.core.HttpMethod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointSearchQueryTest {

    @Test
    fun `blank query matches endpoint`() {
        assertTrue(EndpointSearchQuery.parse("   ").matches(sampleItem()))
    }

    @Test
    fun `single keyword matches url method name class and http method`() {
        val item = sampleItem()

        assertTrue(EndpointSearchQuery.parse("/api/users").matches(item))
        assertTrue(EndpointSearchQuery.parse("GET").matches(item))
        assertTrue(EndpointSearchQuery.parse("findUser").matches(item))
        assertTrue(EndpointSearchQuery.parse("UserController").matches(item))
        assertTrue(EndpointSearchQuery.parse("user-service").matches(item))
    }

    @Test
    fun `multiple keywords must all match searchable fields`() {
        val item = sampleItem()

        assertTrue(EndpointSearchQuery.parse("GET user").matches(item))
        assertTrue(EndpointSearchQuery.parse("example /api").matches(item))
        assertFalse(EndpointSearchQuery.parse("POST user").matches(item))
        assertFalse(EndpointSearchQuery.parse("GET order").matches(item))
    }

    private fun sampleItem(): EndpointTreeItem =
        EndpointTreeItem(
            url = "/api/users/{id}",
            httpMethod = HttpMethod.GET,
            methodName = "findUserById",
            className = "example.user.UserController",
            moduleName = "user-service",
            kind = EndpointKind.CONTROLLER,
            pointer = null,
        )
}
