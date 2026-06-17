package com.lizhuolun.apihelper.core.annotation

import org.junit.Assert.assertEquals
import org.junit.Test

class PathBuilderTest {

    @Test
    fun `join ignores empty and root-only segments`() {
        assertEquals("/users/{id}", PathBuilder.join("/", "", "/users/", "{id}"))
    }

    @Test
    fun `join returns root when all segments are empty`() {
        assertEquals("/", PathBuilder.join(null, "", "/"))
    }

    @Test
    fun `build controller URL combines all prefixes`() {
        assertEquals(
            "/gateway/api/users",
            PathBuilder.buildControllerUrl("/gateway/", "/api", "/", "/users"),
        )
    }
}
