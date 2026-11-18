// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import org.junit.Assert.*
import org.junit.Test

class ForegroundReconnectTest {
    private val identity = LightDevice("02:00:00:00:00:01", "Desk light")
    private fun armed(): ForegroundReconnect = ForegroundReconnect().also {
        it.setForeground(true)
        it.authenticated(identity)
    }

    @Test fun constructionHasNoTargetOrConnectionIntent() {
        val policy = ForegroundReconnect()
        assertFalse(policy.foreground)
        assertNull(policy.target)
        assertEquals(ForegroundReconnect.Phase.DISABLED, policy.phase)
        assertFalse(policy.arrived())
        assertFalse(policy.unexpectedLoss(identity.id))
    }

    @Test fun enteringForegroundAloneNeverArmsOrStartsAConnection() {
        val policy = ForegroundReconnect()
        repeat(3) { assertFalse(policy.setForeground(true)) }
        assertNull(policy.target)
        assertEquals(ForegroundReconnect.Phase.DISABLED, policy.phase)
        assertFalse(policy.unexpectedLoss(identity.id))
        assertFalse(policy.arrived())
    }

    @Test fun authenticationArmsOnlyTheExactCanonicalIdentity() {
        val policy = ForegroundReconnect()
        policy.setForeground(true)
        policy.authenticated(LightDevice("aa:bb:cc:dd:ee:ff", "  Desk\nlight  "))
        assertEquals(LightDevice("AA:BB:CC:DD:EE:FF", "Desk light"), policy.target)
        assertEquals(ForegroundReconnect.Phase.ARMED, policy.phase)
        assertTrue(policy.unexpectedLoss("aA:bB:cC:dD:eE:fF"))
    }

    @Test fun wrongBlankAndMalformedLostIdentitiesNeverOpenWaitingIntent() {
        val policy = armed()
        listOf("02:00:00:00:00:02", "", "Desk light", "02:00:00:00:00", identity.id + "\n").forEach {
            assertFalse(policy.unexpectedLoss(it))
            assertEquals(ForegroundReconnect.Phase.ARMED, policy.phase)
            assertEquals(identity, policy.target)
        }
    }

    @Test fun unexpectedLossCreatesExactlyOneWaitingTransition() {
        val policy = armed()
        assertTrue(policy.unexpectedLoss(identity.id))
        repeat(5) { assertFalse(policy.unexpectedLoss(identity.id)) }
        assertEquals(ForegroundReconnect.Phase.WAITING, policy.phase)
        assertEquals(identity, policy.target)
    }

    @Test fun arrivalCanStartAuthenticationOnlyOnceAfterWaiting() {
        val policy = armed()
        assertFalse(policy.arrived())
        assertTrue(policy.unexpectedLoss(identity.id))
        assertTrue(policy.arrived())
        repeat(5) { assertFalse(policy.arrived()) }
        assertFalse(policy.unexpectedLoss(identity.id))
        assertEquals(ForegroundReconnect.Phase.AUTHENTICATING, policy.phase)
    }

    @Test fun successfulAutomaticAuthenticationRearmsFutureUnexpectedLoss() {
        val policy = armed()
        assertTrue(policy.unexpectedLoss(identity.id))
        assertTrue(policy.arrived())
        policy.authenticated(identity)
        assertEquals(ForegroundReconnect.Phase.ARMED, policy.phase)
        assertTrue(policy.unexpectedLoss(identity.id))
        assertEquals(ForegroundReconnect.Phase.WAITING, policy.phase)
    }

    @Test fun invalidOrBackgroundAuthenticationNeverCreatesATarget() {
        val policy = ForegroundReconnect()
        policy.authenticated(identity)
        assertNull(policy.target)
        policy.setForeground(true)
        listOf("", "not an address", "02:00:00:00:00:GG").forEach {
            policy.authenticated(identity.copy(id = it))
            assertNull(policy.target)
            assertEquals(ForegroundReconnect.Phase.DISABLED, policy.phase)
        }
    }

    @Test fun backgroundClearsArmedTargetAndResumeDoesNotRecreateIt() {
        val policy = armed()
        assertFalse(policy.setForeground(false))
        policy.setForeground(true)
        assertNull(policy.target)
        assertEquals(ForegroundReconnect.Phase.DISABLED, policy.phase)
        assertFalse(policy.unexpectedLoss(identity.id))
        assertFalse(policy.arrived())
    }

    @Test fun backgroundWhileWaitingRequestsCancellationAndResumeDoesNotStart() {
        val policy = armed()
        policy.unexpectedLoss(identity.id)
        assertTrue(policy.setForeground(false))
        assertFalse(policy.setForeground(false))
        policy.setForeground(true)
        assertNull(policy.target)
        assertFalse(policy.arrived())
        assertFalse(policy.unexpectedLoss(identity.id))
    }

    @Test fun backgroundDuringAutomaticAuthenticationRequestsCancellation() {
        val policy = armed()
        policy.unexpectedLoss(identity.id)
        policy.arrived()
        assertTrue(policy.setForeground(false))
        policy.setForeground(true)
        assertNull(policy.target)
        assertEquals(ForegroundReconnect.Phase.DISABLED, policy.phase)
        assertFalse(policy.arrived())
    }

    @Test fun explicitStopOrNewAuthenticatedSelectionInvalidatesOldTarget() {
        val policy = armed()
        policy.unexpectedLoss(identity.id)
        policy.stop()
        assertNull(policy.target)
        assertFalse(policy.arrived())
        assertFalse(policy.unexpectedLoss(identity.id))
        val replacement = LightDevice("02:00:00:00:00:02", "Shelf light")
        policy.authenticated(replacement)
        assertFalse(policy.unexpectedLoss(identity.id))
        assertTrue(policy.unexpectedLoss(replacement.id))
    }

    /** Models the controller's foreground hooks with real session/attempt state machines. */
    private class TransportRig(val identity: LightDevice) {
        val clock = FakeClock()
        val policy = ForegroundReconnect()
        val links = mutableListOf<FakeLink>()
        val errors = mutableListOf<String>()
        var current: FakeLink? = null
            private set
        private var attached = false
        private var automatic = false
        val attempt = ConnectAttempt(clock) { errors += "establishment expired"; stop() }
        val session = LightSession(clock, { _, ready ->
            if (ready) { attempt.authenticated(); policy.authenticated(identity) }
        }, { errors += it }, authenticationTimeout = { errors += it; stop() }, failure = { errors += it; stop() },
            transportLost = { lost() })
        fun manualConnect() {
            stop()
            policy.setForeground(true)
            attempt.begin()
            current = FakeLink(clock).also { links += it }
            attached = true
            session.attach(current!!)
        }
        fun authenticate(accept: Boolean = true) {
            val link = current!!
            link.finishSubscription(); clock.runCurrent(); link.acknowledge(0)
            link.notify(responseFrame(0, 67, 67, 72, 73, 80, 1, 2))
            clock.advanceBy(40); link.acknowledge(1)
            link.notify(responseFrame(1, 67, 67, 72, 73, 80, if (accept) 0 else 1))
        }
        fun lost(): Boolean {
            if (!policy.unexpectedLoss(identity.id)) return false
            session.disconnect(false)
            attached = false
            automatic = true
            current = FakeLink(clock).also { links += it }
            return true
        }
        fun arrived(discoverServices: Boolean = true): Boolean {
            if (!policy.arrived()) return false
            attempt.begin()
            if (discoverServices) { attached = true; session.attach(current!!) }
            return true
        }
        fun foreground(value: Boolean) {
            if (policy.setForeground(value) || (!value && automatic)) stop()
        }
        fun stop() {
            policy.stop(); attempt.cancel(); session.disconnect(false)
            if (!attached) current?.close()
            current = null; attached = false; automatic = false
        }
    }

    @Test fun lossOpensOneFreshWaitingLinkAndArrivalReauthenticatesWithoutLightingReplay() {
        val rig = TransportRig(identity)
        assertTrue(rig.links.isEmpty())
        rig.manualConnect(); rig.authenticate()
        val old = rig.current!!
        rig.session.submit(LightState(effect = Effect.MUSIC2))
        rig.clock.advanceBy(40)
        val staleWrite = old.writes.last().complete
        assertTrue(rig.lost())
        val waiting = rig.current!!
        assertEquals(1, old.closeCount)
        assertTrue(waiting.events.isEmpty())
        repeat(5) { assertFalse(rig.lost()) }
        assertEquals(2, rig.links.size)
        old.notify(responseFrame(1, 67, 67, 72, 73, 80, 0)); staleWrite(true)
        assertFalse(rig.session.ready)
        assertTrue(rig.arrived())
        assertFalse(rig.arrived())
        assertEquals(listOf("subscribe"), waiting.events)
        rig.authenticate()
        rig.clock.advanceBy(6000)
        assertTrue(rig.session.ready)
        assertEquals(ForegroundReconnect.Phase.ARMED, rig.policy.phase)
        assertEquals(listOf(0, 1), waiting.writes.map { it.bytes[1].toInt() and 255 })
        assertEquals(0, rig.session.queuedActions)
        assertTrue(rig.errors.isEmpty())
    }

    @Test fun waitingHasNoThirtySecondTimerButEstablishmentAfterArrivalDoes() {
        val rig = TransportRig(identity)
        rig.manualConnect(); rig.authenticate(); rig.lost()
        val waiting = rig.current!!
        rig.clock.advanceBy(90000)
        assertEquals(ForegroundReconnect.Phase.WAITING, rig.policy.phase)
        assertEquals(0, waiting.closeCount)
        assertFalse(rig.attempt.active)
        assertTrue(waiting.events.isEmpty())
        rig.arrived(discoverServices = false)
        rig.clock.advanceBy(29999)
        assertEquals(ForegroundReconnect.Phase.AUTHENTICATING, rig.policy.phase)
        rig.clock.advanceBy(1)
        assertEquals(ForegroundReconnect.Phase.DISABLED, rig.policy.phase)
        assertEquals(1, waiting.closeCount)
        assertEquals(listOf("establishment expired"), rig.errors)
        assertEquals(2, rig.links.size)
    }

    @Test fun failedColorActionAckBeforeGattLossCreatesOneWaitAndNeverReplaysOldLighting() {
        val rig = TransportRig(identity)
        rig.manualConnect(); rig.authenticate()
        val old = rig.current!!
        rig.session.submit(LightState(effect = Effect.FULL_WHEEL, color = 0xff123456.toInt()))
        rig.clock.advanceBy(40)
        // Let mode setup finish, then fail the actual RGB write with a later color still queued.
        for (index in 2..6) { old.acknowledge(index); rig.clock.advanceBy(40) }
        assertEquals(0x15, old.writes.last().bytes[1].toInt() and 255)
        rig.session.submit(LightState(effect = Effect.FULL_WHEEL, color = 0xffabcdef.toInt()), Change.FRAME)
        val lateCallback = old.writes.last().complete
        old.acknowledge(7, false)
        val waiting = rig.current!!
        assertFalse(rig.session.ready)
        assertEquals(0, rig.session.queuedActions)
        assertEquals(ForegroundReconnect.Phase.WAITING, rig.policy.phase)
        assertEquals(1, old.closeCount)
        assertEquals(2, rig.links.size)
        lateCallback(true)
        assertFalse(rig.lost()) // The later native disconnect must not create another waiting link.
        rig.clock.advanceBy(1000)
        assertEquals(8, old.writes.size)
        assertEquals(2, rig.links.size)
        assertTrue(waiting.events.isEmpty())
        rig.arrived(); rig.authenticate()
        rig.clock.advanceBy(6000)
        assertTrue(rig.session.ready)
        assertEquals(listOf(0, 1), waiting.writes.map { it.bytes[1].toInt() and 255 })
        assertEquals(0, rig.session.queuedActions)
        assertTrue(rig.errors.isEmpty())
    }

    @Test fun rejectedOrMalformedAutomaticAuthenticationStopsIntentWithoutRetry() {
        for (malformed in listOf(false, true)) {
            val rig = TransportRig(identity)
            rig.manualConnect(); rig.authenticate(); rig.lost(); rig.arrived()
            val automatic = rig.current!!
            if (malformed) {
                automatic.finishSubscription(); rig.clock.runCurrent(); automatic.acknowledge(0)
                automatic.notify(byteArrayOf(1, 2, 3))
            } else rig.authenticate(accept = false)
            rig.clock.advanceBy(60000)
            assertEquals(ForegroundReconnect.Phase.DISABLED, rig.policy.phase)
            assertNull(rig.policy.target)
            assertFalse(rig.session.ready)
            assertFalse(rig.attempt.active)
            assertFalse(rig.attempt.retryAuthentication { fail("Automatic failure must not retry") })
            assertEquals(1, automatic.closeCount)
            assertEquals(2, rig.links.size)
            assertEquals(1, rig.errors.size)
        }
    }

    @Test fun automaticAuthenticationTimeoutStopsInsteadOfUsingManualAuthenticationRetry() {
        val rig = TransportRig(identity)
        rig.manualConnect(); rig.authenticate(); rig.lost(); rig.arrived()
        val automatic = rig.current!!
        automatic.finishSubscription(); rig.clock.runCurrent(); automatic.acknowledge(0)
        rig.clock.advanceBy(5000)
        assertEquals(ForegroundReconnect.Phase.DISABLED, rig.policy.phase)
        assertNull(rig.policy.target)
        assertEquals(1, automatic.closeCount)
        assertFalse(rig.attempt.active)
        assertEquals(2, rig.links.size)
        assertEquals(1, rig.errors.size)
        assertTrue(rig.errors.single().contains("Authentication timed out"))
        rig.clock.advanceBy(60000)
        assertEquals(2, rig.links.size)
    }

    @Test fun backgroundCancelsWaitingOrAutomaticHandshakeAndResumeDoesNotReopenLinks() {
        for (duringAuthentication in listOf(false, true)) {
            val rig = TransportRig(identity)
            rig.manualConnect(); rig.authenticate(); rig.lost()
            if (duringAuthentication) rig.arrived()
            val automatic = rig.current!!
            rig.foreground(false)
            rig.foreground(true)
            rig.clock.advanceBy(60000)
            assertEquals(1, automatic.closeCount)
            assertNull(rig.policy.target)
            assertEquals(ForegroundReconnect.Phase.DISABLED, rig.policy.phase)
            assertFalse(rig.session.ready)
            assertFalse(rig.attempt.active)
            assertEquals(2, rig.links.size)
            assertFalse(rig.arrived())
        }
    }
}
