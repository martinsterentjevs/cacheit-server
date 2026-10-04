package com.martinsterentjevs.cacheit

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity

@EnableWebSecurity
@SpringBootApplication
class CacheitApplication

fun main(args: Array<String>) {
    runApplication<CacheitApplication>(*args)
}