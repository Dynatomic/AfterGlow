// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SavedDevicesTest {
    // Synthetic identities, never physical device addresses.
    private val first = LightDevice("02:00:00:00:00:01", "Desk light")
    private val second = LightDevice("02:00:00:00:00:02", "Shelf light")
    private class Storage(var value: String? = null) : DeviceListStorage {
        var writes = 0
        var rejectWrite = false
        var throwWrite = false
        var throwRead = false
        override fun read(): String? { if (throwRead) error("read unavailable"); return value }
        override fun write(value: String): Boolean {
            if (throwWrite) error("write unavailable")
            if (rejectWrite) return false
            writes++
            this.value = value
            return true
        }
    }
    private fun book(storage: Storage = Storage()) = SavedDevices(storage) { null }
    private fun oneRecord() = SavedDevice(first.id, first.name, "My desk", true)
    private fun validJson() = SavedDeviceCodec.encode(DeviceSnapshot(listOf(oneRecord()), first.id))
    private fun assertRejectedWithoutMigration(raw: String) {
        val storage = Storage(raw)
        var legacyReads = 0
        val devices = SavedDevices(storage) { legacyReads++; first }
        assertTrue(devices.all().isEmpty())
        assertNull(devices.lastAuthenticated())
        assertNotNull(devices.loadIssue)
        assertEquals(0, legacyReads)
        assertEquals(0, storage.writes)
        assertEquals(raw, storage.value)
    }

    @Test fun legacyIdentityMigratesOnceAsVerifiedWithNameAndAliasPreserved() {
        val storage = Storage()
        val old = first.copy(id = first.id.lowercase(), name = "Old bedroom light")
        var legacyReads = 0
        val devices = SavedDevices(storage) { legacyReads++; old }
        val saved = devices.all().single()
        assertEquals(SavedDevice(first.id, old.name, old.name, true), saved)
        assertEquals(saved, devices.lastAuthenticated())
        assertEquals(1, storage.writes)
        assertNull(devices.loadIssue)
        val reloaded = SavedDevices(storage) { legacyReads++; second }
        assertEquals(listOf(saved), reloaded.all())
        assertEquals(1, legacyReads)
        assertEquals(1, storage.writes)
    }

    @Test fun forgettingMigratedIdentityAndReloadingNeverResurrectsLegacyRecord() {
        val storage = Storage()
        var legacyReads = 0
        val devices = SavedDevices(storage) { legacyReads++; first }
        devices.forget(first.id)
        assertTrue(devices.all().isEmpty())
        assertNull(devices.lastAuthenticated())
        repeat(2) {
            val reloaded = SavedDevices(storage) { legacyReads++; first }
            assertTrue(reloaded.all().isEmpty())
            assertNull(reloaded.lastAuthenticated())
            assertNull(reloaded.loadIssue)
        }
        assertEquals(1, legacyReads)
    }

    @Test fun canonicalIdentityDeduplicatesSavesAndDecodedRecords() {
        val devices = book()
        val lower = LightDevice("aa:bb:cc:dd:ee:ff", "Original")
        devices.save(lower)
        devices.rename(lower.id, "Custom alias")
        devices.save(lower.copy(id = lower.id.uppercase(), name = "Updated advertisement"))
        assertEquals(1, devices.all().size)
        assertEquals("AA:BB:CC:DD:EE:FF", devices.all().single().id)
        assertEquals("Custom alias", devices.find(lower.id)?.alias)
        assertEquals("Updated advertisement", devices.find(lower.id.uppercase())?.advertisedName)
        val record = devices.all().single()
        val decoded = SavedDeviceCodec.decode(SavedDeviceCodec.encode(DeviceSnapshot(
            listOf(record, record.copy(id = record.id.lowercase(), alias = "Duplicate")), null)))!!
        assertEquals(listOf(record), decoded.records)
    }

    @Test fun fullBookRejectsNewIdentityButAllowsRenamingAndUpdatingExistingIdentity() {
        val storage = Storage()
        val devices = book(storage)
        for (index in 1..32) devices.save(LightDevice("02:00:00:00:00:%02X".format(index), "Light $index"))
        val before = storage.value
        assertThrows(DeviceListException::class.java) { devices.save(LightDevice("02:00:00:00:00:40", "Extra")) }
        assertEquals(before, storage.value)
        assertEquals(32, devices.all().size)
        devices.rename(first.id, "Renamed at capacity")
        devices.authenticated(first.copy(name = "New advertisement"))
        assertEquals(32, devices.all().size)
        assertEquals("Renamed at capacity", devices.find(first.id)?.alias)
        assertEquals("New advertisement", devices.lastAuthenticated()?.advertisedName)
        assertTrue(devices.lastAuthenticated()!!.authenticated)
    }

    @Test fun explicitSaveCreatesUnverifiedRecordWithoutLastAuthenticatedPointer() {
        val storage = Storage()
        val devices = book(storage)
        val saved = devices.save(first)
        assertFalse(saved.authenticated)
        assertEquals(first.name, saved.alias)
        assertNull(devices.lastAuthenticated())
        val reloaded = book(storage)
        assertEquals(listOf(saved), reloaded.all())
        assertNull(reloaded.lastAuthenticated())
    }

    @Test fun authenticationVerifiesRecordMovesPointerAndPreservesCustomAlias() {
        val storage = Storage()
        val devices = book(storage)
        devices.authenticated(first)
        devices.save(second)
        devices.rename(second.id, "My shelf")
        val result = devices.authenticated(second.copy(name = "Updated BLE name"))
        assertTrue(result.authenticated)
        assertEquals("My shelf", result.alias)
        assertEquals("Updated BLE name", result.advertisedName)
        assertEquals(result, devices.lastAuthenticated())
        assertTrue(devices.find(first.id)!!.authenticated)
        assertEquals(result, book(storage).lastAuthenticated())
    }

    @Test fun savingPreviouslyVerifiedIdentityDoesNotDowngradeItOrChangeLastPointer() {
        val devices = book()
        devices.authenticated(first)
        devices.authenticated(second)
        devices.save(first.copy(name = "Fresh scan name"))
        assertTrue(devices.find(first.id)!!.authenticated)
        assertEquals(second.id, devices.lastAuthenticated()?.id)
        assertEquals(first.name, devices.find(first.id)?.alias)
    }

    private class BookAuthRig(val devices: SavedDevices, chosen: LightDevice) {
        val clock = FakeClock()
        val link = FakeLink(clock)
        val memory = LightMemory(object : SavedLightStorage {
            override fun read() = devices.lastAuthenticated()?.let { LightDevice(it.id, it.advertisedName) }
            override fun write(light: LightDevice) { devices.authenticated(light) }
            override fun clear() { devices.lastAuthenticated()?.let { devices.forget(it.id) } }
        })
        val attempt = memory.begin(chosen)!!
        val session = LightSession(clock, { _, ready -> if (ready) memory.authenticated(attempt) }, {})
        init { session.attach(link) }
        fun authenticate(accept: Boolean = true) {
            link.finishSubscription(); clock.runCurrent(); link.acknowledge(0)
            link.notify(responseFrame(0, 67, 67, 72, 73, 80, 1, 2))
            clock.advanceBy(40); link.acknowledge(1)
            link.notify(responseFrame(1, 67, 67, 72, 73, 80, if (accept) 0 else 1))
        }
    }

    @Test fun realSessionAuthenticationCommitsOnlyVerifiedIdentityAndNoLighting() {
        val devices = book()
        devices.authenticated(first)
        devices.save(second)
        val rig = BookAuthRig(devices, second)
        assertEquals(first.id, devices.lastAuthenticated()?.id)
        assertFalse(devices.find(second.id)!!.authenticated)
        rig.authenticate()
        assertTrue(rig.session.ready)
        assertTrue(devices.find(second.id)!!.authenticated)
        assertEquals(second.id, devices.lastAuthenticated()?.id)
        assertEquals(listOf(0, 1), rig.link.writes.map { it.bytes[1].toInt() and 255 })
    }

    @Test fun rejectedSessionAuthenticationPreservesSavedRecordsAndLastGoodIdentity() {
        val storage = Storage()
        val devices = book(storage)
        devices.authenticated(first)
        devices.save(second)
        devices.rename(second.id, "Keep this alias")
        val before = storage.value
        val rig = BookAuthRig(devices, second)
        rig.authenticate(false)
        assertFalse(rig.session.ready)
        assertEquals(before, storage.value)
        assertFalse(devices.find(second.id)!!.authenticated)
        assertEquals(first.id, devices.lastAuthenticated()?.id)
    }

    @Test fun canceledAuthenticationAndLateCallbacksDoNotAlterBook() {
        val storage = Storage()
        val devices = book(storage)
        devices.authenticated(first)
        devices.save(second)
        val before = storage.value
        val rig = BookAuthRig(devices, second)
        rig.link.finishSubscription(); rig.clock.runCurrent()
        val lateAck = rig.link.writes.single().complete
        rig.memory.cancel()
        rig.session.disconnect()
        lateAck(true)
        rig.link.notify(responseFrame(0, 67, 67, 72, 73, 80, 1, 2))
        rig.clock.advanceBy(6000)
        assertFalse(rig.memory.authenticated(rig.attempt))
        assertEquals(before, storage.value)
        assertEquals(first.id, devices.lastAuthenticated()?.id)
    }

    @Test fun savingBookIdentityWithoutSelectingItDoesNotSendBleCommands() {
        val rig = SessionRig()
        rig.authenticate()
        val devices = book()
        devices.save(second)
        rig.clock.advanceBy(6000)
        assertTrue(rig.session.ready)
        assertEquals(2, rig.link.writes.size)
        assertEquals(0, rig.session.queuedActions)
        assertNull(devices.lastAuthenticated())
    }

    @Test fun aliasesTrimAndAcceptOneThroughFortyCharactersWithoutChangingBleName() {
        val storage = Storage()
        val devices = book(storage)
        devices.save(first)
        devices.rename(first.id, "  X  ")
        assertEquals("X", devices.find(first.id)?.alias)
        devices.rename(first.id, "z".repeat(40))
        assertEquals("z".repeat(40), book(storage).find(first.id)?.alias)
        assertEquals(first.name, devices.find(first.id)?.advertisedName)
    }

    @Test fun emptyLongOrControlCharacterAliasIsRejectedWithoutChangingRecord() {
        val storage = Storage()
        val devices = book(storage)
        devices.save(first)
        val before = storage.value
        listOf("", "   ", "x".repeat(41), "two\nlines", "tab\tname", "nul\u0000name", "del\u007fname").forEach { alias ->
            assertThrows(DeviceListException::class.java) { devices.rename(first.id, alias) }
            assertEquals(before, storage.value)
            assertEquals(first.name, devices.find(first.id)?.advertisedName)
            assertEquals(first.name, devices.find(first.id)?.alias)
        }
    }

    @Test fun forgettingCurrentRecordNeverClosesOrWritesToActiveSession() {
        val rig = SessionRig()
        rig.authenticate()
        val devices = book()
        devices.authenticated(first)
        devices.forget(first.id)
        rig.clock.advanceBy(6000)
        assertTrue(devices.all().isEmpty())
        assertNull(devices.lastAuthenticated())
        assertEquals(0, rig.link.closeCount)
        assertEquals(2, rig.link.writes.size)
        assertTrue(rig.session.ready)
    }

    @Test fun codecRoundTripsMultipleRecordsAndEveryStoredField() {
        val snapshot = DeviceSnapshot(listOf(oneRecord(),
            SavedDevice(second.id, "A quoted \"BLE\" name", "Shelf ☀", false)), first.id, true)
        val text = SavedDeviceCodec.encode(snapshot)
        assertEquals(snapshot, SavedDeviceCodec.decode(text))
        assertEquals(snapshot.records, book(Storage(text)).all())
        assertEquals(snapshot.records.first(), book(Storage(text)).lastAuthenticated())
        val invalidPointer = SavedDeviceCodec.decode(SavedDeviceCodec.encode(snapshot.copy(lastAuthenticatedId = second.id)))!!
        assertNull(invalidPointer.lastAuthenticatedId)
        assertEquals(snapshot.records, invalidPointer.records)
    }

    @Test fun malformedVersionAndRootSchemaFailWithoutLegacyResurrection() {
        val variants = mutableListOf("", "not json", "[]", "null", "{")
        listOf("version", "legacyMigrated", "devices").forEach { missing ->
            variants += JSONObject(validJson()).also { it.remove(missing) }.toString()
        }
        listOf(99, "1", 1.5, true).forEach { version ->
            variants += JSONObject(validJson()).put("version", version).toString()
        }
        variants += JSONObject(validJson()).put("legacyMigrated", false).toString()
        variants += JSONObject(validJson()).put("legacyMigrated", "true").toString()
        variants += JSONObject(validJson()).put("devices", JSONObject()).toString()
        variants += JSONObject(validJson()).put("lastAuthenticatedId", 7).toString()
        variants.forEach(::assertRejectedWithoutMigration)
    }

    @Test fun oversizedDeeplyNestedAndTrailingGarbageInputFailWithoutLegacyResurrection() {
        listOf(" ".repeat(32769), "[".repeat(1000) + "0" + "]".repeat(1000),
            validJson() + " trailing garbage", validJson() + "{}").forEach(::assertRejectedWithoutMigration)
        // Brackets within a legitimate quoted advertisement are plain text.
        val brackets = oneRecord().copy(advertisedName = "[[[[[[[[[[[[light]]]]]]]]]]]]")
        assertEquals(listOf(brackets), SavedDeviceCodec.decode(SavedDeviceCodec.encode(DeviceSnapshot(listOf(brackets), first.id)))?.records)
    }

    @Test fun partialRecordsInvalidFieldTypesAndStoredBoundsFailWithoutPartialLoad() {
        val variants = mutableListOf<String>()
        listOf("id", "name", "alias", "authenticated").forEach { missing ->
            val root = JSONObject(validJson())
            root.getJSONArray("devices").getJSONObject(0).remove(missing)
            variants += root.toString()
        }
        val invalidFields = listOf("id" to "bad address", "name" to "n".repeat(81), "alias" to "",
            "alias" to "a".repeat(41), "alias" to "two\nlines", "authenticated" to "true", "name" to 7)
        invalidFields.forEach { (key, value) ->
            val root = JSONObject(validJson())
            root.getJSONArray("devices").getJSONObject(0).put(key, value)
            variants += root.toString()
        }
        val tooMany = JSONArray()
        repeat(33) { tooMany.put(JSONObject(validJson()).getJSONArray("devices").getJSONObject(0)) }
        variants += JSONObject(validJson()).put("devices", tooMany).toString()
        variants += JSONObject(validJson()).put("devices", JSONArray().put(3)).toString()
        // A valid first record must not leak through when a later record is invalid.
        variants += JSONObject(validJson()).also { it.getJSONArray("devices").put(JSONObject().put("id", second.id)) }.toString()
        variants.forEach(::assertRejectedWithoutMigration)
    }

    @Test fun storageReadExceptionLoadsSafelyWithoutTryingLegacyOrWriting() {
        val storage = Storage(validJson()).apply { throwRead = true }
        var legacyReads = 0
        val devices = SavedDevices(storage) { legacyReads++; second }
        assertTrue(devices.all().isEmpty())
        assertNull(devices.lastAuthenticated())
        assertNotNull(devices.loadIssue)
        assertEquals(0, legacyReads)
        assertEquals(0, storage.writes)
        assertEquals(validJson(), storage.value)
    }

    @Test fun rejectedOrThrowingWritesPreserveRecordsPointerAndPersistedData() {
        for (throws in listOf(false, true)) {
            val storage = Storage(validJson())
            val devices = book(storage)
            val beforeRecords = devices.all()
            val beforePointer = devices.lastAuthenticated()
            val beforeText = storage.value
            storage.throwWrite = throws
            storage.rejectWrite = !throws
            val operations: List<() -> Unit> = listOf(
                { devices.save(second) }, { devices.authenticated(second) },
                { devices.rename(first.id, "New alias") }, { devices.forget(first.id) })
            operations.forEach { operation ->
                assertThrows(DeviceListException::class.java) { operation() }
                assertEquals(beforeRecords, devices.all())
                assertEquals(beforePointer, devices.lastAuthenticated())
                assertEquals(beforeText, storage.value)
            }
        }
    }

    @Test fun failedMigrationWriteKeepsLegacyAvailableAndRetainsItsSource() {
        for (throws in listOf(false, true)) {
            val storage = Storage().apply { throwWrite = throws; rejectWrite = !throws }
            var legacyReads = 0
            val devices = SavedDevices(storage) { legacyReads++; first }
            assertEquals(first.id, devices.lastAuthenticated()?.id)
            assertTrue(devices.all().single().authenticated)
            assertNotNull(devices.loadIssue)
            assertNull(storage.value)
            val retried = SavedDevices(storage) { legacyReads++; first }
            assertEquals(devices.all(), retried.all())
            assertEquals(2, legacyReads)
            assertNull(storage.value)
            storage.throwWrite = false
            storage.rejectWrite = false
            devices.rename(first.id, "Recovered alias")
            assertNull(devices.loadIssue)
            assertEquals("Recovered alias", book(storage).lastAuthenticated()?.alias)
        }
    }

    @Test fun invalidIdentitiesCannotSaveAuthenticateRenameOrResolveRecords() {
        val storage = Storage()
        val devices = book(storage)
        devices.save(first)
        val before = storage.value
        listOf("", "name instead of id", "02:00:00:00:00", "02:00:00:00:00:GG", first.id + "\n", " " + first.id).forEach { id ->
            assertThrows(DeviceListException::class.java) { devices.save(LightDevice(id, "Name")) }
            assertThrows(DeviceListException::class.java) { devices.authenticated(LightDevice(id, "Name")) }
            assertThrows(DeviceListException::class.java) { devices.rename(id, "Alias") }
            assertNull(devices.find(id))
            devices.forget(id)
            assertEquals(before, storage.value)
            assertEquals(1, devices.all().size)
        }
    }

    @Test fun forgettingLastAuthenticatedFallsBackOnlyToVerifiedRemainingRecords() {
        val storage = Storage()
        val devices = book(storage)
        devices.authenticated(first)
        devices.authenticated(second)
        val unverified = LightDevice("02:00:00:00:00:03", "Unverified")
        devices.save(unverified)
        devices.forget(second.id)
        assertEquals(first.id, devices.lastAuthenticated()?.id)
        devices.forget(first.id)
        assertNull(devices.lastAuthenticated())
        assertEquals(listOf(unverified.id), devices.all().map { it.id })
        assertNull(book(storage).lastAuthenticated())
    }
}
