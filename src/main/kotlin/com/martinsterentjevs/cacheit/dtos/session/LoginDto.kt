package com.martinsterentjevs.cacheit.dtos.session

import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class LoginDto(
    @field:NotBlank(message = "Username or email is required")
    val identifier: String,
    @field:NotBlank
    val authHash: String,
    val deviceId: UUID,
    @field:NotBlank
    val deviceName: String
)