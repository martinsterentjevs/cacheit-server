package com.martinsterentjevs.cacheit.dtos.session

import java.util.UUID

data class RefreshRequestDto(
    val deviceId: UUID,
    val refreshToken: String
)