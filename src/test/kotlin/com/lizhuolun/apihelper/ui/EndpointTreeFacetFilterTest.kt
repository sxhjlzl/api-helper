package com.lizhuolun.apihelper.ui

import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.core.HttpMethod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointTreeFacetFilterTest {

    @Test
    fun `module type and framework filters can be combined`() {
        val item = item(moduleName = "promotion-api", kind = EndpointKind.FEIGN)

        assertTrue(matches(item, setOf("promotion-api"), emptySet(), emptySet()))
        assertFalse(matches(item, setOf("promotion-server"), emptySet(), emptySet()))
        assertTrue(matches(item, emptySet(), setOf(EndpointTypeFilter.CLIENT), emptySet()))
        assertFalse(matches(item, emptySet(), setOf(EndpointTypeFilter.SERVER), emptySet()))
        assertTrue(matches(item, emptySet(), emptySet(), setOf(EndpointKind.FEIGN)))
        assertFalse(matches(item, emptySet(), emptySet(), setOf(EndpointKind.HTTP_EXCHANGE)))
        assertTrue(matches(
            item,
            setOf("promotion-api", "promotion-server"),
            setOf(EndpointTypeFilter.CLIENT, EndpointTypeFilter.SERVER),
            setOf(EndpointKind.FEIGN, EndpointKind.CONTROLLER),
        ))
        assertFalse(matches(
            item,
            setOf("promotion-server"),
            setOf(EndpointTypeFilter.CLIENT),
            setOf(EndpointKind.FEIGN),
        ))
    }

    @Test
    fun `blank module uses default module filter`() {
        val item = item(moduleName = "", kind = EndpointKind.CONTROLLER)

        assertTrue(matches(
            item,
            setOf("default"),
            setOf(EndpointTypeFilter.SERVER),
            setOf(EndpointKind.CONTROLLER),
        ))
    }

    private fun matches(
        item: EndpointTreeItem,
        moduleNames: Set<String>,
        types: Set<EndpointTypeFilter>,
        frameworks: Set<EndpointKind>,
    ): Boolean = EndpointTree.matchesFacetFilters(item, moduleNames, types, frameworks)

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
