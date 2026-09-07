package com.martinsterentjevs.cacheit.services

import com.martinsterentjevs.cacheit.exceptions.SecretNotFoundException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service

@Service
class SecretsManager(
    @Autowired val environment: Environment
) {
    init {
        require(getHmacSecret().isNotEmpty()) { "HMAC_SECRET cannot be empty" }
        require(getJwtSecret().isNotEmpty()) { "JWT_SECRET cannot be empty" }
        require(getJwtSecret().length >= 32) { "JWT_SECRET must be at least 32 characters long" }
        require(getDbUsername().isNotEmpty()) { "POSTGRES_USER cannot be empty" }
        require(getDatabaseName().isNotEmpty()) { "POSTGRES_DB cannot be empty" }
        require(getDbPassword().isNotEmpty()) { "POSTGRES_PASSWORD cannot be empty" }
    }

    fun getHmacSecret(): String =
        environment.getProperty("HMAC_SECRET") ?: throw SecretNotFoundException("HMAC Key not found")

    fun getJwtSecret(): String =
        environment.getProperty("JWT_SECRET") ?: throw SecretNotFoundException("JWT Secret not found")

    fun isSingleUser(): Boolean = environment.getProperty("IS_SINGLE_USER")?.toBoolean() ?: false

    // Database keys
    fun getDatabaseName(): String =
        environment.getProperty("POSTGRES_DB") ?: throw SecretNotFoundException("Database name not set")

    fun getDbUsername(): String =
        environment.getProperty("POSTGRES_USER") ?: throw SecretNotFoundException("DB Username not set")

    fun getDbPassword(): String =
        environment.getProperty("POSTGRES_PASSWORD") ?: throw SecretNotFoundException("DB Password not set")
}