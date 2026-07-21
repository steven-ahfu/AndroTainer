package com.dokeraj.androtainer

import com.dokeraj.androtainer.models.KontainerFilterPref
import com.dokeraj.androtainer.models.KontainerSortField
import com.dokeraj.androtainer.models.KontainerStateFilter
import com.dokeraj.androtainer.models.retrofit.AppSettings
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Documents the backward-compat contract: legacy persisted AppSettings JSON
 * (2 fields) must deserialize with the new fields null — they are normalized
 * to defaults in MainActiviy.initializeGlobalVar(). */
class AppSettingsCompatTest {
    @Test
    fun `legacy two-field json leaves new fields null`() {
        val legacyJson = """{"kontainerFilter":"RUNNING","searchTermVisibility":true}"""
        val settings = Gson().fromJson(legacyJson, AppSettings::class.java)

        assertEquals(KontainerFilterPref.RUNNING, settings.kontainerFilter)
        assertEquals(true, settings.searchTermVisibility)
        assertNull(settings.sortField)
        assertNull(settings.sortAscending)
        assertNull(settings.stateFilters)
    }

    @Test
    fun `new fields round-trip through gson`() {
        val settings = AppSettings(
            kontainerFilter = KontainerFilterPref.TOTAL,
            searchTermVisibility = false,
            sortField = KontainerSortField.UPTIME,
            sortAscending = false,
            stateFilters = listOf(KontainerStateFilter.RUNNING, KontainerStateFilter.HEALTHY)
        )
        val json = Gson().toJson(settings)
        val back = Gson().fromJson(json, AppSettings::class.java)
        assertEquals(settings, back)
    }
}
