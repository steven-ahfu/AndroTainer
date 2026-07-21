package com.dokeraj.androtainer.util

import com.dokeraj.androtainer.models.ContainerStateType
import com.dokeraj.androtainer.models.HealthState
import com.dokeraj.androtainer.models.Kontainer
import com.dokeraj.androtainer.models.KontainerSortField
import com.dokeraj.androtainer.models.KontainerStateFilter
import com.dokeraj.androtainer.models.KontainerStats
import java.util.Locale

/** Pure sorting + multi-select filtering for the container list (Stage 1). */
object KontainerListOrganizer {

    fun sort(
        list: List<Kontainer>,
        field: KontainerSortField,
        ascending: Boolean,
        statsById: Map<String, KontainerStats> = emptyMap(),
    ): List<Kontainer> {
        val sorted = when (field) {
            KontainerSortField.NAME ->
                list.sortedBy { it.name.lowercase(Locale.getDefault()) }
            KontainerSortField.UPTIME ->
                // nulls (not running) always sort after running containers, regardless of direction
                list.sortedWith(
                    compareBy<Kontainer> { UptimeParser.uptimeSeconds(it.status) == null }
                        .thenComparator { a, b ->
                            val ua = UptimeParser.uptimeSeconds(a.status) ?: -1L
                            val ub = UptimeParser.uptimeSeconds(b.status) ?: -1L
                            val cmp = ua.compareTo(ub)
                            if (ascending) cmp else -cmp
                        }
                        .thenByDescending { it.created }
                )
            KontainerSortField.CREATED ->
                list.sortedBy { it.created }
            KontainerSortField.CPU ->
                sortByLiveStat(list, ascending, statsById) { it.cpuPct }
            KontainerSortField.MEMORY ->
                sortByLiveStat(list, ascending, statsById) { it.memUsedBytes.toDouble() }
        }

        return when (field) {
            KontainerSortField.UPTIME,
            KontainerSortField.CPU,
            KontainerSortField.MEMORY -> sorted // direction already applied inside the comparator
            else -> if (ascending) sorted else sorted.reversed()
        }
    }

    /** Containers without a live sample always remain last in either direction. */
    private fun sortByLiveStat(
        list: List<Kontainer>,
        ascending: Boolean,
        statsById: Map<String, KontainerStats>,
        selector: (KontainerStats) -> Double,
    ): List<Kontainer> = list.sortedWith(
        compareBy<Kontainer> { statsById[it.id] == null }
            .thenComparator { a, b ->
                val aValue = statsById[a.id]?.let(selector) ?: 0.0
                val bValue = statsById[b.id]?.let(selector) ?: 0.0
                val comparison = aValue.compareTo(bValue)
                if (ascending) comparison else -comparison
            }
            .thenBy { it.name.lowercase(Locale.getDefault()) }
    )

    /** empty [filters] = show all; multiple selections combine with OR */
    fun filter(
        list: List<Kontainer>,
        filters: Set<KontainerStateFilter>,
        searchTerm: String?,
    ): List<Kontainer> {
        return list.filter { container ->
            val matchesFilter = filters.isEmpty() || filters.any { f ->
                when (f) {
                    KontainerStateFilter.RUNNING -> container.state in setOf(
                        ContainerStateType.RUNNING,
                        ContainerStateType.TRANSITIONING,
                        ContainerStateType.RESTARTING)
                    KontainerStateFilter.HEALTHY -> container.health == HealthState.HEALTHY
                    KontainerStateFilter.EXITED -> container.state in setOf(
                        ContainerStateType.EXITED,
                        ContainerStateType.ERRORED)
                    KontainerStateFilter.CREATED -> container.state == ContainerStateType.CREATED
                }
            }

            val matchesSearch = searchTerm?.let {
                container.name.lowercase(Locale.getDefault()).startsWith(it)
            } ?: true

            matchesFilter && matchesSearch
        }
    }
}
