// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import org.junit.Assert.*
import org.junit.Test

class QcProtocolTest {
    private val state = LightState(color = 0xffff0000.toInt(), brightness = 80)

    @Test fun outgoingFramesMatchKnownProtocolVectors() {
        assertEquals("a0110401b121", QcProtocol.frame(0x11, 1).hex())
        assertEquals("a011040070e1", QcProtocol.frame(0x11, 0).hex())
        assertEquals("a012050100b0f0", QcProtocol.frame(0x12, 1, 0).hex())
        assertEquals("a0130450d11d", QcProtocol.frame(0x13, 80).hex())
        assertEquals("a01507ff0000003c1b", QcProtocol.frame(0x15, 255, 0, 0, 0).hex())
        assertEquals("a000084343484950acca", QcProtocol.frame(0, 67, 67, 72, 73, 80).hex())
    }

    @Test fun frameRejectsValuesOutsideTheWireFormat() {
        assertThrows(IllegalArgumentException::class.java) { QcProtocol.frame(-1) }
        assertThrows(IllegalArgumentException::class.java) { QcProtocol.frame(256) }
        assertThrows(IllegalArgumentException::class.java) { QcProtocol.frame(1, -1) }
        assertThrows(IllegalArgumentException::class.java) { QcProtocol.frame(1, 256) }
        assertThrows(IllegalArgumentException::class.java) { QcProtocol.frame(1, *IntArray(15)) }
        assertEquals(19, QcProtocol.frame(255, *IntArray(14) { 255 }).size)
    }

    @Test fun responseRetainsUnsignedCommandAndPayloadBytes() {
        val parsed = QcProtocol.response(responseFrame(0xfe, 0, 128, 255))
        assertEquals(254, parsed.command)
        assertArrayEquals(byteArrayOf(0, 128.toByte(), 255.toByte()), parsed.payload)
    }

    @Test fun responseRequiresA1MarkerEvenWithAValidChecksum() {
        assertThrows(IllegalArgumentException::class.java) { QcProtocol.response(QcProtocol.frame(1, 0)) }
    }

    @Test fun responseRejectsTruncationOversizeAndInconsistentLength() {
        val valid = responseFrame(1, 0)
        listOf(valid.copyOf(4), valid.copyOf(21), valid.copyOf(valid.size - 1),
            valid.copyOf().also { it[2] = 3 }).forEach {
            assertThrows(IllegalArgumentException::class.java) { QcProtocol.response(it) }
        }
    }

    @Test fun responseRejectsCorruptPayloadAndEitherChecksumByte() {
        val valid = responseFrame(1, 67, 67, 72, 73, 80, 0)
        listOf(3, valid.size - 2, valid.lastIndex).forEach { index ->
            val corrupt = valid.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertThrows(IllegalArgumentException::class.java) { QcProtocol.response(corrupt) }
        }
    }

    @Test fun challengeRequiresExactCchipPrefixAndTransformsBothNonceBytes() {
        assertArrayEquals(intArrayOf(67, 67, 72, 73, 80, 0xdb, 0xcc),
            QcProtocol.challengeReply(byteArrayOf(67, 67, 72, 73, 80, 255.toByte(), 128.toByte())))
        listOf(byteArrayOf(67, 67, 72, 73, 80, 0), byteArrayOf(67, 67, 72, 73, 80, 0, 0, 0),
            byteArrayOf(67, 67, 72, 73, 81, 0, 0)).forEach {
            assertThrows(IllegalArgumentException::class.java) { QcProtocol.challengeReply(it) }
        }
    }

    @Test fun acceptanceRequiresExactPrefixZeroStatusAndLength() {
        val accepted = byteArrayOf(67, 67, 72, 73, 80, 0)
        assertTrue(QcProtocol.accepted(accepted))
        assertFalse(QcProtocol.accepted(accepted.copyOf(5)))
        assertFalse(QcProtocol.accepted(accepted.copyOf(7)))
        assertFalse(QcProtocol.accepted(accepted.copyOf().also { it[0] = 0 }))
        assertFalse(QcProtocol.accepted(accepted.copyOf().also { it[5] = 1 }))
        assertFalse(QcProtocol.accepted(accepted.copyOf().also { it[5] = 255.toByte() }))
    }

    @Test fun everyNonMusicAppearanceStopsRhythmAndMicrophoneBeforeModeOrPower() {
        Effect.values().filter { !it.isMusic }.forEach { effect ->
            val frames = QcProtocol.lighting(state.copy(effect = effect), Change.APPEARANCE)
            assertEquals(effect.name, listOf(QcProtocol.frame(0x21, 0).hex(), QcProtocol.frame(0x1a, 0).hex()),
                frames.take(2).map { it.hex() })
            assertEquals(QcProtocol.frame(0x11, 1).hex(), frames[2].hex())
        }
        val off = QcProtocol.lighting(state.copy(effect = Effect.MUSIC0), Change.OFF)
        assertEquals(listOf(QcProtocol.frame(0x21, 0).hex(), QcProtocol.frame(0x1a, 0).hex(),
            "a011040070e1"), off.map { it.hex() })
    }

    @Test fun brightnessPreservesMusicPresetAndFullWheelWithoutResettingMode() {
        listOf(Effect.MUSIC2, Effect.FADE7, Effect.FULL_WHEEL, Effect.SOLID).forEach { effect ->
            assertEquals(effect.name, listOf("a0110401b121", "a0130450d11d"),
                QcProtocol.lighting(state.copy(effect = effect), Change.BRIGHTNESS).map { it.hex() })
        }
    }

    @Test fun musicAppearanceSetsSubmodeAndEnablesMicrophoneAndRhythm() {
        val frames = QcProtocol.lighting(state.copy(effect = Effect.MUSIC3), Change.APPEARANCE)
        assertEquals(listOf(QcProtocol.frame(0x11, 1), QcProtocol.frame(0x12, 160, 3),
            QcProtocol.frame(0x13, 80), QcProtocol.frame(0x1a, 1), QcProtocol.frame(0x21, 1))
            .map { it.hex() }, frames.map { it.hex() })
    }

    @Test fun presetsUseTheirDocumentedModeAndSpeedInsteadOfStaticColor() {
        val modeIds = mapOf(Effect.FADE3 to 11, Effect.FADE7 to 12, Effect.JUMP3 to 9,
            Effect.JUMP7 to 10, Effect.STROBE7 to 23)
        modeIds.forEach { (effect, modeId) ->
            assertEquals(effect.name, listOf(QcProtocol.frame(0x21, 0), QcProtocol.frame(0x1a, 0),
                QcProtocol.frame(0x11, 1), QcProtocol.frame(0x12, modeId, 255),
                QcProtocol.frame(0x13, 80), QcProtocol.frame(0x16, 64)).map { it.hex() },
                QcProtocol.lighting(state.copy(effect = effect, speed = 64), Change.APPEARANCE).map { it.hex() })
        }
    }

    @Test fun fullWheelTicksOnlySendColorAndSpeedOnlyAffectsPreset() {
        assertEquals(listOf("a01507ff0000003c1b"),
            QcProtocol.lighting(state.copy(effect = Effect.FULL_WHEEL), Change.FRAME).map { it.hex() })
        assertThrows(IllegalArgumentException::class.java) { QcProtocol.lighting(state, Change.FRAME) }
        assertTrue(QcProtocol.lighting(state.copy(effect = Effect.MUSIC0), Change.SPEED).isEmpty())
        assertEquals(QcProtocol.frame(0x16, 75).hex(),
            QcProtocol.lighting(state.copy(effect = Effect.JUMP7, speed = 75), Change.SPEED).single().hex())
    }
}
