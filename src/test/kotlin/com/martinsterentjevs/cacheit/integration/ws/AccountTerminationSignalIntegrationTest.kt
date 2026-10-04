package com.martinsterentjevs.cacheit.integration.ws

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.martinsterentjevs.cacheit.dtos.websockets.WsAccountSignalDto
import com.martinsterentjevs.cacheit.dtos.websockets.WsAccountSignalType
import com.martinsterentjevs.cacheit.services.TokenService
import com.martinsterentjevs.cacheit.testdata.AuthTestHelper.registerAndLogin
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountTerminationSignalIntegrationTest : WsIntegrationTestBase() {
    @Autowired lateinit var tokenService: TokenService

    @BeforeEach
    fun setUp() {
        stompClient = WebSocketStompClient(StandardWebSocketClient())
        val objectMapper =
            ObjectMapper()
                .registerModule(JavaTimeModule())
                .registerModule(KotlinModule.Builder().build())
        val converter = MappingJackson2MessageConverter()
        converter.objectMapper = objectMapper
        stompClient.messageConverter = converter
    }

    @Test
    fun `deleting the account delivers ACCOUNT_TERMINATED on the account queue`() {
        val user = registerAndLogin(restTemplate, tokenService)

        val headers =
            org.springframework.web.socket
                .WebSocketHttpHeaders()
        headers.add("Authorization", "Bearer ${user.accessToken}")

        val received = CompletableFuture<WsAccountSignalDto>()
        val connected = CompletableFuture<StompSession>()

        stompClient.connectAsync(
            "ws://localhost:$port/ws/sync",
            headers,
            object : StompSessionHandlerAdapter() {
                override fun afterConnected(
                    session: StompSession,
                    connectedHeaders: StompHeaders
                ) {
                    session.subscribe(
                        "/user/queue/account",
                        object : StompFrameHandler {
                            override fun getPayloadType(headers: StompHeaders) = WsAccountSignalDto::class.java

                            override fun handleFrame(
                                headers: StompHeaders,
                                payload: Any?
                            ) {
                                received.complete(payload as WsAccountSignalDto)
                            }
                        }
                    )
                    connected.complete(session)
                }
            }
        )
        connected.get(5, TimeUnit.SECONDS)
        Thread.sleep(300) // let SUBSCRIBE register before the deletion fires

        val deleteHeaders = HttpHeaders()
        deleteHeaders.set("Authorization", "Bearer ${user.accessToken}")
        restTemplate.exchange(
            "/account",
            HttpMethod.DELETE,
            HttpEntity<Void>(deleteHeaders),
            Void::class.java
        )

        val signal = received.get(5, TimeUnit.SECONDS)
        assert(signal.type == WsAccountSignalType.ACCOUNT_TERMINATED)
    }
}