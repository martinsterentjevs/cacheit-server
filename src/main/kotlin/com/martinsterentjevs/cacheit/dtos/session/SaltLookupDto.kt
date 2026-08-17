package com.martinsterentjevs.cacheit.dtos.session

import org.jetbrains.annotations.NotNull

data class SaltLookupDto(
    @field:NotNull
    var identifier: String
)