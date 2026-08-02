package com.martinsterentjevs.cacheit.dtos.session

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

data class RegisterDto(
    @field:NotBlank(message = "Account holder name is required")
    val accountHolder: String,
    @field:Size(min = 3, max = 32, message = "Username must be 3–32 characters")
    val username: String?,
    @field:Email(message = "Must be a valid email address")
    val email: String?,
    @field:NotBlank
    @field:Size(min = 8, message = "Password must be at least 8 characters")
    val password: String,
    val deviceId: UUID,
    @field:NotBlank
    val deviceName: String,
    @field:NotBlank(message = "MEK envelope is required")
    @field:Pattern(
        regexp = "^[A-Za-z0-9+/]+={0,2}$",
        message = "MEK envelope must be valid base64"
    )
    val encMekEnvelope: String
)