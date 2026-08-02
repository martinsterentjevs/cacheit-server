package com.martinsterentjevs.cacheit.controllers

import com.martinsterentjevs.cacheit.dtos.session.AccountSessionDto
import com.martinsterentjevs.cacheit.dtos.session.LoginDto
import com.martinsterentjevs.cacheit.dtos.session.RefreshRequestDto
import com.martinsterentjevs.cacheit.dtos.session.RegisterDto
import com.martinsterentjevs.cacheit.services.UserService
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val userService: UserService
) {
    @PostMapping("/register", produces = ["application/json"])
    fun register(
        @Valid @RequestBody registration: RegisterDto
    ): AccountSessionDto = userService.registerUser(registration)

    @PostMapping("/login", produces = ["application/json"])
    fun login(
        @Valid @RequestBody login: LoginDto
    ): AccountSessionDto = userService.authenticateUser(login)

    @PostMapping("/refresh", produces = ["application/json"])
    fun refresh(
        @RequestBody request: RefreshRequestDto
    ): AccountSessionDto = userService.refreshSession(request)

    @PostMapping("/logout")
    fun logout(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String
    ) {
        userService.logout(authorization)
    }
}