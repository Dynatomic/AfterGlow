// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SavedDescriptorTest {
    private val identity = LightDevice("02:AA:BB:CC:DD:01", "Advertised light")
    private val reference = DeviceDescriptorRef(TransportType.BLE, "daybetter-qc", "P001")
    private class Storage(var value: String? = null) : DeviceListStorage {
        var rejectWrite = false
        var writes = 0
        override fun read() = value
        override fun write(value: String): Boolean {
            if (rejectWrite) return false
            writes++
            this.value = value
            return true
        }
    }
    private fun book(storage: Storage) = SavedDevices(storage) { null }
    private fun versionOne(): String = JSONObject().put("version", 1).put("legacyMigrated", true)
        .put("lastAuthenticatedId", identity.id)
        .put("devices", JSONArray().put(JSONObject().put("id", identity.id.lowercase())
            .put("name", identity.name).put("alias", "My custom alias").put("authenticated", true))).toString()
    private fun record() = SavedDevice(identity.id, identity.name, "My custom alias", true, reference)
    private fun descriptorJson(ref: DeviceDescriptorRef) = JSONObject().put("transport", ref.transport.storageId)
        .put("driver", ref.driverId).put("catalog", ref.modelId)

    @Test fun versionOneMigrationPreservesIdentityAliasAuthenticationAndPointerWithoutGuessingModel() {
        val storage = Storage(versionOne())
        var legacyReads = 0
        val devices = SavedDevices(storage) { legacyReads++; LightDevice("02:00:00:00:00:02", "Old single light") }
        val saved = devices.all().single()
        assertEquals(identity.id, saved.id)
        assertEquals(identity.name, saved.advertisedName)
        assertEquals("My custom alias", saved.alias)
        assertTrue(saved.authenticated)
        assertNull(saved.descriptor)
        assertEquals(saved, devices.lastAuthenticated())
        assertEquals(0, legacyReads)
        assertEquals(1, storage.writes)
        assertEquals(2, JSONObject(storage.value!!).getInt("version"))
        assertNull(devices.loadIssue)
        assertEquals(listOf(saved), book(storage).all())
        assertEquals(1, storage.writes)
    }

    @Test fun rejectedVersionMigrationKeepsOldDataAvailableAndRetriesOnLaterSuccessfulEdit() {
        val old = versionOne()
        val storage = Storage(old).apply { rejectWrite = true }
        val devices = book(storage)
        assertEquals(old, storage.value)
        assertNotNull(devices.loadIssue)
        assertEquals("My custom alias", devices.lastAuthenticated()?.alias)
        assertNull(devices.lastAuthenticated()?.descriptor)
        assertEquals(devices.all(), book(storage).all())
        storage.rejectWrite = false
        devices.rename(identity.id, "Recovered alias")
        assertNull(devices.loadIssue)
        assertEquals(2, JSONObject(storage.value!!).getInt("version"))
        assertEquals("Recovered alias", book(storage).lastAuthenticated()?.alias)
    }

    @Test fun versionTwoRoundTripsEachExactKnownDescriptorAndEveryExistingRecordField() {
        val records = DeviceCatalog.models.mapIndexed { index, model ->
            SavedDevice("02:00:00:00:00:%02X".format(index + 1), "BLE name ${model.id}", "Alias ${model.id}", true, model.reference())
        } + SavedDevice("02:00:00:00:00:20", "Unverified light", "Saved only", false, null)
        val snapshot = DeviceSnapshot(records, records[2].id)
        val text = SavedDeviceCodec.encode(snapshot)
        assertEquals(2, JSONObject(text).getInt("version"))
        assertEquals("bluetooth-le", JSONObject(text).getJSONArray("devices").getJSONObject(0)
            .getJSONObject("descriptor").getString("transport"))
        assertEquals(snapshot, SavedDeviceCodec.decode(text))
        assertEquals(records, book(Storage(text)).all())
        assertFalse(SavedDeviceCodec.requiresMigration(text))
    }

    @Test fun explicitSaveAndOldSingleLightMigrationNeverInferDescriptorFromAdvertisedName() {
        val storage = Storage()
        val devices = book(storage)
        val saved = devices.save(identity.copy(name = "DayBetter QC P001"))
        assertNull(saved.descriptor)
        assertFalse(saved.authenticated)
        assertNull(devices.lastAuthenticated())
        assertNull(book(storage).all().single().descriptor)
        val migrated = SavedDevices(Storage()) { identity.copy(name = "P001 Smart Light") }
        assertTrue(migrated.all().single().authenticated)
        assertNull(migrated.all().single().descriptor)
    }

    @Test fun authenticatedExactDescriptorIsStoredWhileRescanAndRenamePreserveItAndCustomAlias() {
        val storage = Storage()
        val devices = book(storage)
        devices.save(identity)
        devices.rename(identity.id, "My custom alias")
        val authenticated = devices.authenticated(identity, reference)
        assertEquals(reference, authenticated.descriptor)
        assertEquals("My custom alias", authenticated.alias)
        devices.save(identity.copy(name = "Updated advertisement"))
        devices.rename(identity.id, "New alias")
        val reloaded = book(storage).lastAuthenticated()!!
        assertEquals(reference, reloaded.descriptor)
        assertEquals("New alias", reloaded.alias)
        assertEquals("Updated advertisement", reloaded.advertisedName)
        assertTrue(reloaded.authenticated)
        val nextRef = reference.copy(modelId = "P01B")
        assertEquals(nextRef, devices.authenticated(identity, nextRef).descriptor)
        assertEquals(nextRef, book(storage).lastAuthenticated()?.descriptor)
    }

    @Test fun unsupportedOrMalformedDescriptorBecomesUnknownWithoutDiscardingSavedIdentity() {
        val text = SavedDeviceCodec.encode(DeviceSnapshot(listOf(record()), identity.id))
        val unsupported = listOf(
            descriptorJson(reference.copy(transport = TransportType.WIFI)),
            descriptorJson(reference.copy(driverId = "future-driver")),
            descriptorJson(reference.copy(modelId = "future-model")),
            JSONObject().put("transport", "unknown").put("driver", "daybetter-qc").put("catalog", "P001"),
            JSONObject().put("transport", "bluetooth-le"),
            JSONObject().put("transport", "bluetooth-le").put("driver", 1).put("catalog", "P001"),
            "not a descriptor", JSONObject.NULL
        )
        unsupported.forEach { value ->
            val root = JSONObject(text)
            root.getJSONArray("devices").getJSONObject(0).put("descriptor", value)
            val decoded = SavedDeviceCodec.decode(root.toString())!!
            assertEquals(listOf(record().copy(descriptor = null)), decoded.records)
            assertEquals(identity.id, decoded.lastAuthenticatedId)
        }
        val devices = book(Storage())
        val saved = devices.authenticated(identity, reference.copy(transport = TransportType.WIFI))
        assertTrue(saved.authenticated)
        assertNull(saved.descriptor)
        assertEquals(identity.id, devices.lastAuthenticated()?.id)
    }

    @Test fun unverifiedOrVersionOneRecordCannotSmuggleACompatibleDescriptor() {
        val original = record().copy(authenticated = false)
        val text = SavedDeviceCodec.encode(DeviceSnapshot(listOf(original), null))
        assertTrue(JSONObject(text).getJSONArray("devices").getJSONObject(0).isNull("descriptor"))
        val root = JSONObject(text)
        root.getJSONArray("devices").getJSONObject(0).put("descriptor", descriptorJson(reference))
        val unverified = SavedDeviceCodec.decode(root.toString())!!
        assertEquals(listOf(original.copy(descriptor = null)), unverified.records)
        assertNull(unverified.lastAuthenticatedId)
        root.put("version", 1)
        root.getJSONArray("devices").getJSONObject(0).put("authenticated", true)
        assertNull(SavedDeviceCodec.decode(root.toString())!!.records.single().descriptor)
    }

    @Test fun failedDescriptorUpdateKeepsPreviousIdentityAliasReferenceAndPointer() {
        val original = DeviceSnapshot(listOf(record()), identity.id)
        val storage = Storage(SavedDeviceCodec.encode(original))
        val devices = book(storage)
        val before = storage.value
        storage.rejectWrite = true
        assertThrows(DeviceListException::class.java) {
            devices.authenticated(identity.copy(name = "Changed advertisement"), reference.copy(modelId = "P026"))
        }
        assertEquals(before, storage.value)
        assertEquals(original.records, devices.all())
        assertEquals(original.records.single(), devices.lastAuthenticated())
        assertEquals(original.records, book(storage).all())
    }
}
