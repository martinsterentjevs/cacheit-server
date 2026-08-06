package com.martinsterentjevs.cacheit.integration.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.martinsterentjevs.cacheit.integration.BaseIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

@AutoConfigureMockMvc
abstract class BaseControllerTest : BaseIntegrationTest() {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    /** Wraps a completed request: status is always available, body parsing is opt-in and typed. */
    inner class ApiResponse(
        private val result: MvcResult
    ) {
        val status: HttpStatus get() = HttpStatus.valueOf(result.response.status)
        val rawBody: String get() = result.response.contentAsString

        fun header(name: String): String? = result.response.getHeader(name)

        internal inline fun <reified T> body(): T = objectMapper.readValue(rawBody)

        @PublishedApi
        internal fun <T> parseBody(type: Class<T>): T = objectMapper.readValue(rawBody, type)

        fun expectStatus(expected: HttpStatus): ApiResponse {
            check(status == expected) { "Expected $expected but was $status. Body: $rawBody" }
            return this
        }
    }

    protected fun get(
        path: String,
        headers: Map<String, String> = emptyMap()
    ): ApiResponse = perform(MockMvcRequestBuilders.get(path), body = null, headers = headers)

    protected fun post(
        path: String,
        body: Any? = null,
        headers: Map<String, String> = emptyMap()
    ): ApiResponse = perform(MockMvcRequestBuilders.post(path), body, headers)

    protected fun put(
        path: String,
        body: Any? = null,
        headers: Map<String, String> = emptyMap()
    ): ApiResponse = perform(MockMvcRequestBuilders.put(path), body, headers)

    protected fun patch(
        path: String,
        body: Any? = null,
        headers: Map<String, String> = emptyMap()
    ): ApiResponse = perform(MockMvcRequestBuilders.patch(path), body, headers)

    protected fun delete(
        path: String,
        headers: Map<String, String> = emptyMap()
    ): ApiResponse = perform(MockMvcRequestBuilders.delete(path), body = null, headers = headers)

    /** Convenience for auth'd requests: get("/notes", headers = bearer(token)) */
    protected fun bearer(token: String): Map<String, String> = mapOf("Authorization" to "Bearer $token")

    private fun perform(
        builder: MockHttpServletRequestBuilder,
        body: Any?,
        headers: Map<String, String>
    ): ApiResponse {
        headers.forEach { (name, value) -> builder.header(name, value) }
        if (body != null) {
            builder
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        }
        return ApiResponse(mockMvc.perform(builder).andReturn())
    }
}