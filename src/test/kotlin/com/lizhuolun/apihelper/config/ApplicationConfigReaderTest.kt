package com.lizhuolun.apihelper.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationConfigReaderTest {

    @Test
    fun `recognizes supported Spring configuration files`() {
        assertTrue(ApplicationConfigReader.isSpringConfigFile("application.yml"))
        assertTrue(ApplicationConfigReader.isSpringConfigFile("application-prod.yaml"))
        assertTrue(ApplicationConfigReader.isSpringConfigFile("bootstrap.properties"))
        assertFalse(ApplicationConfigReader.isSpringConfigFile("application.json"))
        assertFalse(ApplicationConfigReader.isSpringConfigFile("other.yml"))
    }

    @Test
    fun `reads server port from properties`() {
        val properties = mapOf<String, Any?>("server.port" to 9090)

        assertEquals(9090, ApplicationConfigReader.readServerPort(properties))
    }

    @Test
    fun `reads server port from placeholder default`() {
        val properties = mapOf<String, Any?>("server.port" to "\${APP_PORT:9091}")

        assertEquals(9091, ApplicationConfigReader.readServerPort(properties))
    }

    @Test
    fun `returns null for invalid server port`() {
        val properties = mapOf<String, Any?>("server.port" to "random")

        assertNull(ApplicationConfigReader.readServerPort(properties))
    }
}
