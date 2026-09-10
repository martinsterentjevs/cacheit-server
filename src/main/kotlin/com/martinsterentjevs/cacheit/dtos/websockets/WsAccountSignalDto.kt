package com.martinsterentjevs.cacheit.dtos.websockets

data class WsAccountSignalDto(
    val type: WsAccountSignalType
)

enum class WsAccountSignalType {
    ACCOUNT_TERMINATED
}