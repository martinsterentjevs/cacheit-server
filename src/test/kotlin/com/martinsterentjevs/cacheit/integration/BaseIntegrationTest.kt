package com.martinsterentjevs.cacheit.integration

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest // webEnvironment defaults to MOCK — remove RANDOM_PORT
abstract class BaseIntegrationTest {
    companion object {
        @Container
        @ServiceConnection
        val postgres =
            PostgreSQLContainer("postgres:15-alpine")
                .withDatabaseName("cacheit")
                .withUsername("user")
                .withPassword("password")
    }
}