package com.dokeraj.androtainer

import com.dokeraj.androtainer.interfaces.ApiInterface
import com.dokeraj.androtainer.interfaces.ApiInterfaceApiKey
import com.dokeraj.androtainer.models.retrofit.UserCredentials
import com.dokeraj.androtainer.network.RetrofitInstance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PortainerRetrofitRequestTest {
    private val baseUrl = "http://portainer.test"
    private val jwtApi = RetrofitInstance.retrofitInstance!!.create(ApiInterface::class.java)
    private val apiKeyApi =
        RetrofitInstance.retrofitInstance!!.create(ApiInterfaceApiKey::class.java)

    @Test
    fun `password login creates annotated auth request`() {
        val request = jwtApi.loginRequest(
            UserCredentials(username = "user", password = "password"),
            "$baseUrl/api/auth"
        ).request()

        assertEquals("POST", request.method)
        assertEquals("$baseUrl/api/auth", request.url.toString())
        assertNotNull(request.body)
    }

    @Test
    fun `JWT endpoint request uses bearer authorization`() {
        val request = jwtApi.getEnpointId(
            "$baseUrl/api/endpoints",
            "Bearer jwt-token",
            10,
            0
        ).request()

        assertEquals("Bearer jwt-token", request.header("Authorization"))
        assertNull(request.header("X-API-Key"))
        assertEquals("10", request.url.queryParameter("limit"))
        assertEquals("0", request.url.queryParameter("start"))
    }

    @Test
    fun `API key endpoint request uses API key header`() {
        val request = apiKeyApi.getEnpointId(
            "$baseUrl/api/endpoints",
            "api-key",
            10,
            0
        ).request()

        assertEquals("api-key", request.header("X-API-Key"))
        assertNull(request.header("Authorization"))
        assertEquals("10", request.url.queryParameter("limit"))
        assertEquals("0", request.url.queryParameter("start"))
    }

    @Test
    fun `JWT log request preserves auth and query parameters`() {
        val request = jwtApi.getLog(
            "$baseUrl/api/endpoints/1/docker/containers/container/logs",
            "Bearer jwt-token",
            0,
            1,
            1,
            100,
            1
        ).request()

        assertEquals("Bearer jwt-token", request.header("Authorization"))
        assertLogQuery(request.url.queryParameterNames.associateWith {
            request.url.queryParameter(it)
        })
    }

    @Test
    fun `API key log request preserves auth and query parameters`() {
        val request = apiKeyApi.getLog(
            "$baseUrl/api/endpoints/1/docker/containers/container/logs",
            "api-key",
            0,
            1,
            1,
            100,
            1
        ).request()

        assertEquals("api-key", request.header("X-API-Key"))
        assertLogQuery(request.url.queryParameterNames.associateWith {
            request.url.queryParameter(it)
        })
    }

    private fun assertLogQuery(query: Map<String, String?>) {
        assertEquals("0", query["since"])
        assertEquals("1", query["stderr"])
        assertEquals("1", query["stdout"])
        assertEquals("100", query["tail"])
        assertEquals("1", query["timestamps"])
    }
}
