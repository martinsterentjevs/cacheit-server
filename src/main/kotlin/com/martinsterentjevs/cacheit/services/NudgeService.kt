package com.martinsterentjevs.cacheit.services

import com.martinsterentjevs.cacheit.dtos.websockets.WsAccountSignalDto
import com.martinsterentjevs.cacheit.dtos.websockets.WsNudgeDto
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

@Service
class NudgeService(
    val simpMessageTemplate: SimpMessagingTemplate
) {
    fun send(
        userId: String,
        nudgeDto: WsNudgeDto
    ) {
        try {
            simpMessageTemplate.convertAndSendToUser(userId, "/queue/nudges", nudgeDto)
        } catch (e: Exception) {
            println("NUDGE SEND FAILED: $e")
            throw e
        }
    }

    fun sendAccountSignal(
        userId: String,
        signal: WsAccountSignalDto
    ) {
        try {
            simpMessageTemplate.convertAndSendToUser(userId, "/queue/account", signal)
        } catch (e: Exception) {
            println("ACCOUNT SIGNAL SEND FAILED: $e")
            throw e
        }
    }
}