package com.martinsterentjevs.cacheit.models.auth

import com.martinsterentjevs.cacheit.models.Account
import java.util.UUID

data class RequestIdentity(
    val account: Account,
    val deviceId: UUID
)