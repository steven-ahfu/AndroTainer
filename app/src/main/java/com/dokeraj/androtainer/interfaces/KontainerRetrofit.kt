package com.dokeraj.androtainer.interfaces

import com.dokeraj.androtainer.models.retrofit.PContainerInspectResponse
import com.dokeraj.androtainer.models.retrofit.PContainerStatsResponse
import com.dokeraj.androtainer.models.retrofit.PContainersResponse
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface KontainerRetrofit {
    @GET
    suspend fun listDockerContainers(
        @Header("Authorization") auth: String?,
        @Url fullPath: String,
        @Query("all") paramAll: Int,
    ): PContainersResponse

    @GET
    suspend fun listDockerContainersApiKey(
        @Header("X-API-Key") auth: String?,
        @Url fullPath: String,
        @Query("all") paramAll: Int,
    ): PContainersResponse

    @POST
    suspend fun startStopContainer(
        @Header("Authorization") auth: String?,
        @Url fullPath: String,
    ): Response<Unit>

    @POST
    suspend fun startStopContainerApiKey(
        @Header("X-API-Key") auth: String?,
        @Url fullPath: String,
    ): Response<Unit>

    @POST
    suspend fun restartContainer(
        @Header("Authorization") auth: String?,
        @Url fullPath: String,
    ): Response<Unit>

    @POST
    suspend fun restartContainerApiKey(
        @Header("X-API-Key") auth: String?,
        @Url fullPath: String,
    ): Response<Unit>

    @DELETE
    suspend fun deleteDockerContainer(
        @Header("Authorization") auth: String?,
        @Url fullPath: String,
        @Query("force") force: Boolean,
        @Query("v") deleteVolumes: Int,
    ): Response<Unit>

    @DELETE
    suspend fun deleteDockerContainerApiKey(
        @Header("X-API-Key") auth: String?,
        @Url fullPath: String,
        @Query("force") force: Boolean,
        @Query("v") deleteVolumes: Int,
    ): Response<Unit>

    /** one-sample live stats: always call with stream=false (precpu populated by daemon) */
    @GET
    suspend fun getContainerStats(
        @Header("Authorization") auth: String?,
        @Url fullPath: String,
        @Query("stream") stream: Boolean,
    ): PContainerStatsResponse

    @GET
    suspend fun getContainerStatsApiKey(
        @Header("X-API-Key") auth: String?,
        @Url fullPath: String,
        @Query("stream") stream: Boolean,
    ): PContainerStatsResponse

    @GET
    suspend fun inspectContainer(
        @Header("Authorization") auth: String?,
        @Url fullPath: String,
    ): PContainerInspectResponse

    @GET
    suspend fun inspectContainerApiKey(
        @Header("X-API-Key") auth: String?,
        @Url fullPath: String,
    ): PContainerInspectResponse

    @POST
    suspend fun pauseUnpauseContainer(
        @Header("Authorization") auth: String?,
        @Url fullPath: String,
    ): Response<Unit>

    @POST
    suspend fun pauseUnpauseContainerApiKey(
        @Header("X-API-Key") auth: String?,
        @Url fullPath: String,
    ): Response<Unit>

}