package com.martinsterentjevs.cacheit.dtos.session

import java.util.UUID

data class AccountSessionDto(
    val userId: UUID,
    val deviceId: UUID,
    val accessToken: String,
    val refreshToken: String,
    val encMekEnvelope: String
)