package com.lizhuolun.apihelper.config

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceholderResolverTest {

    @Test
    fun `resolves values defaults and nested placeholders`() {
        val properties = mapOf(
            "root" to "/api",
            "users" to "\${root}/users",
        )

        assertEquals("/api/users/v1", PlaceholderResolver.resolve("\${users}/\${version:v1}", properties))
    }

    @Test
    fun `keeps unresolved placeholder`() {
        assertEquals("\${missing}", PlaceholderResolver.resolve("\${missing}", emptyMap()))
    }
}
