// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import org.junit.Assert.*
import org.junit.Test

class DiscoverySessionTest {
    @Test fun openingAndCancellingDiscoveryPreservesAuthenticatedMusicWithoutAnyWrites(){
        val rig=SessionRig();rig.authenticate();rig.session.submit(LightState(effect=Effect.MUSIC1));rig.drain()
        val written=rig.link.writes.map{it.bytes.hex()};var expired=0
        val discovery=DiscoverySession(rig.clock){expired++}
        discovery.begin();rig.clock.advanceBy(1500);discovery.stop();rig.clock.advanceBy(20000)
        assertTrue(rig.session.ready);assertEquals(0,rig.link.closeCount);assertEquals(written,rig.link.writes.map{it.bytes.hex()});assertEquals(0,expired)
    }
    @Test fun cancelledChooserLeavesFullWheelSessionUsableWithoutResettingItsMode(){
        val rig=SessionRig();rig.authenticate();val wheel=LightState(effect=Effect.FULL_WHEEL)
        rig.session.submit(wheel);rig.drain();val start=rig.link.writes.size
        val discovery=DiscoverySession(rig.clock){};discovery.begin();discovery.stop()
        assertEquals(start,rig.link.writes.size);assertTrue(rig.session.ready);assertEquals(0,rig.link.closeCount)
        rig.session.submit(wheel.copy(color=0xff00ffff.toInt()),Change.FRAME);rig.drain()
        assertEquals(listOf(QcProtocol.frame(0x15,0,255,255,0).hex()),rig.link.writes.drop(start).map{it.bytes.hex()})
    }
    @Test fun lateResultsFromCancelledAndReplacedScansAreIgnored(){
        val clock=FakeClock();val discovery=DiscoverySession(clock){};val results=mutableListOf<String>()
        fun result(token:Long,name:String){if(discovery.owns(token))results+=name}
        val old=discovery.begin();result(old,"first");discovery.stop();result(old,"late")
        val fresh=discovery.begin();result(old,"old");result(fresh,"current")
        assertEquals(listOf("first","current"),results);assertFalse(discovery.owns(old));assertTrue(discovery.owns(fresh))
    }
    @Test fun scanExpiresOnceAfterTenSecondsWithoutClosingCurrentConnection(){
        val rig=SessionRig();rig.authenticate();var expired=0;val discovery=DiscoverySession(rig.clock){expired++}
        val token=discovery.begin();rig.clock.advanceBy(9999);assertTrue(discovery.active)
        rig.clock.advanceBy(1);assertFalse(discovery.active);assertFalse(discovery.owns(token));assertEquals(1,expired)
        rig.clock.advanceBy(30000);assertEquals(1,expired);assertTrue(rig.session.ready);assertEquals(0,rig.link.closeCount);assertEquals(2,rig.link.writes.size)
    }
    @Test fun restartingDiscoveryCancelsThePreviousDeadline(){
        val clock=FakeClock();var expired=0;val discovery=DiscoverySession(clock){expired++}
        discovery.begin();clock.advanceBy(9000);val current=discovery.begin();clock.advanceBy(1000)
        assertTrue(discovery.owns(current));assertEquals(0,expired);clock.advanceBy(9000);assertEquals(1,expired)
    }
    @Test fun repeatedCancelAndDismissAreHarmlessAndCancelTheDeadline(){
        val clock=FakeClock();var expired=0;val discovery=DiscoverySession(clock){expired++}
        discovery.begin();assertTrue(discovery.stop());assertFalse(discovery.stop());assertFalse(discovery.stop())
        clock.advanceBy(30000);assertEquals(0,expired);assertFalse(clock.hasTimers)
    }
    @Test fun actualSelectionClosesOldSessionOnceAndDismissCannotCancelFreshAuthentication(){
        val rig=SessionRig();rig.authenticate();val discovery=DiscoverySession(rig.clock){};discovery.begin()
        // Native controller captures selected device, disconnects old owner, then opens its new link.
        rig.session.disconnect();val selected=FakeLink(rig.clock);rig.session.attach(selected)
        discovery.stop();discovery.stop() // chooser dismiss after selection is discovery-only
        assertEquals(1,rig.link.closeCount);assertEquals(0,selected.closeCount);rig.authenticate(selected)
        assertTrue(rig.session.ready);assertEquals(listOf(0,1),selected.writes.map{it.bytes[1].toInt() and 255});assertEquals(1,rig.link.closeCount)
    }
    @Test fun chooserDismissDoesNotCancelTheSelectedConnectionRetry(){
        val clock=FakeClock();val discovery=DiscoverySession(clock){};val connection=ConnectAttempt(clock){};var reconnects=0
        discovery.begin();connection.begin();connection.retryAuthentication{reconnects++}
        discovery.stop();clock.runCurrent();assertEquals(1,reconnects);assertTrue(connection.active);assertEquals(2,connection.attempts)
        connection.cancel();clock.advanceBy(30000);assertEquals(1,reconnects)
    }
    @Test fun discoveryCancelCannotReplaceOrForgetTheLastAuthenticatedIdentity(){
        // Synthetic locally administered fixture, unrelated to physical hardware.
        var saved:LightDevice?=LightDevice("02:00:00:00:00:01","Saved light");var mutations=0
        val memory=LightMemory(object:SavedLightStorage {
            override fun read()=saved
            override fun write(light:LightDevice){mutations++;saved=light}
            override fun clear(){mutations++;saved=null}
        })
        val clock=FakeClock();val discovery=DiscoverySession(clock){};discovery.begin();discovery.stop()
        assertEquals("Saved light",memory.saved()!!.name);assertEquals(0,mutations)
    }
}
