package com.martinsterentjevs.cacheit.integration.ws

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.martinsterentjevs.cacheit.dtos.websockets.WsNudgeDto
import com.martinsterentjevs.cacheit.integration.BaseIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.lang.reflect.Type
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class WsIntegrationTestBase : BaseIntegrationTest() {
    @LocalServerPort
    protected var port: Int = 0

    @Autowired
    protected lateinit var restTemplate: TestRestTemplate

    protected lateinit var stompClient: WebSocketStompClient

    private val sessions = CopyOnWriteArrayList<StompSession>()

    protected fun wsUrl(): String = "ws://localhost:$port/ws/sync"

    @BeforeEach
    fun setUpStompClient() {
        stompClient = WebSocketStompClient(StandardWebSocketClient())

        val objectMapper =
            ObjectMapper()
                .registerModule(JavaTimeModule())
                .registerModule(KotlinModule.Builder().build())

        val converter = MappingJackson2MessageConverter()
        converter.objectMapper = objectMapper

        stompClient.messageConverter = converter
    }

    @AfterEach
    fun tearDownWebSockets() {
        sessions.forEach { session ->
            try {
                if (session.isConnected) {
                    session.disconnect()
                }
            } catch (_: Exception) {
                // Test cleanup must not mask the original test failure.
            }
        }

        sessions.clear()
    }

    /**
     * Connects an authenticated STOMP client and subscribes to the user's
     * nudge queue.
     *
     * The returned capture contains every nudge received by this session.
     *
     * The small settle delay is deliberately kept here rather than scattered
     * through individual tests. The STOMP client version used by this project
     * does not expose subscription receipts.
     */
    protected fun connectAndCaptureNudges(accessToken: String): Pair<StompSession, NudgeCapture> {
        val headers = WebSocketHttpHeaders()
        headers.add("Authorization", "Bearer $accessToken")

        val capture = NudgeCapture()
        val connected = java.util.concurrent.CompletableFuture<StompSession>()

        stompClient.connectAsync(
            wsUrl(),
            headers,
            object : StompSessionHandlerAdapter() {
                override fun afterConnected(
                    session: StompSession,
                    connectedHeaders: StompHeaders
                ) {
                    session.subscribe(
                        "/user/queue/nudges",
                        object : StompFrameHandler {
                            override fun getPayloadType(headers: StompHeaders): Type = WsNudgeDto::class.java

                            override fun handleFrame(
                                headers: StompHeaders,
                                payload: Any?
                            ) {
                                capture.add(payload as WsNudgeDto)
                            }
                        }
                    )

                    connected.complete(session)
                }

                override fun handleException(
                    session: StompSession,
                    command: StompCommand?,
                    headers: StompHeaders,
                    payload: ByteArray,
                    exception: Throwable
                ) {
                    capture.fail(exception)
                    connected.completeExceptionally(exception)
                }

                override fun handleTransportError(
                    session: StompSession,
                    exception: Throwable
                ) {
                    capture.fail(exception)
                    connected.completeExceptionally(exception)
                }
            }
        )

        val session =
            try {
                connected.get(5, TimeUnit.SECONDS)
            } catch (e: Exception) {
                throw AssertionError(
                    "Failed to establish WebSocket/STOMP connection",
                    e
                )
            }

        sessions += session

        // STOMP client version has no subscription receipt support.
        Thread.sleep(100)

        return session to capture
    }

    protected class NudgeCapture {
        private val messages =
            CopyOnWriteArrayList<WsNudgeDto>()

        private val failure =
            java.util.concurrent.atomic
                .AtomicReference<Throwable?>()

        fun add(nudge: WsNudgeDto) {
            messages += nudge
        }

        fun fail(exception: Throwable) {
            failure.compareAndSet(null, exception)
        }

        /**
         * Returns the current number of received messages.
         *
         * Use this before an action to establish a boundary:
         *
         * val mark = received.mark()
         * performAction()
         * received.awaitNudge(mark) { ... }
         */
        fun mark(): Int = messages.size

        fun snapshot(): List<WsNudgeDto> = messages.toList()

        /**
         * Wait for any matching nudge.
         */
        fun awaitNudge(
            timeout: Long = 5,
            unit: TimeUnit = TimeUnit.SECONDS,
            predicate: (WsNudgeDto) -> Boolean
        ): WsNudgeDto =
            awaitNudgeFrom(
                startIndex = 0,
                timeout = timeout,
                unit = unit,
                predicate = predicate
            )

        /**
         * Wait only for a matching nudge received after the supplied mark.
         */
        fun awaitNudge(
            mark: Int,
            timeout: Long = 5,
            unit: TimeUnit = TimeUnit.SECONDS,
            predicate: (WsNudgeDto) -> Boolean
        ): WsNudgeDto =
            awaitNudgeFrom(
                startIndex = mark,
                timeout = timeout,
                unit = unit,
                predicate = predicate
            )

        fun awaitNoNudge(
            mark: Int,
            timeout: Long = 2,
            unit: TimeUnit = TimeUnit.SECONDS,
            predicate: (WsNudgeDto) -> Boolean
        ) {
            val deadline =
                System.nanoTime() + unit.toNanos(timeout)

            while (System.nanoTime() < deadline) {
                checkFailure()

                val unexpected =
                    messages
                        .drop(mark)
                        .firstOrNull(predicate)

                if (unexpected != null) {
                    throw AssertionError(
                        "Unexpected nudge received: $unexpected"
                    )
                }

                Thread.sleep(10)
            }
        }

        /**
         * Convenience for matching one nudge type.
         */
        fun awaitType(
            type: com.martinsterentjevs.cacheit.dtos.websockets.WsNudgeType,
            noteId: java.util.UUID? = null,
            timeout: Long = 5,
            unit: TimeUnit = TimeUnit.SECONDS
        ): WsNudgeDto =
            awaitNudge(timeout, unit) {
                it.type == type &&
                    (noteId == null || it.noteId == noteId)
            }

        /**
         * Wait for multiple matching nudges.
         *
         * The predicates are matched independently and may arrive in any order.
         */
        fun awaitNudges(
            predicates: List<(WsNudgeDto) -> Boolean>,
            timeout: Long = 5,
            unit: TimeUnit = TimeUnit.SECONDS
        ): List<WsNudgeDto> =
            awaitNudgesFrom(
                startIndex = 0,
                predicates = predicates,
                timeout = timeout,
                unit = unit
            )

        /**
         * Same as awaitNudges(), but ignores messages before mark.
         */
        fun awaitNudges(
            mark: Int,
            predicates: List<(WsNudgeDto) -> Boolean>,
            timeout: Long = 5,
            unit: TimeUnit = TimeUnit.SECONDS
        ): List<WsNudgeDto> =
            awaitNudgesFrom(
                startIndex = mark,
                predicates = predicates,
                timeout = timeout,
                unit = unit
            )

        /**
         * Wait for exactly count messages after mark.
         */
        fun awaitCount(
            mark: Int = 0,
            count: Int,
            timeout: Long = 5,
            unit: TimeUnit = TimeUnit.SECONDS
        ): List<WsNudgeDto> {
            require(count >= 0)

            val deadline =
                System.nanoTime() + unit.toNanos(timeout)

            while (System.nanoTime() < deadline) {
                checkFailure()

                val result =
                    messages.drop(mark)

                if (result.size >= count) {
                    return result.take(count)
                }

                Thread.sleep(10)
            }

            throw TimeoutException(
                "Timed out waiting for $count nudges. " +
                    "Received ${messages.size - mark} after mark. " +
                    "Messages: ${messages.drop(mark)}"
            )
        }

        /**
         * Assert that no matching nudge arrives during the timeout.
         */
        fun awaitNoNudge(
            timeout: Long = 2,
            unit: TimeUnit = TimeUnit.SECONDS,
            predicate: (WsNudgeDto) -> Boolean
        ) {
            val deadline =
                System.nanoTime() + unit.toNanos(timeout)

            while (System.nanoTime() < deadline) {
                checkFailure()

                val unexpected =
                    messages.firstOrNull(predicate)

                if (unexpected != null) {
                    throw AssertionError(
                        "Unexpected nudge received: $unexpected"
                    )
                }

                Thread.sleep(10)
            }
        }

        private fun awaitNudgeFrom(
            startIndex: Int,
            timeout: Long,
            unit: TimeUnit,
            predicate: (WsNudgeDto) -> Boolean
        ): WsNudgeDto {
            val deadline =
                System.nanoTime() + unit.toNanos(timeout)

            while (System.nanoTime() < deadline) {
                checkFailure()

                val match =
                    messages
                        .drop(startIndex)
                        .firstOrNull(predicate)

                if (match != null) {
                    return match
                }

                Thread.sleep(10)
            }

            throw TimeoutException(
                "Timed out waiting for matching nudge. " +
                    "Received after mark: ${messages.drop(startIndex)}"
            )
        }

        private fun awaitNudgesFrom(
            startIndex: Int,
            predicates: List<(WsNudgeDto) -> Boolean>,
            timeout: Long,
            unit: TimeUnit
        ): List<WsNudgeDto> {
            if (predicates.isEmpty()) {
                return emptyList()
            }

            val deadline =
                System.nanoTime() + unit.toNanos(timeout)

            while (System.nanoTime() < deadline) {
                checkFailure()

                val available =
                    messages.drop(startIndex)

                val remaining =
                    predicates.toMutableList()

                val matched =
                    mutableListOf<WsNudgeDto>()

                for (message in available) {
                    val index =
                        remaining.indexOfFirst { it(message) }

                    if (index >= 0) {
                        matched += message
                        remaining.removeAt(index)

                        if (remaining.isEmpty()) {
                            return matched
                        }
                    }
                }

                Thread.sleep(10)
            }

            throw TimeoutException(
                "Timed out waiting for ${predicates.size} matching nudges. " +
                    "Received after mark: ${messages.drop(startIndex)}"
            )
        }

        private fun checkFailure() {
            failure.get()?.let {
                throw AssertionError(
                    "WebSocket/STOMP client failed",
                    it
                )
            }
        }
    }
}