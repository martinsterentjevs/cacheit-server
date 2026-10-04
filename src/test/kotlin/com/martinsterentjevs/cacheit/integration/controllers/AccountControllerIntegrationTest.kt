package com.martinsterentjevs.cacheit.integration.controllers

import com.martinsterentjevs.cacheit.dtos.account.DeviceSessionDto
import com.martinsterentjevs.cacheit.dtos.note.NoteDto
import com.martinsterentjevs.cacheit.dtos.session.AccountSessionDto
import com.martinsterentjevs.cacheit.integration.controllers.notes.NoteControllerTestBase
import com.martinsterentjevs.cacheit.testdata.AccountTestDataFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

class AccountControllerIntegrationTest : NoteControllerTestBase() {
    private val accountPath = "/account"
    private val devicesPath = "/account/devices"

    // --- DELETE /account ---

    @Test
    fun `delete account returns 204`() {
        val (_, session) = registerAndAuthenticate()

        delete(accountPath, headers = bearer(session.accessToken)).expectStatus(HttpStatus.NO_CONTENT)
    }

    @Test
    fun `delete account rejects a missing access token`() {
        delete(accountPath).expectStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `delete account cascades to notes - re-registering the same email starts with an empty account`() {
        val (registration, session) = registerAndAuthenticate()
        createNote(session.accessToken, encTitle = "Will be deleted")
        createNote(session.accessToken, encTitle = "Also deleted")

        delete(accountPath, headers = bearer(session.accessToken)).expectStatus(HttpStatus.NO_CONTENT)

        // Re-registering with the same email only succeeds if the old account row is
        // genuinely gone (AccountAlreadyExistsException would fire otherwise) - this proves
        // deletion happened without needing to know what an orphaned old token does.
        val newRegistration = AccountTestDataFactory.validRegistrationRequest(email = registration.email)
        val newSession =
            post("/auth/register", newRegistration).expectStatus(HttpStatus.OK).body<AccountSessionDto>()

        val notes =
            get("/notes", headers = bearer(newSession.accessToken))
                .expectStatus(HttpStatus.OK)
                .body<List<NoteDto>>()

        assertThat(notes).isEmpty()
    }

    // --- GET /account/devices ---

    @Test
    fun `list devices returns a single entry marked current for a fresh account`() {
        val (_, session) = registerAndAuthenticate()

        val devices =
            get(devicesPath, headers = bearer(session.accessToken))
                .expectStatus(HttpStatus.OK)
                .body<List<DeviceSessionDto>>()

        assertThat(devices).hasSize(1)
        assertThat(devices.single().isCurrentDevice).isTrue()
    }

    @Test
    fun `list devices marks only the calling device as current`() {
        val (registration, primarySession) = registerAndAuthenticate()
        val secondSession = loginSecondDevice(registration)

        val devicesFromPrimary =
            get(devicesPath, headers = bearer(primarySession.accessToken))
                .expectStatus(HttpStatus.OK)
                .body<List<DeviceSessionDto>>()

        assertThat(devicesFromPrimary).hasSize(2)
        val current = devicesFromPrimary.single { it.isCurrentDevice }
        assertThat(current.deviceId).isEqualTo(primarySession.deviceId)

        val devicesFromSecondary =
            get(devicesPath, headers = bearer(secondSession.accessToken))
                .expectStatus(HttpStatus.OK)
                .body<List<DeviceSessionDto>>()

        val currentFromSecondary = devicesFromSecondary.single { it.isCurrentDevice }
        assertThat(currentFromSecondary.deviceId).isEqualTo(secondSession.deviceId)
    }

    @Test
    fun `list devices rejects a missing access token`() {
        get(devicesPath).expectStatus(HttpStatus.UNAUTHORIZED)
    }

    // --- DELETE /account/devices/{deviceId} ---

    @Test
    fun `revoke device removes it from the device list`() {
        val (registration, primarySession) = registerAndAuthenticate()
        val secondSession = loginSecondDevice(registration)

        delete(
            "$devicesPath/${secondSession.deviceId}",
            headers = bearer(primarySession.accessToken)
        ).expectStatus(HttpStatus.NO_CONTENT)

        val remaining =
            get(devicesPath, headers = bearer(primarySession.accessToken))
                .expectStatus(HttpStatus.OK)
                .body<List<DeviceSessionDto>>()

        assertThat(remaining).hasSize(1)
        assertThat(remaining.single().deviceId).isEqualTo(primarySession.deviceId)
    }

    @Test
    fun `revoke device on an unknown deviceId returns 404`() {
        val (_, session) = registerAndAuthenticate()

        delete(
            "$devicesPath/${UUID.randomUUID()}",
            headers = bearer(session.accessToken)
        ).expectStatus(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `revoke device belonging to another account returns 404, not a leak of its existence`() {
        val (_, ownerSession) = registerAndAuthenticate()
        val (_, otherSession) = registerAndAuthenticate()

        // ownerSession attempts to revoke a device that genuinely exists, just not theirs.
        delete(
            "$devicesPath/${otherSession.deviceId}",
            headers = bearer(ownerSession.accessToken)
        ).expectStatus(HttpStatus.NOT_FOUND)

        // Confirm it's untouched - the other account's device is still there.
        val stillThere =
            get(devicesPath, headers = bearer(otherSession.accessToken))
                .expectStatus(HttpStatus.OK)
                .body<List<DeviceSessionDto>>()
        assertThat(stillThere).hasSize(1)
    }

    @Test
    fun `revoke device rejects a missing access token`() {
        val (_, session) = registerAndAuthenticate()

        delete("$devicesPath/${session.deviceId}").expectStatus(HttpStatus.UNAUTHORIZED)
    }
}