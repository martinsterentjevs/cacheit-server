package com.martinsterentjevs.cacheit.dtos.account

data class AccountProfileDto(
    val accountHolder: String,
    val username: String?,
    val email: String?
)