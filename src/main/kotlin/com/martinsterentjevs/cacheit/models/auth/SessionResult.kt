package com.martinsterentjevs.cacheit.models.auth

import com.martinsterentjevs.cacheit.models.DeviceSession

data class SessionResult(
    val tokens: TokenPair,
    val deviceSession: DeviceSession
)