package com.martinsterentjevs.cacheit.integration.ws

import com.martinsterentjevs.cacheit.dtos.websockets.WsNudgeDto
import com.martinsterentjevs.cacheit.dtos.websockets.WsNudgeType
import com.martinsterentjevs.cacheit.services.NudgeService
import com.martinsterentjevs.cacheit.services.TokenService
import com.martinsterentjevs.cacheit.testdata.AuthTestHelper.registerAndLogin
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.web.socket.WebSocketHttpHeaders
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WebSocketAuthIntegrationTest : WsIntegrationTestBase() {
    @Autowired
    lateinit var tokenService: TokenService

    @Autowired
    lateinit var nudgeService: NudgeService

    @Test
    fun `handshake with valid token succeeds and resolves principal`() {
        val user =
            registerAndLogin(
                restTemplate,
                tokenService
            )

        val headers = WebSocketHttpHeaders()
        headers.add(
            "Authorization",
            "Bearer ${user.accessToken}"
        )

        val connected =
            CompletableFuture<StompSession>()

        stompClient.connectAsync(
            wsUrl(),
            headers,
            object : org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter() {
                override fun afterConnected(
                    session: StompSession,
                    connectedHeaders: org.springframework.messaging.simp.stomp.StompHeaders
                ) {
                    connected.complete(session)
                }

                override fun handleException(
                    session: StompSession,
                    command: org.springframework.messaging.simp.stomp.StompCommand?,
                    headers: org.springframework.messaging.simp.stomp.StompHeaders,
                    payload: ByteArray,
                    exception: Throwable
                ) {
                    connected.completeExceptionally(exception)
                }

                override fun handleTransportError(
                    session: StompSession,
                    exception: Throwable
                ) {
                    connected.completeExceptionally(exception)
                }
            }
        )

        val session =
            connected.get(5, TimeUnit.SECONDS)

        assert(session.isConnected)

        session.disconnect()
    }

    @Test
    fun `handshake with missing token is rejected`() {
        val connected =
            CompletableFuture<StompSession>()

        stompClient.connectAsync(
            wsUrl(),
            WebSocketHttpHeaders(),
            object : org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter() {
                override fun afterConnected(
                    session: StompSession,
                    connectedHeaders: org.springframework.messaging.simp.stomp.StompHeaders
                ) {
                    connected.complete(session)
                }

                override fun handleTransportError(
                    session: StompSession,
                    exception: Throwable
                ) {
                    connected.completeExceptionally(exception)
                }
            }
        )

        assertFailsWith<Exception> {
            connected.get(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `handshake with invalid token is rejected`() {
        val headers = WebSocketHttpHeaders()
        headers.add(
            "Authorization",
            "Bearer garbage.invalid.token"
        )

        val connected =
            CompletableFuture<StompSession>()

        stompClient.connectAsync(
            wsUrl(),
            headers,
            object : org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter() {
                override fun afterConnected(
                    session: StompSession,
                    connectedHeaders: org.springframework.messaging.simp.stomp.StompHeaders
                ) {
                    connected.complete(session)
                }

                override fun handleTransportError(
                    session: StompSession,
                    exception: Throwable
                ) {
                    connected.completeExceptionally(exception)
                }
            }
        )

        assertFailsWith<Exception> {
            connected.get(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `nudge sent to authenticated user is delivered on their queue`() {
        val user =
            registerAndLogin(
                restTemplate,
                tokenService
            )

        val (_, received) =
            connectAndCaptureNudges(user.accessToken)

        val mark = received.mark()

        val testNudge =
            WsNudgeDto(
                type = WsNudgeType.NOTE_UPDATED,
                noteId = UUID.randomUUID(),
                lastModifiedAt = Instant.now()
            )

        nudgeService.send(
            user.userId.toString(),
            testNudge
        )

        val result =
            received.awaitNudge(mark) {
                it.type == testNudge.type &&
                    it.noteId == testNudge.noteId
            }

        assertEquals(testNudge.type, result.type)
        assertEquals(testNudge.noteId, result.noteId)
        assertEquals(
            testNudge.lastModifiedAt,
            result.lastModifiedAt
        )
    }
}