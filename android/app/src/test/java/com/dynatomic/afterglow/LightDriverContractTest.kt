// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import org.junit.Assert.*
import org.junit.Test

class LightDriverContractTest {
    private class RecordingDriver(override val capabilities: DeviceCapabilities = DeviceCatalog.qcCapabilities) : LightDriver {
        override val id = "test-recording-driver"
        var begins = 0
        var responses = 0
        var advances = 0
        var lightingCalls = 0
        override fun beginAuthentication(): AuthenticationRequest { begins++; return DaybetterQcDriver.beginAuthentication() }
        override fun response(bytes: ByteArray): DriverResponse { responses++; return DaybetterQcDriver.response(bytes) }
        override fun advanceAuthentication(request: AuthenticationRequest, response: DriverResponse): AuthenticationRequest? {
            advances++
            return DaybetterQcDriver.advanceAuthentication(request, response)
        }
        override fun lighting(state: LightState, change: Change, restoreMode: Boolean): List<ByteArray> {
            lightingCalls++
            return DaybetterQcDriver.lighting(state, change, restoreMode)
        }
    }
    private class Rig(val driver: RecordingDriver = RecordingDriver()) {
        val clock = FakeClock()
        val errors = mutableListOf<String>()
        val session = LightSession(clock, { _, _ -> }, { errors += it }, driver = driver)
        var link = FakeLink(clock)
            private set
        init { session.attach(link) }
        fun authenticate() {
            link.finishSubscription(); clock.runCurrent(); link.acknowledge(0)
            link.notify(responseFrame(0, 67, 67, 72, 73, 80, 1, 2))
            clock.advanceBy(40); link.acknowledge(1)
            link.notify(responseFrame(1, 67, 67, 72, 73, 80, 0))
            check(session.ready)
        }
        fun reconnect() {
            session.disconnect(false)
            link = FakeLink(clock)
            session.attach(link)
        }
        fun drainLighting() {
            repeat(30) {
                val pending = link.pendingIndex
                if (pending >= 0) link.acknowledge(pending)
                if (session.queuedActions == 0 && !clock.hasTimers) return
                clock.advanceBy(40)
            }
            error("Lighting did not settle")
        }
    }

    @Test fun qcDriverOwnsChallengeExchangeAndRequiresMatchingAcceptedConfirmation() {
        val driver: LightDriver = DaybetterQcDriver
        val initial = driver.beginAuthentication()
        assertEquals(0, initial.command)
        assertEquals("a000084343484950acca", initial.bytes.hex())
        val challenge = driver.response(responseFrame(0, 67, 67, 72, 73, 80, 0x12, 0x34))
        val confirmation = driver.advanceAuthentication(initial, challenge)!!
        assertEquals(1, confirmation.command)
        assertEquals(QcProtocol.frame(1, 67, 67, 72, 73, 80, 0x36, 0x78).hex(), confirmation.bytes.hex())
        assertThrows(IllegalArgumentException::class.java) {
            driver.advanceAuthentication(initial, DriverResponse(1, challenge.payload))
        }
        assertThrows(IllegalArgumentException::class.java) {
            driver.advanceAuthentication(confirmation, driver.response(responseFrame(1, 67, 67, 72, 73, 80, 1)))
        }
        assertNull(driver.advanceAuthentication(confirmation, driver.response(responseFrame(1, 67, 67, 72, 73, 80, 0))))
    }

    @Test fun sessionUsesInjectedDriverAndAuthenticationNeverEncodesLighting() {
        val rig = Rig()
        assertEquals(0, rig.driver.begins)
        assertFalse(rig.session.submit(LightState(effect = Effect.MUSIC0)))
        assertEquals(0, rig.driver.lightingCalls)
        rig.authenticate()
        rig.clock.advanceBy(6000)
        assertEquals(1, rig.driver.begins)
        assertEquals(2, rig.driver.responses)
        assertEquals(2, rig.driver.advances)
        assertEquals(0, rig.driver.lightingCalls)
        assertEquals(listOf(0, 1), rig.link.writes.map { it.bytes[1].toInt() and 255 })
        rig.session.submit(LightState(color = 0xffff0000.toInt(), brightness = 80))
        rig.drainLighting()
        assertEquals(1, rig.driver.lightingCalls)
        assertEquals("a01507ff0000003c1b", rig.link.writes.last().bytes.hex())
    }

    @Test fun unsupportedCapabilityIsRejectedBeforeEncodingOrWriting() {
        val capabilities = DeviceCatalog.qcCapabilities.copy(controllerMicrophone = false)
        val rig = Rig(RecordingDriver(capabilities))
        rig.authenticate()
        assertFalse(rig.session.submit(LightState(effect = Effect.MUSIC1)))
        rig.clock.advanceBy(1000)
        assertEquals(0, rig.driver.lightingCalls)
        assertEquals(2, rig.link.writes.size)
        assertEquals(0, rig.session.queuedActions)
        assertEquals(listOf("This light does not support that setting."), rig.errors)
        assertTrue(rig.session.ready)
    }

    @Test fun replacingTransportReauthenticatesThroughDriverWithoutReplayingPriorModeOrColor() {
        val rig = Rig()
        rig.authenticate()
        rig.session.submit(LightState(effect = Effect.MUSIC3))
        rig.drainLighting()
        val old = rig.link
        rig.reconnect()
        assertEquals(1, old.closeCount)
        assertTrue(rig.link.writes.isEmpty())
        rig.authenticate()
        rig.clock.advanceBy(6000)
        assertEquals(2, rig.driver.begins)
        assertEquals(1, rig.driver.lightingCalls)
        assertEquals(listOf(0, 1), rig.link.writes.map { it.bytes[1].toInt() and 255 })
        assertEquals(0, rig.session.queuedActions)
        assertTrue(rig.errors.isEmpty())
    }
}
