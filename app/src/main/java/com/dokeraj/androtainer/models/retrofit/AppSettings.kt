package com.dokeraj.androtainer.models.retrofit

import com.dokeraj.androtainer.models.KontainerFilterPref
import com.dokeraj.androtainer.models.KontainerSortField
import com.dokeraj.androtainer.models.KontainerStateFilter

/** New fields are nullable for Gson backward-compat with persisted legacy JSON;
 * nulls are normalized to defaults in MainActiviy.initializeGlobalVar(). */
data class AppSettings(
    val kontainerFilter: KontainerFilterPref,
    val searchTermVisibility: Boolean,
    val sortField: KontainerSortField? = null,
    val sortAscending: Boolean? = null,
    val stateFilters: List<KontainerStateFilter>? = null,
    val monitorIntervalMinutes: Int? = null,
)
