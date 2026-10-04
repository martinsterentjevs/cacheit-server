package com.martinsterentjevs.cacheit.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "accounts")
class Account(
    @Id
    var userId: UUID = UUID.randomUUID(),
    var accountHolder: String,
    @Column(unique = true, nullable = true)
    var username: String?,
    @Column(unique = true, nullable = true)
    var email: String?,
    // A KDF derived hash of the MUK that gets hashed again, see spec ADR-0004-Auth-hash-separation.md
    var passwordHash: String,
    var mekEnvelope: String,
    var kdfSalt: String,
    var createdAt: Instant = Instant.now(),
    var isSingleUser: Boolean = false
) {
    override fun equals(other: Any?): Boolean = this === other || (other is Account && userId == other.userId)

    override fun hashCode(): Int = userId.hashCode()
}

interface AccountRepository : JpaRepository<Account, UUID> {
    fun findByUsernameOrEmail(
        username: String?,
        email: String?
    ): Account?

    fun findByUserId(userFromBearer: UUID): Account
}