package com.dokeraj.androtainer.repositories

import com.dokeraj.androtainer.interfaces.KontainerRetrofit
import com.dokeraj.androtainer.models.ContainerStateType
import com.dokeraj.androtainer.models.Kontainer
import com.dokeraj.androtainer.models.KontainerInspectInfo
import com.dokeraj.androtainer.models.KontainerStats
import com.dokeraj.androtainer.models.retrofit.NetworkMapper
import com.dokeraj.androtainer.models.retrofit.PContainersResponse
import com.dokeraj.androtainer.models.retrofit.StatsNetworkMapper
import com.dokeraj.androtainer.util.DataState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class DockerListerRepo constructor(
    private val kontainerRetrofit: KontainerRetrofit,
    private val networkMapper: NetworkMapper,
    private val statsNetworkMapper: StatsNetworkMapper,
) {
    suspend fun getDocContainers(
        jwt: String?,
        url: String,
        isUsingApiKey: Boolean,
    ): Flow<DataState<List<Kontainer>>> = flow {
        emit(DataState.Loading)
        try {
            val networkKontainers: PContainersResponse = if (!isUsingApiKey)
                kontainerRetrofit.listDockerContainers(jwt, url, 1)
            else
                kontainerRetrofit.listDockerContainersApiKey(jwt, url, 1)

            val kontainers: List<Kontainer> =
                networkMapper.mapFromEntityList(networkKontainers).sortedBy { it.name }
            emit(DataState.Success(kontainers))
        } catch (e: Exception) {
            emit(DataState.Error(e))
        }
    }

    suspend fun startStopDokerContainer(
        jwt: String?,
        url: String,
        isUsingApiKey: Boolean,
        currentItemIndex: Int,
    ): Flow<DataState<List<Kontainer>>> = flow {
        emit(DataState.CardLoading(listOf<Kontainer>(), currentItemIndex))
        try {
            val so = if (!isUsingApiKey) kontainerRetrofit.startStopContainer(jwt,
                url) else kontainerRetrofit.startStopContainerApiKey(jwt, url)
            if (so.code() != 204) {
                emit(DataState.CardError(listOf<Kontainer>(), currentItemIndex))
            } else
                emit(DataState.CardSuccess(listOf<Kontainer>(), currentItemIndex))
        } catch (e: Exception) {
            emit(DataState.CardError(listOf<Kontainer>(), currentItemIndex))
        }
    }

    suspend fun restartContainer(
        jwt: String?,
        url: String,
        isUsingApiKey: Boolean,
        currentItemIndex: Int,
    ): Flow<DataState<List<Kontainer>>> = flow {
        emit(DataState.CardLoading(listOf<Kontainer>(), currentItemIndex))
        try {
            val so = if (!isUsingApiKey) kontainerRetrofit.restartContainer(jwt,
                url) else kontainerRetrofit.restartContainerApiKey(jwt, url)
            if (so.code() != 204) {
                emit(DataState.CardError(listOf<Kontainer>(), currentItemIndex))
            } else
                emit(DataState.CardSuccess(listOf<Kontainer>(), currentItemIndex))
        } catch (e: Exception) {
            emit(DataState.CardError(listOf<Kontainer>(), currentItemIndex))
        }
    }

    suspend fun deleteContainer(
        jwt: String?,
        url: String,
        isUsingApiKey: Boolean,
        selectedItem: Kontainer,
    ): Flow<DataState<List<Kontainer>>> = flow {
        emit(DataState.DeleteLoading(listOf<Kontainer>(), selectedItem))
        try {
            val res = if (!isUsingApiKey) kontainerRetrofit.deleteDockerContainer(jwt,
                url,
                true,
                1) else kontainerRetrofit.deleteDockerContainerApiKey(jwt, url, true, 1)
            if (res.code() != 204) {
                emit(DataState.Error(java.lang.Exception("cannot delete container!")))
            } else
                emit(DataState.DeleteSuccess(listOf<Kontainer>(), selectedItem))
        } catch (e: Exception) {
            emit(DataState.Error(java.lang.Exception("cannot delete container!")))
        }
    }

    /** fetches one stats sample per RUNNING container, at most [MAX_CONCURRENT_STATS]
     * in flight (each call costs the daemon ~1-2s); emits per container as results
     * arrive — failures simply never emit, so UI placeholders stay */
    suspend fun getStatsBatch(
        jwt: String?,
        urlTemplate: String,
        isUsingApiKey: Boolean,
        containers: List<Kontainer>,
    ): Flow<Pair<String, KontainerStats>> = channelFlow {
        val semaphore = Semaphore(MAX_CONCURRENT_STATS)
        containers.filter { it.state == ContainerStateType.RUNNING }.forEach { container ->
            launch {
                semaphore.withPermit {
                    try {
                        val url = urlTemplate.replace("{containerId}", container.id)
                        val dto = if (!isUsingApiKey)
                            kontainerRetrofit.getContainerStats(jwt, url, false)
                        else
                            kontainerRetrofit.getContainerStatsApiKey(jwt, url, false)
                        send(container.id to statsNetworkMapper.mapStats(dto))
                    } catch (e: Exception) {
                        // no emission — the card keeps its placeholder
                    }
                }
            }
        }
    }

    /** stats (running containers only) + inspect for the details page, fetched in parallel */
    suspend fun getContainerDetailExtras(
        jwt: String?,
        statsUrl: String?,
        inspectUrl: String,
        isUsingApiKey: Boolean,
    ): Flow<DataState<Pair<KontainerStats?, KontainerInspectInfo?>>> = flow {
        emit(DataState.Loading)
        try {
            val result = coroutineScope {
                val statsDeferred = statsUrl?.let {
                    async {
                        try {
                            val dto = if (!isUsingApiKey)
                                kontainerRetrofit.getContainerStats(jwt, it, false)
                            else
                                kontainerRetrofit.getContainerStatsApiKey(jwt, it, false)
                            statsNetworkMapper.mapStats(dto)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                val inspectDeferred = async {
                    val dto = if (!isUsingApiKey)
                        kontainerRetrofit.inspectContainer(jwt, inspectUrl)
                    else
                        kontainerRetrofit.inspectContainerApiKey(jwt, inspectUrl)
                    statsNetworkMapper.mapInspect(dto)
                }
                Pair(statsDeferred?.await(), inspectDeferred.await())
            }
            emit(DataState.Success(result))
        } catch (e: Exception) {
            emit(DataState.Error(e))
        }
    }

    suspend fun pauseUnpauseContainer(
        jwt: String?,
        url: String,
        isUsingApiKey: Boolean,
        currentItemIndex: Int,
    ): Flow<DataState<List<Kontainer>>> = flow {
        emit(DataState.CardLoading(listOf<Kontainer>(), currentItemIndex))
        try {
            val so = if (!isUsingApiKey) kontainerRetrofit.pauseUnpauseContainer(jwt,
                url) else kontainerRetrofit.pauseUnpauseContainerApiKey(jwt, url)
            if (so.code() != 204) {
                emit(DataState.CardError(listOf<Kontainer>(), currentItemIndex))
            } else
                emit(DataState.CardSuccess(listOf<Kontainer>(), currentItemIndex))
        } catch (e: Exception) {
            emit(DataState.CardError(listOf<Kontainer>(), currentItemIndex))
        }
    }

    companion object {
        private const val MAX_CONCURRENT_STATS = 6
    }
}