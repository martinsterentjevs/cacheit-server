
package com.martinsterentjevs.cacheit.dtos.error

data class ErrorResponse(
    // stable, never reworded once shipped — INVALID_CREDENTIALS, etc.
    val errorCode: String,
    // human-readable, free to change for UX
    val message: String
)