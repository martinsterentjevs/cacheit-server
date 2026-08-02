package com.martinsterentjevs.cacheit.dtos.account

import java.util.Date
import java.util.UUID

data class DeviceSessionDto(
    val deviceId: UUID,
    val deviceName: String?,
    val lastSeenAt: Date,
    val isCurrentDevice: Boolean
)