// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import org.junit.Assert.*
import org.junit.Test

class LightMemoryTest {
    // Synthetic locally administered test identities, never a physical device address.
    private val previous=LightDevice("02:00:00:00:00:01","Previous light")
    private val next=LightDevice("02:00:00:00:00:02","Next light")
    private class Storage(var value:LightDevice?=null):SavedLightStorage {
        var writes=0;var failRead=false;var failWrite=false
        override fun read():LightDevice? {if(failRead)error("storage unavailable");return value}
        override fun write(light:LightDevice){if(failWrite)error("storage unavailable");writes++;value=light}
        override fun clear(){value=null}
    }
    @Test fun constructionAndLookupHaveNoConnectionOrWriteSideEffects(){
        val storage=Storage(previous);val memory=LightMemory(storage)
        assertEquals(previous,memory.saved());assertEquals(0,storage.writes)
    }
    @Test fun malformedSavedIdentityIsNotExposedAsAShortcut(){
        for(id in listOf("","Smart Light","02:00:00:00:00","02:00:00:00:00:01\n","02:00:00:00:00:GG")){
            val storage=Storage(LightDevice(id,"name"));val memory=LightMemory(storage)
            assertNull(memory.saved());assertNull(memory.begin(LightDevice(id,"name")));assertEquals(0,storage.writes)
        }
    }
    @Test fun identityIsCanonicalAndNameIsBoundedForOneLine(){
        val result=LightMemory.validated(LightDevice("aa:bb:cc:dd:ee:ff","  Smart\nLight  "))!!
        assertEquals("AA:BB:CC:DD:EE:FF",result.id);assertEquals("Smart Light",result.name)
        assertEquals(80,LightMemory.validated(next.copy(name="x".repeat(200)))!!.name.length)
        assertEquals("Last light",LightMemory.validated(next.copy(name=" \n "))!!.name)
    }
    @Test fun choosingAnotherLightDoesNotReplaceTheLastAuthenticatedOne(){
        val storage=Storage(previous);val memory=LightMemory(storage)
        memory.begin(next);assertEquals(previous,memory.saved());assertEquals(0,storage.writes)
    }
    @Test fun onlySuccessfulAuthenticationPersistsTheChosenIdentity(){
        val storage=Storage(previous);val memory=LightMemory(storage);val token=memory.begin(next)!!
        assertTrue(memory.authenticated(token));assertEquals(next,memory.saved());assertEquals(1,storage.writes)
        assertFalse(memory.authenticated(token));assertEquals(1,storage.writes)
    }
    @Test fun cancellationPreventsLateSuccessFromReplacingSavedLight(){
        val storage=Storage(previous);val memory=LightMemory(storage);val token=memory.begin(next)!!
        memory.cancel();assertFalse(memory.authenticated(token));assertEquals(previous,memory.saved());assertEquals(0,storage.writes)
    }
    @Test fun supersededAttemptCannotSaveInsteadOfTheCurrentAttempt(){
        val storage=Storage(previous);val memory=LightMemory(storage)
        val stale=memory.begin(previous)!!;val current=memory.begin(next)!!
        assertFalse(memory.authenticated(stale));assertTrue(memory.authenticated(current));assertEquals(next,memory.saved())
    }
    @Test fun forgetClearsOnlyMemoryAndRejectsAnOldAuthenticationCallback(){
        val storage=Storage(previous);val memory=LightMemory(storage);val token=memory.begin(next)!!
        memory.forget();assertNull(memory.saved());assertFalse(memory.authenticated(token));assertEquals(0,storage.writes)
    }
    @Test fun readFailureIsAnEmptyShortcutWithoutReplacingStoredIdentity(){
        val storage=Storage(previous).apply{failRead=true};assertNull(LightMemory(storage).saved());assertEquals(previous,storage.value)
    }
    @Test fun failedWriteDoesNotOverwriteThePreviousStoredIdentity(){
        val storage=Storage(previous).apply{failWrite=true};val memory=LightMemory(storage);val token=memory.begin(next)!!
        assertThrows(IllegalStateException::class.java){memory.authenticated(token)}
        assertEquals(previous,memory.saved());assertEquals(0,storage.writes)
    }
    private class AuthRig(val chosen:LightDevice,val initial:LightDevice?) {
        val clock=FakeClock();val link=FakeLink(clock);val storage=Storage(initial);val memory=LightMemory(storage)
        val attempt=memory.begin(chosen)!!
        val session=LightSession(clock,{_,ready->if(ready)memory.authenticated(attempt)},{})
        init{session.attach(link)}
        fun authenticate(accepted:Boolean=true){
            link.finishSubscription();clock.runCurrent();link.acknowledge(0)
            link.notify(responseFrame(0,67,67,72,73,80,1,2));clock.advanceBy(40);link.acknowledge(1)
            link.notify(responseFrame(1,67,67,72,73,80,if(accepted)0 else 1))
        }
    }
    @Test fun actualSessionReadinessSavesIdentityButNeverEmitsLighting(){
        val rig=AuthRig(next,previous)
        assertEquals(previous,rig.memory.saved());assertEquals(0,rig.storage.writes)
        assertFalse(rig.session.submit(LightState(effect=Effect.MUSIC0)))
        rig.authenticate();rig.clock.advanceBy(6000)
        assertTrue(rig.session.ready);assertEquals(next,rig.memory.saved());assertEquals(1,rig.storage.writes)
        assertEquals(listOf(0,1),rig.link.writes.map{it.bytes[1].toInt() and 255});assertEquals(0,rig.session.queuedActions)
    }
    @Test fun rejectedNewDeviceAuthenticationPreservesLastGoodShortcut(){
        val rig=AuthRig(next,previous);rig.authenticate(false)
        assertFalse(rig.session.ready);assertEquals(previous,rig.memory.saved());assertEquals(0,rig.storage.writes)
    }
    @Test fun disconnectAndLateNotificationsCannotSaveAFailedSelection(){
        val rig=AuthRig(next,previous)
        rig.link.finishSubscription();rig.clock.runCurrent();rig.link.acknowledge(0)
        rig.memory.cancel();rig.session.disconnect()
        rig.link.notify(responseFrame(0,67,67,72,73,80,1,2));rig.clock.advanceBy(6000)
        assertFalse(rig.session.ready);assertEquals(previous,rig.memory.saved());assertEquals(0,rig.storage.writes)
    }
}
