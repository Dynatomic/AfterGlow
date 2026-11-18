// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import org.junit.Assert.*
import org.junit.Test

class DeviceCatalogTest {
    private fun uuid(short: String) = "0000$short-0000-1000-8000-00805f9b34fb"
    private val signatures = listOf(
        "P001" to listOf("ff10", "ff12", "ff11"),
        "P01B" to listOf("e01b", "a01b", "f01b"),
        "P026" to listOf("e026", "a026", "f026"),
        "P02C" to listOf("e02c", "a02c", "f02c"),
        "P031" to listOf("e031", "a031", "f031")
    )

    @Test fun catalogResolvesExactlyTheFiveDocumentedQcChannelSignatures() {
        assertEquals(signatures.map { it.first }.toSet(), DeviceCatalog.models.map { it.id }.toSet())
        assertEquals(5, DeviceCatalog.models.size)
        signatures.forEach { (id, parts) ->
            val model = DeviceCatalog.match(uuid(parts[0]), uuid(parts[1]), uuid(parts[2]))!!
            assertEquals(id, model.id)
            assertEquals("daybetter-qc", model.driverId)
            assertEquals(TransportType.BLE, model.transport)
            assertEquals(GattSignature(uuid(parts[0]), uuid(parts[1]), uuid(parts[2])), model.signature)
        }
    }

    @Test fun uuidLetterCaseDoesNotChangeExactChannelIdentity() {
        signatures.forEach { (id, parts) ->
            assertEquals(id, DeviceCatalog.match(uuid(parts[0]).uppercase(), uuid(parts[1]).uppercase(),
                uuid(parts[2]).uppercase())?.id)
        }
    }

    @Test fun changingAnyChannelOrSwappingWriteAndNotifyRefusesCompatibility() {
        signatures.forEach { (_, parts) ->
            val full = parts.map(::uuid)
            for (index in 0..2) {
                val changed = full.toMutableList().also { it[index] = uuid("ffff") }
                assertNull(DeviceCatalog.match(changed[0], changed[1], changed[2]))
            }
            assertNull(DeviceCatalog.match(full[0], full[2], full[1]))
        }
    }

    @Test fun malformedUnknownAndFriendlyNameInputsNeverSelectADriver() {
        val known = signatures.first().second.map(::uuid)
        listOf("", "DayBetter", "Smart Light", "ff10", "not-a-uuid", uuid("1234")).forEach {
            assertNull(DeviceCatalog.match(it, known[1], known[2]))
            assertNull(DeviceCatalog.match(known[0], it, known[2]))
            assertNull(DeviceCatalog.match(known[0], known[1], it))
        }
    }

    @Test fun descriptorResolutionRequiresTransportDriverAndModelTogether() {
        DeviceCatalog.models.forEach { model ->
            val ref = model.reference()
            assertEquals(model, DeviceCatalog.resolve(ref))
            assertNull(DeviceCatalog.resolve(ref.copy(transport = TransportType.WIFI)))
            assertNull(DeviceCatalog.resolve(ref.copy(driverId = "unknown-driver")))
            assertNull(DeviceCatalog.resolve(ref.copy(modelId = "unknown-model")))
        }
    }

    @Test fun onlyBleAndQcAreRegisteredWhileWifiRemainsUnavailable() {
        assertEquals(setOf(TransportType.BLE), TransportRegistry.registered)
        assertEquals("bluetooth-le", TransportType.BLE.storageId)
        assertTrue(TransportRegistry.supports(TransportType.BLE))
        assertFalse(TransportRegistry.supports(TransportType.WIFI))
        assertSame(DaybetterQcDriver, DriverRegistry.resolve("daybetter-qc"))
        listOf("", "wifi", "DAYBETTER-QC", "unknown-driver").forEach { assertNull(DriverRegistry.resolve(it)) }
    }

    @Test fun implementationDocumentationAndLimitedHardwareEvidenceStaySeparate() {
        DeviceCatalog.models.forEach { model ->
            assertEquals(ImplementationEvidence.IMPLEMENTED, model.evidence.implementation)
            assertEquals(DocumentationEvidence.DOCUMENTED, model.evidence.documentation)
            assertEquals(if (model.id == "P001") HardwareEvidence.PARTIAL_REFERENCE else HardwareEvidence.NOT_VERIFIED,
                model.evidence.hardware)
            assertNotEquals(HardwareEvidence.VERIFIED, model.evidence.hardware)
        }
        val partial = DeviceCatalog.models.single { it.id == "P001" }.evidence
        val notes = partial.hardwareNotes!!
        assertTrue(notes.contains("one steady blue"))
        assertTrue(notes.contains("effects are not hardware verified"))
    }

    @Test fun capabilitiesGateEachControlAndDoNotInferMusicFromColorSupport() {
        val all = DeviceCapabilities(true, true, true, true, true, true, true)
        DeviceCatalog.models.forEach { assertEquals(all, it.capabilities) }
        val none = DeviceCapabilities(false, false, false, false, false, false, false)
        val state = LightState()
        assertFalse(none.supports(state.copy(on = false), Change.OFF))
        assertTrue(none.copy(power = true).supports(state.copy(on = false), Change.OFF))
        assertFalse(none.supports(state, Change.BRIGHTNESS))
        assertTrue(none.copy(brightness = true).supports(state, Change.BRIGHTNESS))
        assertFalse(none.supports(state, Change.APPEARANCE))
        assertTrue(none.copy(color = true).supports(state, Change.APPEARANCE))
        assertTrue(none.copy(foregroundColorStream = true).supports(state.copy(effect = Effect.FULL_WHEEL), Change.FRAME))
        assertFalse(none.copy(color = true, foregroundColorStream = true).supports(state.copy(effect = Effect.MUSIC0), Change.APPEARANCE))
        assertTrue(none.copy(controllerMicrophone = true).supports(state.copy(effect = Effect.MUSIC0), Change.APPEARANCE))
        assertTrue(none.copy(presets = true).supports(state.copy(effect = Effect.FADE3), Change.APPEARANCE))
        assertFalse(none.copy(presets = true).supports(state.copy(effect = Effect.FADE3), Change.SPEED))
        assertTrue(none.copy(presets = true, speed = true).supports(state.copy(effect = Effect.FADE3), Change.SPEED))
    }
}
