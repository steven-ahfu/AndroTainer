package com.dokeraj.androtainer

import com.dokeraj.androtainer.util.UptimeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UptimeParserTest {
    @Test
    fun `parses plain units`() {
        assertEquals(45L, UptimeParser.uptimeSeconds("Up 45 seconds"))
        assertEquals(3 * 60L, UptimeParser.uptimeSeconds("Up 3 minutes"))
        assertEquals(3 * 3600L, UptimeParser.uptimeSeconds("Up 3 hours"))
        assertEquals(2 * 86400L, UptimeParser.uptimeSeconds("Up 2 days"))
        assertEquals(1 * 604800L, UptimeParser.uptimeSeconds("Up 1 week"))
        assertEquals(5 * 2592000L, UptimeParser.uptimeSeconds("Up 5 months"))
        assertEquals(2 * 31536000L, UptimeParser.uptimeSeconds("Up 2 years"))
    }

    @Test
    fun `parses singular unit`() {
        assertEquals(3600L, UptimeParser.uptimeSeconds("Up 1 hour"))
    }

    @Test
    fun `parses about phrasings`() {
        assertEquals(60L, UptimeParser.uptimeSeconds("Up About a minute"))
        assertEquals(3600L, UptimeParser.uptimeSeconds("Up About an hour"))
    }

    @Test
    fun `parses less than a second`() {
        assertEquals(0L, UptimeParser.uptimeSeconds("Up Less than a second"))
    }

    @Test
    fun `strips health suffix`() {
        assertEquals(3 * 3600L, UptimeParser.uptimeSeconds("Up 3 hours (healthy)"))
        assertEquals(120L, UptimeParser.uptimeSeconds("Up 2 minutes (unhealthy)"))
        assertEquals(30L, UptimeParser.uptimeSeconds("Up 30 seconds (health: starting)"))
    }

    @Test
    fun `non-up statuses return null`() {
        assertNull(UptimeParser.uptimeSeconds("Exited (0) 2 hours ago"))
        assertNull(UptimeParser.uptimeSeconds("Exited (137) 3 days ago"))
        assertNull(UptimeParser.uptimeSeconds("Created"))
        assertNull(UptimeParser.uptimeSeconds("Restarting (1) 5 seconds ago"))
        assertNull(UptimeParser.uptimeSeconds(""))
    }

    @Test
    fun `unknown up phrasing treated as just started`() {
        assertEquals(0L, UptimeParser.uptimeSeconds("Up"))
    }
}
