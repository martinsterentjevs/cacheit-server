package com.martinsterentjevs.cacheit.models

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID
@Entity
@Table(name = "device_sessions")
class DeviceSession (
    @Id
    var deviceId: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="user_id",nullable = false)
    var account: Account,
    var deviceName: String?,
    var refreshToken: String,
    var lastSeenAt: Instant = Instant.now(),
    var createdAt: Instant = Instant.now(),
    ){
    override fun equals(other: Any?): Boolean =
        this === other || (other is DeviceSession && deviceId == other.deviceId)

    override fun hashCode(): Int = deviceId.hashCode()
}

interface DeviceSessionRepository : JpaRepository<DeviceSession,UUID>