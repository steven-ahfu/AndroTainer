package com.dokeraj.androtainer.interfaces

import com.dokeraj.androtainer.models.retrofit.PEndpointsResponse
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.Url

interface ApiInterfaceApiKey {
    @Headers("Content-Type: application/octet-stream")
    @GET
    fun getLog(
        @Url fullPath: String,
        @Header("X-API-Key") auth: String,
        @Query("since") since: Int,
        @Query("stderr") stderr: Int,
        @Query("stdout") stdout: Int,
        @Query("tail") tail: Int,
        @Query("timestamps") timestamps: Int,
    ): Call<ResponseBody>


    @Headers("Content-Type: application/json")
    @GET
    fun getEnpointId(
        @Url fullPath: String,
        @Header("X-API-Key") auth: String,
        @Query("limit") limit: Int,
        @Query("start") start: Int,
    ): Call<PEndpointsResponse>

}
