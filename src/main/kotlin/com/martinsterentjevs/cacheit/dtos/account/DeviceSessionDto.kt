package com.martinsterentjevs.cacheit.dtos.account

import java.time.Instant
import java.util.*

data class DeviceSessionDto(
    val deviceId: UUID,
    val deviceName: String?,
    val lastSeenAt: Instant,
    val createdAt: Instant,
    val isCurrentDevice: Boolean
)