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

    @Test
    fun `normalizeForMatch unifies path variable names`() {
        assertEquals("/user/{}", PathBuilder.normalizeForMatch("/user/{id}"))
        assertEquals("/user/{}", PathBuilder.normalizeForMatch("/user/{userId}"))
        assertEquals(
            "/order/{}/items/{}",
            PathBuilder.normalizeForMatch("/order/{orderId}/items/{itemId}"),
        )
    }

    @Test
    fun `normalizeForMatch keeps plain path unchanged`() {
        assertEquals("/api/hello", PathBuilder.normalizeForMatch("/api/hello"))
    }

    @Test
    fun `client prefix is stripped only for matching`() {
        assertEquals(
            "/system/oauth2/token/create",
            PathBuilder.normalizeClientForMatch(
                "/rpc-api/system/oauth2/token/create",
                listOf("/rpc-api"),
            ),
        )
    }

    @Test
    fun `client prefix matching uses longest prefix and path segment boundary`() {
        assertEquals(
            "/oauth2/token/create",
            PathBuilder.normalizeClientForMatch(
                "/rpc-api/system/oauth2/token/create",
                listOf("/rpc-api", "/rpc-api/system"),
            ),
        )
        assertEquals(
            "/rpc-api-other/system/oauth2/token/create",
            PathBuilder.normalizeClientForMatch(
                "/rpc-api-other/system/oauth2/token/create",
                listOf("/rpc-api"),
            ),
        )
        assertEquals(
            "/system/oauth2/token/create",
            PathBuilder.normalizeClientForMatch("/system/oauth2/token/create", emptyList()),
        )
    }
}
