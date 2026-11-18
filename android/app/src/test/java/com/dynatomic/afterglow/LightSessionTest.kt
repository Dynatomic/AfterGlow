// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import org.junit.Assert.*
import org.junit.Test

class LightSessionTest {
    private val state = LightState(color = 0xffff0000.toInt(), brightness = 80)
    private val challenge = responseFrame(0, 67, 67, 72, 73, 80, 0x12, 0x34)
    private val accepted = responseFrame(1, 67, 67, 72, 73, 80, 0)

    @Test fun subscribesBeforeWritingAndConnectionNeverTurnsTheLightOn() {
        val rig = SessionRig()
        assertEquals(listOf("subscribe"), rig.link.events)
        rig.clock.advanceBy(100)
        assertTrue(rig.link.writes.isEmpty())
        rig.authenticate()
        assertEquals(listOf("subscribe", "write", "write"), rig.link.events)
        assertEquals(listOf(0, 1), rig.link.writes.map { it.bytes[1].toInt() and 255 })
        rig.clock.advanceBy(6000)
        assertTrue(rig.session.ready)
        assertEquals(2, rig.link.writes.size)
        assertTrue(rig.errors.isEmpty())
    }

    @Test fun challengeAndAcceptanceEachWaitForBothWriteAckAndExpectedResponse() {
        val rig = SessionRig()
        rig.link.finishSubscription()
        rig.clock.runCurrent()
        rig.link.notify(challenge)
        rig.clock.advanceBy(100)
        assertEquals(1, rig.link.writes.size)
        assertFalse(rig.session.ready)
        rig.link.acknowledge(0)
        rig.clock.runCurrent()
        assertEquals(2, rig.link.writes.size)
        assertEquals(QcProtocol.frame(1, 67, 67, 72, 73, 80, 0x36, 0x78).hex(), rig.link.writes[1].bytes.hex())
        rig.link.notify(accepted)
        assertFalse(rig.session.ready)
        rig.link.acknowledge(1)
        assertTrue(rig.session.ready)
        assertEquals(listOf("Authenticated" to true), rig.statuses.filter { it.second })
    }

    @Test fun writeAckAloneCannotAuthenticate() {
        val rig = SessionRig()
        rig.link.finishSubscription()
        rig.clock.runCurrent()
        rig.link.acknowledge(0)
        rig.clock.advanceBy(40)
        assertFalse(rig.session.ready)
        assertEquals(1, rig.link.writes.size)
        rig.link.notify(challenge)
        rig.clock.runCurrent()
        rig.link.acknowledge(1)
        assertFalse(rig.session.ready)
        rig.link.notify(accepted)
        assertTrue(rig.session.ready)
    }

    @Test fun disconnectedControlsAreRejectedAndNeverReplayedAfterAuthentication() {
        val rig = SessionRig()
        assertFalse(rig.session.submit(state))
        assertEquals(0, rig.session.queuedActions)
        rig.authenticate()
        rig.clock.advanceBy(1000)
        assertEquals(2, rig.link.writes.size)
        rig.session.disconnect()
        assertFalse(rig.session.submit(state.copy(effect = Effect.MUSIC0)))
        val replacement = FakeLink(rig.clock)
        rig.session.attach(replacement)
        rig.authenticate(replacement)
        rig.clock.advanceBy(1000)
        assertEquals(2, replacement.writes.size)
        assertEquals(2, rig.errors.size)
    }

    @Test fun subscriptionFailureClosesLinkWithoutAnyWrites() {
        val rig = SessionRig()
        rig.link.finishSubscription(false)
        rig.clock.advanceBy(10000)
        assertFalse(rig.session.ready)
        assertEquals(1, rig.link.closeCount)
        assertTrue(rig.link.writes.isEmpty())
        assertEquals(listOf("Could not enable light notifications"), rig.errors)
    }

    @Test fun subscriptionAndEachAuthenticationStageTimeOutAndCleanUp() {
        for (stage in 0..2) {
            val rig = SessionRig()
            if (stage > 0) {
                rig.link.finishSubscription()
                rig.clock.runCurrent()
                rig.link.acknowledge(0)
            }
            if (stage > 1) {
                rig.link.notify(challenge)
                rig.clock.advanceBy(40)
                rig.link.acknowledge(1)
            }
            rig.clock.advanceBy(5000)
            assertFalse("stage $stage", rig.session.ready)
            assertEquals("stage $stage", 1, rig.link.closeCount)
            assertEquals(0, rig.session.queuedActions)
            assertEquals(1, rig.errors.size)
            assertTrue(rig.errors.single().contains("timed out"))
            val count = rig.link.writes.size
            rig.clock.advanceBy(10000)
            assertEquals(count, rig.link.writes.size)
        }
    }

    @Test fun authenticationWriteFailureClosesAndReportsOnce() {
        val rig = SessionRig()
        rig.link.finishSubscription()
        rig.clock.runCurrent()
        rig.link.acknowledge(0, false)
        rig.clock.advanceBy(10000)
        assertFalse(rig.session.ready)
        assertEquals(1, rig.link.closeCount)
        assertEquals(listOf("Authentication write failed"), rig.errors)
    }

    @Test fun malformedWrongCommandAndUnsupportedChallengeFailClosed() {
        val responses = listOf(
            challenge.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() },
            responseFrame(1, 67, 67, 72, 73, 80, 0),
            responseFrame(0, 67, 67, 72, 73, 81, 0, 0)
        )
        responses.forEach { response ->
            val rig = SessionRig()
            rig.link.finishSubscription()
            rig.clock.runCurrent()
            rig.link.acknowledge(0)
            rig.link.notify(response)
            assertFalse(rig.session.ready)
            assertEquals(1, rig.link.closeCount)
            assertEquals(1, rig.errors.size)
            assertEquals(1, rig.link.writes.size)
        }
    }

    @Test fun rejectedAuthenticationNeverEnablesControls() {
        val rig = SessionRig()
        rig.link.finishSubscription()
        rig.clock.runCurrent()
        rig.link.acknowledge(0)
        rig.link.notify(challenge)
        rig.clock.advanceBy(40)
        rig.link.acknowledge(1)
        rig.link.notify(responseFrame(1, 67, 67, 72, 73, 80, 1))
        assertFalse(rig.session.ready)
        assertEquals(1, rig.link.closeCount)
        assertEquals(listOf("Light rejected authentication"), rig.errors)
        assertFalse(rig.session.submit(state))
        assertEquals(2, rig.link.writes.size)
    }

    @Test fun oldSessionNotificationsSubscriptionAndWriteCallbacksCannotAuthenticateReplacement() {
        val rig = SessionRig()
        rig.link.finishSubscription()
        rig.clock.runCurrent()
        val staleWrite = rig.link.writes.single().complete
        val replacement = FakeLink(rig.clock)
        rig.session.attach(replacement)
        assertEquals(1, rig.link.closeCount)
        rig.link.notify(challenge)
        staleWrite(true)
        rig.link.finishSubscription()
        rig.link.notify(accepted)
        rig.clock.advanceBy(100)
        assertFalse(rig.session.ready)
        assertTrue(replacement.writes.isEmpty())
        rig.authenticate(replacement)
        rig.link.notify(challenge)
        staleWrite(false)
        assertTrue(rig.session.ready)
        assertEquals(0, replacement.closeCount)
        assertTrue(rig.errors.isEmpty())
    }

    @Test fun lightingIsSerializedAndSpacedAtLeastFortyMilliseconds() {
        val rig = SessionRig()
        rig.authenticate()
        assertTrue(rig.session.submit(state))
        rig.clock.advanceBy(39)
        assertEquals(2, rig.link.writes.size)
        rig.clock.advanceBy(1)
        assertEquals(3, rig.link.writes.size)
        rig.clock.advanceBy(500)
        assertEquals("No second lighting write before the first ACK", 3, rig.link.writes.size)
        rig.drain()
        assertEquals(QcProtocol.lighting(state, Change.APPEARANCE).map { it.hex() }, rig.lightingHex())
        rig.link.writes.zipWithNext().forEach { (first, second) -> assertTrue(second.at - first.at >= 40) }
        assertEquals(0, rig.session.queuedActions)
    }

    @Test fun rapidBrightnessChangesRemainBoundedAndKeepOnlyLatestWaitingValue() {
        val rig = SessionRig()
        rig.authenticate()
        rig.session.submit(state.copy(brightness = 1), Change.BRIGHTNESS)
        rig.clock.advanceBy(40)
        for (value in 2..100) {
            assertTrue(rig.session.submit(state.copy(brightness = value), Change.BRIGHTNESS))
            assertTrue(rig.session.queuedActions <= 2)
        }
        rig.drain()
        assertEquals(listOf(QcProtocol.frame(0x11, 1), QcProtocol.frame(0x13, 1),
            QcProtocol.frame(0x11, 1), QcProtocol.frame(0x13, 100)).map { it.hex() }, rig.lightingHex())
        assertTrue(rig.errors.isEmpty())
    }

    @Test fun rapidWheelFramesSendCurrentInFlightColorThenOnlyLatestColor() {
        val rig = SessionRig()
        rig.authenticate()
        rig.session.submit(state.copy(effect = Effect.FULL_WHEEL, color = 1), Change.FRAME)
        rig.clock.advanceBy(40)
        for (color in 2..200) rig.session.submit(state.copy(effect = Effect.FULL_WHEEL, color = color), Change.FRAME)
        assertTrue(rig.session.queuedActions <= 2)
        rig.drain()
        assertEquals(listOf(QcProtocol.frame(0x15, 0, 0, 1, 0).hex(),
            QcProtocol.frame(0x15, 0, 0, 200, 0).hex()), rig.lightingHex())
    }

    @Test fun switchingFromMusicToEveryNonMusicModeOrOffStopsBothMusicControlsFirst() {
        Effect.values().filter { !it.isMusic }.forEach { effect ->
            val rig = SessionRig()
            rig.authenticate()
            rig.session.submit(state.copy(effect = Effect.MUSIC2))
            rig.drain()
            val start = rig.link.writes.size
            rig.session.submit(state.copy(effect = effect))
            rig.drain()
            assertEquals(effect.name, listOf(QcProtocol.frame(0x21, 0).hex(), QcProtocol.frame(0x1a, 0).hex()),
                rig.link.writes.drop(start).take(2).map { it.bytes.hex() })
        }
        val rig = SessionRig()
        rig.authenticate()
        rig.session.submit(state.copy(effect = Effect.MUSIC0))
        rig.drain()
        val start = rig.link.writes.size
        rig.session.submit(state.copy(effect = Effect.MUSIC0, on = false), Change.OFF)
        rig.drain()
        assertEquals(listOf(QcProtocol.frame(0x21, 0), QcProtocol.frame(0x1a, 0), QcProtocol.frame(0x11, 0))
            .map { it.hex() }, rig.link.writes.drop(start).map { it.bytes.hex() })
    }

    @Test fun brightnessKeepsSelectedMusicPresetOrWheelButRestoresModeAfterOff() {
        listOf(Effect.MUSIC1, Effect.FADE3, Effect.FULL_WHEEL).forEach { effect ->
            val rig = SessionRig()
            rig.authenticate()
            val selected = state.copy(effect = effect)
            rig.session.submit(selected)
            rig.drain()
            var start = rig.link.writes.size
            rig.session.submit(selected.copy(brightness = 35), Change.BRIGHTNESS)
            rig.drain()
            assertEquals(effect.name, listOf(QcProtocol.frame(0x11, 1).hex(), QcProtocol.frame(0x13, 35).hex()),
                rig.link.writes.drop(start).map { it.bytes.hex() })
            rig.session.submit(selected.copy(on = false), Change.OFF)
            rig.drain()
            start = rig.link.writes.size
            rig.session.submit(selected.copy(brightness = 60), Change.BRIGHTNESS)
            rig.drain()
            assertEquals(effect.name, QcProtocol.lighting(selected.copy(brightness = 60), Change.APPEARANCE).map { it.hex() },
                rig.link.writes.drop(start).map { it.bytes.hex() })
        }
    }

    @Test fun brightnessRestoresSelectedModeAfterZeroBrightness() {
        val rig = SessionRig()
        rig.authenticate()
        val selected = state.copy(effect = Effect.MUSIC3)
        rig.session.submit(selected.copy(brightness = 0), Change.BRIGHTNESS)
        rig.drain()
        val start = rig.link.writes.size
        rig.session.submit(selected, Change.BRIGHTNESS)
        rig.drain()
        assertEquals(QcProtocol.lighting(selected, Change.APPEARANCE).map { it.hex() },
            rig.link.writes.drop(start).map { it.bytes.hex() })
    }

    @Test fun cancelDuringPacingDelayThenNewIntentDropsEveryOldFrame() {
        val rig = SessionRig()
        rig.authenticate()
        rig.session.submit(state)
        rig.clock.advanceBy(10)
        rig.session.cancelLighting()
        rig.session.submit(state.copy(effect = Effect.MUSIC1))
        rig.drain()
        assertEquals(QcProtocol.lighting(state.copy(effect = Effect.MUSIC1), Change.APPEARANCE).map { it.hex() }, rig.lightingHex())
        rig.link.writes.zipWithNext().forEach { (first, second) -> assertTrue(second.at - first.at >= 40) }
    }

    @Test fun cancelDuringInFlightWriteWaitsForAckAndDropsRemainingOldFrames() {
        val rig = SessionRig()
        rig.authenticate()
        rig.session.submit(state)
        rig.clock.advanceBy(40)
        assertEquals(QcProtocol.frame(0x21, 0).hex(), rig.link.writes.last().bytes.hex())
        rig.session.cancelLighting()
        val latest = state.copy(effect = Effect.MUSIC2)
        rig.session.submit(latest)
        rig.clock.advanceBy(100)
        assertEquals(3, rig.link.writes.size)
        rig.drain()
        assertEquals(listOf(QcProtocol.frame(0x21, 0).hex()) +
            QcProtocol.lighting(latest, Change.APPEARANCE).map { it.hex() }, rig.lightingHex())
    }

    @Test fun newerAppearanceSupersedesOlderInFlightModeWithoutSendingOldRemainder() {
        val rig = SessionRig()
        rig.authenticate()
        rig.session.submit(state.copy(effect = Effect.MUSIC0))
        rig.clock.advanceBy(40)
        val latest = state.copy(effect = Effect.JUMP3)
        rig.session.submit(latest)
        rig.drain()
        assertEquals(listOf(QcProtocol.frame(0x11, 1).hex()) +
            QcProtocol.lighting(latest, Change.APPEARANCE).map { it.hex() }, rig.lightingHex())
    }

    @Test fun disconnectCancelsPendingDelayAndInFlightRemainder() {
        for (inFlight in listOf(false, true)) {
            val rig = SessionRig()
            rig.authenticate()
            rig.session.submit(state)
            if (inFlight) rig.clock.advanceBy(40)
            val count = rig.link.writes.size
            rig.session.disconnect()
            if (inFlight) rig.link.acknowledge(2)
            rig.clock.advanceBy(10000)
            assertEquals(count, rig.link.writes.size)
            assertEquals(0, rig.session.queuedActions)
            assertEquals(1, rig.link.closeCount)
            assertFalse(rig.session.ready)
            assertTrue(rig.errors.isEmpty())
        }
    }

    @Test fun failedLightingWriteDisconnectsAndDiscardsQueuedCommands() {
        val rig = SessionRig()
        rig.authenticate()
        rig.session.submit(state)
        rig.clock.advanceBy(40)
        rig.session.submit(state.copy(brightness = 10), Change.BRIGHTNESS)
        rig.link.acknowledge(2, false)
        rig.clock.advanceBy(10000)
        assertFalse(rig.session.ready)
        assertEquals(0, rig.session.queuedActions)
        assertEquals(1, rig.link.closeCount)
        assertEquals(3, rig.link.writes.size)
        assertEquals(listOf("The light stopped accepting commands"), rig.errors)
    }
}
