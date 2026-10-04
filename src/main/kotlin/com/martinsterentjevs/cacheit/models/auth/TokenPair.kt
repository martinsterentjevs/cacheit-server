package com.martinsterentjevs.cacheit.models.auth

data class TokenPair(
    val accessToken: String,
    val refreshToken: String
)