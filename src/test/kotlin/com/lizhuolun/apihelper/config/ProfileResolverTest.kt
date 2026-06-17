package com.lizhuolun.apihelper.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileResolverTest {

    @Test
    fun `manual profile overrides configured profiles`() {
        val properties = mapOf("spring.profiles.active" to "prod")

        assertEquals(
            listOf("dev", "test"),
            ProfileResolver.resolveActiveProfiles(properties, "dev, test;dev"),
        )
    }

    @Test
    fun `default profile is used when active profile is absent`() {
        val properties = mapOf("spring.profiles.default" to "local")

        assertEquals(listOf("local"), ProfileResolver.resolveActiveProfiles(properties, null))
    }
}
