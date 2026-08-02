package com.martinsterentjevs.cacheit.domain

class TokenValidationException(message: String) : RuntimeException(message)
class InvalidCredentialsException(message: String) : RuntimeException(message)
class SessionNotFoundException(message: String) : RuntimeException(message)