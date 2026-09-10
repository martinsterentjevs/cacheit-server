package com.martinsterentjevs.cacheit.services

import com.martinsterentjevs.cacheit.dtos.account.DeviceSessionDto
import com.martinsterentjevs.cacheit.events.AccountTerminatedEvent
import com.martinsterentjevs.cacheit.exceptions.DeviceNotFoundException
import com.martinsterentjevs.cacheit.models.AccountRepository
import com.martinsterentjevs.cacheit.models.DeviceSession
import com.martinsterentjevs.cacheit.models.DeviceSessionRepository
import com.martinsterentjevs.cacheit.models.NoteRepository
import com.martinsterentjevs.cacheit.models.NoteVersionRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AccountService(
    private val userService: UserService,
    private val accountRepository: AccountRepository,
    private val deviceSessionRepository: DeviceSessionRepository,
    private val noteRepository: NoteRepository,
    private val noteVersionRepository: NoteVersionRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    @Transactional
    fun deleteAccount(bearer: String) {
        val identity = userService.getRequestIdentity(bearer)
        val userId = identity.account.userId

        noteVersionRepository.deleteAllByNoteAccountUserId(userId)
        noteRepository.deleteAllByAccountUserId(userId)
        deviceSessionRepository.deleteAllByAccountUserId(userId)
        accountRepository.delete(identity.account)

        eventPublisher.publishEvent(AccountTerminatedEvent(userId))
    }

    fun listDevices(bearer: String): List<DeviceSessionDto> {
        val identity = userService.getRequestIdentity(bearer)
        return deviceSessionRepository
            .findAllByAccountUserId(identity.account.userId)
            .map { it.toDto(isCurrentDevice = it.deviceId == identity.deviceId) }
    }

    @Transactional
    fun revokeDevice(
        deviceId: UUID,
        bearer: String
    ) {
        val identity = userService.getRequestIdentity(bearer)
        val device =
            deviceSessionRepository.findByDeviceId(deviceId)
                ?: throw DeviceNotFoundException()

        if (device.account.userId != identity.account.userId) {
            // Same exception as "not found" - don't leak whether a deviceId belongs to
            // someone else's account.
            throw DeviceNotFoundException()
        }

        deviceSessionRepository.delete(device)
    }

    private fun DeviceSession.toDto(isCurrentDevice: Boolean) =
        DeviceSessionDto(
            deviceId = deviceId,
            deviceName = deviceName,
            lastSeenAt = lastSeenAt,
            createdAt = createdAt,
            isCurrentDevice = isCurrentDevice
        )
}