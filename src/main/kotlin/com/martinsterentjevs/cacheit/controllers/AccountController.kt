package com.martinsterentjevs.cacheit.controllers

import com.martinsterentjevs.cacheit.dtos.account.DeviceSessionDto
import com.martinsterentjevs.cacheit.services.AccountService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/account")
class AccountController(
    private val accountService: AccountService
) {
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAccount(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String
    ) = accountService.deleteAccount(authorization)

    @GetMapping("/devices")
    fun listDevices(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String
    ): List<DeviceSessionDto> = accountService.listDevices(authorization)

    @DeleteMapping("/devices/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeDevice(
        @PathVariable deviceId: UUID,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String
    ) = accountService.revokeDevice(deviceId, authorization)
}