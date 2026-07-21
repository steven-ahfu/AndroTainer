package com.dokeraj.androtainer

import com.dokeraj.androtainer.models.ContainerStateType
import com.dokeraj.androtainer.models.HealthState
import com.dokeraj.androtainer.models.HostConfig
import com.dokeraj.androtainer.models.Kontainer
import com.dokeraj.androtainer.models.KontainerSortField
import com.dokeraj.androtainer.models.KontainerStateFilter
import com.dokeraj.androtainer.models.KontainerStats
import com.dokeraj.androtainer.models.MaintainerInfo
import com.dokeraj.androtainer.util.KontainerListOrganizer
import org.junit.Assert.assertEquals
import org.junit.Test

class KontainerListOrganizerTest {

    private fun kontainer(
        name: String,
        state: ContainerStateType = ContainerStateType.RUNNING,
        status: String = "Up 1 hour",
        created: Long = 1000L,
        health: HealthState = HealthState.NONE,
    ) = Kontainer(
        id = "id-$name",
        name = name,
        status = status,
        state = state,
        created = created,
        pulledImage = "img",
        maintainerInfo = MaintainerInfo(null, null),
        hostConfig = HostConfig("bridge"),
        mounts = emptyList(),
        ports = emptyList(),
        health = health
    )

    @Test
    fun `sort by name ascending is case-insensitive`() {
        val list = listOf(kontainer("beta"), kontainer("Alpha"), kontainer("gamma"))
        val sorted = KontainerListOrganizer.sort(list, KontainerSortField.NAME, true)
        assertEquals(listOf("Alpha", "beta", "gamma"), sorted.map { it.name })
    }

    @Test
    fun `sort by name descending`() {
        val list = listOf(kontainer("beta"), kontainer("Alpha"), kontainer("gamma"))
        val sorted = KontainerListOrganizer.sort(list, KontainerSortField.NAME, false)
        assertEquals(listOf("gamma", "beta", "Alpha"), sorted.map { it.name })
    }

    @Test
    fun `sort by uptime descending with nulls last`() {
        val list = listOf(
            kontainer("short", status = "Up 5 minutes"),
            kontainer("stopped", state = ContainerStateType.EXITED, status = "Exited (0) 2 hours ago"),
            kontainer("long", status = "Up 3 days"),
        )
        val sorted = KontainerListOrganizer.sort(list, KontainerSortField.UPTIME, false)
        assertEquals(listOf("long", "short", "stopped"), sorted.map { it.name })
    }

    @Test
    fun `sort by uptime ascending still keeps stopped containers last`() {
        val list = listOf(
            kontainer("stopped", state = ContainerStateType.EXITED, status = "Exited (0) 2 hours ago"),
            kontainer("long", status = "Up 3 days"),
            kontainer("short", status = "Up 5 minutes"),
        )
        val sorted = KontainerListOrganizer.sort(list, KontainerSortField.UPTIME, true)
        assertEquals(listOf("short", "long", "stopped"), sorted.map { it.name })
    }

    @Test
    fun `uptime ties broken by created descending`() {
        val list = listOf(
            kontainer("older", status = "Up 1 hour", created = 100L),
            kontainer("newer", status = "Up 1 hour", created = 200L),
        )
        val sorted = KontainerListOrganizer.sort(list, KontainerSortField.UPTIME, false)
        assertEquals(listOf("newer", "older"), sorted.map { it.name })
    }

    @Test
    fun `sort by created`() {
        val list = listOf(
            kontainer("mid", created = 500L),
            kontainer("new", created = 900L),
            kontainer("old", created = 100L),
        )
        assertEquals(listOf("old", "mid", "new"),
            KontainerListOrganizer.sort(list, KontainerSortField.CREATED, true).map { it.name })
        assertEquals(listOf("new", "mid", "old"),
            KontainerListOrganizer.sort(list, KontainerSortField.CREATED, false).map { it.name })
    }

    @Test
    fun `empty filter set shows all`() {
        val list = listOf(
            kontainer("a", state = ContainerStateType.RUNNING),
            kontainer("b", state = ContainerStateType.EXITED),
            kontainer("c", state = ContainerStateType.CREATED),
        )
        assertEquals(3, KontainerListOrganizer.filter(list, emptySet(), null).size)
    }

    @Test
    fun `running filter includes transitioning and restarting`() {
        val list = listOf(
            kontainer("run", state = ContainerStateType.RUNNING),
            kontainer("trans", state = ContainerStateType.TRANSITIONING),
            kontainer("restart", state = ContainerStateType.RESTARTING),
            kontainer("gone", state = ContainerStateType.EXITED),
            kontainer("paused", state = ContainerStateType.PAUSED),
        )
        val filtered = KontainerListOrganizer.filter(list, setOf(KontainerStateFilter.RUNNING), null)
        assertEquals(listOf("run", "trans", "restart"), filtered.map { it.name })
    }

    @Test
    fun `healthy filter matches health state`() {
        val list = listOf(
            kontainer("ok", health = HealthState.HEALTHY),
            kontainer("sick", health = HealthState.UNHEALTHY),
            kontainer("nohc", health = HealthState.NONE),
        )
        val filtered = KontainerListOrganizer.filter(list, setOf(KontainerStateFilter.HEALTHY), null)
        assertEquals(listOf("ok"), filtered.map { it.name })
    }

    @Test
    fun `exited filter includes errored`() {
        val list = listOf(
            kontainer("dead", state = ContainerStateType.EXITED),
            kontainer("broken", state = ContainerStateType.ERRORED),
            kontainer("up", state = ContainerStateType.RUNNING),
        )
        val filtered = KontainerListOrganizer.filter(list, setOf(KontainerStateFilter.EXITED), null)
        assertEquals(listOf("dead", "broken"), filtered.map { it.name })
    }

    @Test
    fun `multi-select combines with OR`() {
        val list = listOf(
            kontainer("run", state = ContainerStateType.RUNNING),
            kontainer("new", state = ContainerStateType.CREATED),
            kontainer("dead", state = ContainerStateType.EXITED),
        )
        val filtered = KontainerListOrganizer.filter(list,
            setOf(KontainerStateFilter.RUNNING, KontainerStateFilter.CREATED), null)
        assertEquals(listOf("run", "new"), filtered.map { it.name })
    }

    @Test
    fun `search term combines with filter using AND`() {
        val list = listOf(
            kontainer("alpha", state = ContainerStateType.RUNNING),
            kontainer("beta", state = ContainerStateType.RUNNING),
            kontainer("alphaDead", state = ContainerStateType.EXITED),
        )
        val filtered = KontainerListOrganizer.filter(list, setOf(KontainerStateFilter.RUNNING), "alpha")
        assertEquals(listOf("alpha"), filtered.map { it.name })
    }

    @Test
    fun `sort by CPU supports both directions and keeps missing samples last`() {
        val list = listOf(kontainer("missing"), kontainer("high"), kontainer("low"))
        val stats = mapOf(
            "id-high" to KontainerStats(cpuPct = 82.0, memUsedBytes = 200, memLimitBytes = 1000),
            "id-low" to KontainerStats(cpuPct = 4.0, memUsedBytes = 800, memLimitBytes = 1000),
        )

        val descending = KontainerListOrganizer.sort(
            list, KontainerSortField.CPU, false, stats)
        val ascending = KontainerListOrganizer.sort(
            list, KontainerSortField.CPU, true, stats)

        assertEquals(listOf("high", "low", "missing"), descending.map { it.name })
        assertEquals(listOf("low", "high", "missing"), ascending.map { it.name })
    }

    @Test
    fun `sort by memory uses bytes and keeps missing samples last`() {
        val list = listOf(kontainer("missing"), kontainer("cpuHigh"), kontainer("memoryHigh"))
        val stats = mapOf(
            "id-cpuHigh" to KontainerStats(cpuPct = 82.0, memUsedBytes = 200, memLimitBytes = 1000),
            "id-memoryHigh" to KontainerStats(cpuPct = 4.0, memUsedBytes = 800, memLimitBytes = 2000),
        )

        val descending = KontainerListOrganizer.sort(
            list, KontainerSortField.MEMORY, false, stats)
        val ascending = KontainerListOrganizer.sort(
            list, KontainerSortField.MEMORY, true, stats)

        assertEquals(listOf("memoryHigh", "cpuHigh", "missing"), descending.map { it.name })
        assertEquals(listOf("cpuHigh", "memoryHigh", "missing"), ascending.map { it.name })
    }
}
