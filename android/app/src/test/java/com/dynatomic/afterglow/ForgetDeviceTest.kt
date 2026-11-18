// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import org.junit.Assert.*
import org.junit.Test

class ForgetDeviceTest {
    private val first = LightDevice("02:00:00:00:00:01", "Desk light")
    private val second = LightDevice("02:00:00:00:00:02", "Shelf light")
    private class Storage(var value: String? = null) : DeviceListStorage {
        var rejectWrite = false
        var throwWrite = false
        override fun read() = value
        override fun write(value: String): Boolean {
            if (throwWrite) error("storage unavailable")
            if (rejectWrite) return false
            this.value = value
            return true
        }
    }
    private fun book(storage: Storage = Storage()) = SavedDevices(storage) { null }

    /** Disconnect wiring mirrors the controller: target, attempt, memory and transport end together. */
    private class ActiveRig(val devices: SavedDevices, chosen: LightDevice) {
        val clock = FakeClock()
        val links = mutableListOf<FakeLink>()
        val target = ForegroundReconnect()
        var selected: LightDevice? = chosen
            private set
        var token: Long? = null
            private set
        var disconnects = 0
            private set
        val memory = LightMemory(object : SavedLightStorage {
            override fun read() = devices.lastAuthenticated()?.let { LightDevice(it.id, it.advertisedName) }
            override fun write(light: LightDevice) { devices.authenticated(light) }
            override fun clear() { devices.lastAuthenticated()?.let { devices.forget(it.id) } }
        })
        val attempt = ConnectAttempt(clock) { disconnect() }
        val session = LightSession(clock, { _, ready ->
            if (ready) {
                attempt.authenticated()
                selected?.let { target.authenticated(it) }
                token?.let { memory.authenticated(it) }
            }
        }, {}, authenticationTimeout = { disconnect() }, failure = { disconnect() })
        val action = ForgetDevice(devices, { selected?.id }, { disconnect() })
        init {
            target.setForeground(true)
            attempt.begin()
            open(chosen)
        }
        private fun open(chosen: LightDevice) {
            token = memory.begin(chosen)
            val link = FakeLink(clock)
            links += link
            session.attach(link)
        }
        fun authenticate() {
            val link = links.last()
            link.finishSubscription(); clock.runCurrent(); link.acknowledge(0)
            link.notify(responseFrame(0, 67, 67, 72, 73, 80, 1, 2))
            clock.advanceBy(40); link.acknowledge(1)
            link.notify(responseFrame(1, 67, 67, 72, 73, 80, 0))
        }
        fun requestAuthenticationRetry(): Boolean {
            val chosen = selected ?: return false
            val retry = attempt.retryAuthentication { open(chosen) }
            if (retry) { memory.cancel(); token = null; session.disconnect(false) }
            return retry
        }
        private fun disconnect() {
            disconnects++
            target.stop(); attempt.cancel(); memory.cancel(); token = null; selected = null
            session.disconnect(false)
        }
    }

    @Test fun forgettingActiveIdentityClosesRealSessionAndClearsAllConnectionIntent() {
        val storage = Storage()
        val devices = book(storage)
        val rig = ActiveRig(devices, first)
        val oldToken = rig.token!!
        rig.authenticate()
        assertTrue(rig.session.ready)
        assertEquals(ForegroundReconnect.Phase.ARMED, rig.target.phase)
        rig.action.forget(first.id)
        assertEquals(1, rig.disconnects)
        assertEquals(1, rig.links.single().closeCount)
        assertFalse(rig.session.ready)
        assertFalse(rig.attempt.active)
        assertNull(rig.selected)
        assertNull(rig.target.target)
        assertFalse(rig.memory.authenticated(oldToken))
        assertNull(devices.find(first.id))
        assertNull(book(storage).find(first.id))
        rig.clock.advanceBy(60000)
        assertEquals(1, rig.links.size)
        assertEquals(2, rig.links.single().writes.size)
    }

    @Test fun forgettingDifferentRecordLeavesActiveIdentityAndSessionAlone() {
        val devices = book()
        devices.save(second)
        val rig = ActiveRig(devices, first)
        rig.authenticate()
        rig.action.forget(second.id)
        rig.clock.advanceBy(60000)
        assertNull(devices.find(second.id))
        assertEquals(first.id, devices.lastAuthenticated()?.id)
        assertTrue(rig.session.ready)
        assertEquals(0, rig.disconnects)
        assertEquals(0, rig.links.single().closeCount)
        assertEquals(first, rig.target.target)
        assertEquals(2, rig.links.single().writes.size)
    }

    @Test fun forgettingSelectedIdentityDuringAuthenticationInvalidatesLateReadyAndMemoryToken() {
        val storage = Storage()
        val devices = book(storage)
        devices.save(first)
        val rig = ActiveRig(devices, first)
        val oldToken = rig.token!!
        val link = rig.links.single()
        link.finishSubscription(); rig.clock.runCurrent()
        val staleWrite = link.writes.single().complete
        assertTrue(rig.attempt.active)
        rig.action.forget(first.id)
        staleWrite(true)
        link.notify(responseFrame(0, 67, 67, 72, 73, 80, 1, 2))
        link.notify(responseFrame(1, 67, 67, 72, 73, 80, 0))
        rig.clock.advanceBy(60000)
        assertFalse(rig.memory.authenticated(oldToken))
        assertFalse(rig.session.ready)
        assertFalse(rig.attempt.active)
        assertNull(rig.selected)
        assertEquals(1, rig.disconnects)
        assertEquals(1, link.closeCount)
        assertEquals(1, link.writes.size)
        assertTrue(devices.all().isEmpty())
        assertTrue(book(storage).all().isEmpty())
    }

    @Test fun forgettingSelectedIdentityInRetryGapCancelsScheduledFreshLink() {
        val storage = Storage()
        val devices = book(storage)
        devices.save(first)
        val rig = ActiveRig(devices, first)
        val oldToken = rig.token!!
        assertTrue(rig.requestAuthenticationRetry())
        assertEquals(2, rig.attempt.attempts)
        assertNotNull(rig.selected)
        rig.action.forget(first.id)
        rig.clock.runCurrent()
        rig.clock.advanceBy(60000)
        assertEquals(1, rig.links.size)
        assertEquals(1, rig.links.single().closeCount)
        assertEquals(1, rig.disconnects)
        assertFalse(rig.attempt.active)
        assertFalse(rig.memory.authenticated(oldToken))
        assertFalse(rig.session.ready)
        assertNull(rig.selected)
        assertTrue(book(storage).all().isEmpty())
    }

    @Test fun matchingAliasIsNeverMistakenForSelectedDeviceIdentity() {
        val devices = book()
        devices.save(second)
        devices.rename(second.id, first.id)
        val rig = ActiveRig(devices, first)
        rig.authenticate()
        rig.action.forget(second.id)
        assertNull(devices.find(second.id))
        assertNotNull(devices.find(first.id))
        assertTrue(rig.session.ready)
        assertEquals(0, rig.disconnects)
        assertEquals(0, rig.links.single().closeCount)
        rig.action.forget("Desk light")
        assertNotNull(devices.find(first.id))
        assertEquals(0, rig.disconnects)
    }

    @Test fun canonicalCaseMatchesTheSelectedIdentityWhenForgetting() {
        val devices = book()
        val chosen = LightDevice("aa:bb:cc:dd:ee:ff", "Canonical light")
        val rig = ActiveRig(devices, chosen)
        rig.authenticate()
        assertEquals("AA:BB:CC:DD:EE:FF", devices.all().single().id)
        rig.action.forget("aA:bB:cC:dD:eE:fF")
        assertEquals(1, rig.disconnects)
        assertEquals(1, rig.links.single().closeCount)
        assertFalse(rig.session.ready)
        assertTrue(devices.all().isEmpty())
    }

    @Test fun failedStorageWriteDoesNotDisconnectOrClaimRecordWasForgotten() {
        for (throws in listOf(false, true)) {
            val storage = Storage()
            val devices = book(storage)
            val rig = ActiveRig(devices, first)
            rig.authenticate()
            val before = storage.value
            storage.throwWrite = throws
            storage.rejectWrite = !throws
            assertThrows(DeviceListException::class.java) { rig.action.forget(first.id) }
            assertEquals(before, storage.value)
            assertNotNull(devices.find(first.id))
            assertNotNull(book(storage).find(first.id))
            assertEquals(0, rig.disconnects)
            assertEquals(0, rig.links.single().closeCount)
            assertTrue(rig.session.ready)
            assertEquals(first, rig.target.target)
            assertNotNull(rig.selected)
        }
    }

    @Test fun forgettingMigratedRecordPersistsTombstoneSoLegacyCannotReturnOnReload() {
        val storage = Storage()
        var legacyReads = 0
        val devices = SavedDevices(storage) { legacyReads++; first }
        var disconnects = 0
        val action = ForgetDevice(devices, { first.id }, { disconnects++ })
        action.forget(first.id)
        assertEquals(1, disconnects)
        repeat(3) {
            val reloaded = SavedDevices(storage) { legacyReads++; first }
            assertTrue(reloaded.all().isEmpty())
            assertNull(reloaded.lastAuthenticated())
            assertNull(reloaded.loadIssue)
        }
        assertEquals(1, legacyReads)
    }
}
