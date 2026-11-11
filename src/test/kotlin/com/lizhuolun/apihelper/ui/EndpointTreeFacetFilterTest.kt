package com.lizhuolun.apihelper.ui

import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.core.HttpMethod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointTreeFacetFilterTest {

    @Test
    fun `module and framework filters can be combined`() {
        val item = item(moduleName = "promotion-api", kind = EndpointKind.FEIGN)

        assertTrue(matches(item, setOf("promotion-api"), emptySet()))
        assertFalse(matches(item, setOf("promotion-server"), emptySet()))
        assertTrue(matches(item, emptySet(), setOf(EndpointKind.FEIGN)))
        assertFalse(matches(item, emptySet(), setOf(EndpointKind.HTTP_EXCHANGE)))
        assertTrue(matches(
            item,
            setOf("promotion-api", "promotion-server"),
            setOf(EndpointKind.FEIGN, EndpointKind.CONTROLLER),
        ))
        assertFalse(matches(
            item,
            setOf("promotion-server"),
            setOf(EndpointKind.FEIGN),
        ))
    }

    @Test
    fun `blank module uses default module filter`() {
        val item = item(moduleName = "", kind = EndpointKind.CONTROLLER)

        assertTrue(matches(item, setOf("default"), setOf(EndpointKind.CONTROLLER)))
    }

    private fun matches(
        item: EndpointTreeItem,
        moduleNames: Set<String>,
        frameworks: Set<EndpointKind>,
    ): Boolean = EndpointTree.matchesFacetFilters(item, moduleNames, frameworks)

    private fun item(moduleName: String, kind: EndpointKind): EndpointTreeItem =
        EndpointTreeItem(
            url = "/coupon",
            httpMethod = HttpMethod.GET,
            methodName = "getCoupon",
            className = "example.CouponApi",
            moduleName = moduleName,
            kind = kind,
            pointer = null,
        )
}
