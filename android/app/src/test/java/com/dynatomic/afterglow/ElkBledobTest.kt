// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import org.junit.Assert.*
import org.junit.Test

class ElkBledobTest {
    private val driver = ElkBledobDriver
    private val state = LightState(color=0xff123456.toInt(),brightness=78,speed=50)
    private fun frames(value:LightState=state,change:Change=Change.APPEARANCE,restore:Boolean=false) = driver.lighting(value,change,restore).map{it.hex()}
    private fun uuid(short:String)="0000$short-0000-1000-8000-00805f9b34fb"
    private class Rig {
        val clock=FakeClock()
        val errors=mutableListOf<String>()
        val statuses=mutableListOf<String>()
        val session=LightSession(clock,{text,_->statuses+=text},{errors+=it})
        val link=FakeLink(clock)
        init {session.attach(link,ElkBledobDriver)}
        fun drain(){repeat(30){
            clock.advanceBy(40)
            val index=link.pendingIndex
            if(index>=0)link.acknowledge(index)
            if(session.queuedActions==0&&!clock.hasTimers)return
        };error("ELK session did not settle")}
    }
    @Test fun exactNameAndNoResponseChannelTogetherAreRequired() {
        assertSame(driver,DriverRegistry.resolve("elk-bledob"))
        val model=DeviceCatalog.match(uuid("fff0"),uuid("fff3"),null,"ELK-BLEDOB",GattWriteMode.WITHOUT_RESPONSE)!!
        assertEquals("ELK-BLEDOB",model.id)
        assertNull(model.signature.notify)
        listOf("", "ELK-BLEDOB-extra", "DayBetter").forEach {
            assertNull(DeviceCatalog.match(uuid("fff0"),uuid("fff3"),null,it,GattWriteMode.WITHOUT_RESPONSE))
        }
        assertNull(DeviceCatalog.match(uuid("fff0"),uuid("fff3"),null,"ELK-BLEDOB"))
        assertNull(DeviceCatalog.match(uuid("fff1"),uuid("fff3"),null,"ELK-BLEDOB",GattWriteMode.WITHOUT_RESPONSE))
        assertNull(DeviceCatalog.match(uuid("fff0"),uuid("fff2"),null,"ELK-BLEDOB",GattWriteMode.WITHOUT_RESPONSE))
        assertFalse(DeviceCatalog.acceptsSaved(model,DeviceCatalog.models.first().reference()))
        assertTrue(DeviceCatalog.acceptsSaved(model,model.reference()))
    }
    @Test fun readyHasNoSubscriptionAuthenticationClockSyncLightingOrReplay() {
        val rig=Rig()
        assertFalse(driver.requiresAuthentication)
        assertNull(driver.beginAuthentication())
        assertTrue(rig.session.ready)
        assertEquals(listOf("Ready"),rig.statuses)
        rig.clock.advanceBy(6000)
        assertTrue(rig.link.events.isEmpty())
        rig.session.submit(state)
        rig.drain()
        val next=FakeLink(rig.clock)
        rig.session.attach(next,driver)
        rig.clock.advanceBy(6000)
        assertEquals(1,rig.link.closeCount)
        assertTrue(next.events.isEmpty())
        assertTrue(rig.session.ready)
    }
    @Test fun staticColorOnBrightnessAndOffMatchDocumentedNineByteFrames() {
        assertEquals(listOf("7e0704ff00010201ef","7e07050312345610ef","7e04014e01ff0201ef"),frames())
        assertEquals(listOf("7e07040000000201ef"),frames(state.copy(on=false),Change.OFF))
        assertEquals(listOf("7e07040000000201ef"),frames(state.copy(brightness=0),Change.BRIGHTNESS))
        assertEquals(listOf("7e070503ff000010ef"),frames(state.copy(color=0xffff0000.toInt(),effect=Effect.FULL_WHEEL),Change.FRAME))
        assertEquals(listOf("7e0705030000ff10ef"),frames(state.copy(color=0xff0000ff.toInt(),effect=Effect.FULL_WHEEL),Change.FRAME))
    }
    @Test fun presetsSpeedAndBrightnessUseElkValuesAndPreserveMode() {
        val ids=mapOf(Effect.JUMP3 to "87",Effect.JUMP7 to "88",Effect.FADE3 to "89",Effect.FADE7 to "8a",Effect.STROBE7 to "95")
        ids.forEach{(effect,id)->assertEquals(listOf("7e0704ff00010201ef","7e0703${id}03ffff00ef","7e070232ffffff00ef","7e04014e01ff0201ef"),frames(state.copy(effect=effect)))}
        assertEquals(listOf("7e070201ffffff00ef"),frames(state.copy(effect=Effect.FADE3,speed=1),Change.SPEED))
        assertEquals(listOf("7e070264ffffff00ef"),frames(state.copy(effect=Effect.FADE3,speed=100),Change.SPEED))
        assertTrue(frames(state,Change.SPEED).isEmpty())
        assertEquals(listOf("7e0704ff00010201ef","7e04016401ff0201ef"),frames(state.copy(brightness=100,effect=Effect.FADE3),Change.BRIGHTNESS))
        assertEquals(listOf("7e0704ff00010201ef","7e04014e01ff0201ef"),frames(state.copy(effect=Effect.FADE3),Change.BRIGHTNESS,true))
    }
    @Test fun invalidValuesAndMicrophoneAreRefusedBeforeSending() {
        val rig=Rig()
        Effect.values().filter{it.isMusic}.forEach{assertFalse(rig.session.submit(state.copy(effect=it)))}
        assertFalse(driver.capabilities.controllerMicrophone)
        rig.clock.advanceBy(1000)
        assertTrue(rig.link.events.isEmpty())
        assertThrows(IllegalArgumentException::class.java){frames(state.copy(brightness=101))}
        assertThrows(IllegalArgumentException::class.java){frames(state.copy(speed=0))}
        assertThrows(IllegalArgumentException::class.java){frames(state.copy(effect=Effect.MUSIC0))}
    }
    @Test fun replacingPresetWithRgbAndOffCancelsOldRemainder() {
        val rig=Rig()
        rig.session.submit(state.copy(effect=Effect.FADE7))
        rig.clock.runCurrent()
        rig.session.submit(state.copy(effect=Effect.FULL_WHEEL))
        rig.link.acknowledge(0)
        rig.drain()
        assertEquals(listOf("7e0704ff00010201ef","7e0704ff00010201ef","7e07050312345610ef","7e04014e01ff0201ef"),rig.link.writes.map{it.bytes.hex()})
        rig.session.submit(state.copy(effect=Effect.FADE3))
        rig.clock.advanceBy(40)
        rig.session.submit(state.copy(on=false),Change.OFF)
        rig.link.acknowledge(4)
        rig.drain()
        assertEquals("7e07040000000201ef",rig.link.writes.last().bytes.hex())
        assertFalse(rig.link.writes.drop(4).any{it.bytes.hex().startsWith("7e0703")})
    }
    @Test fun disconnectAndForegroundCancellationDiscardPacedFramesAndStaleCompletion() {
        val rig=Rig()
        rig.session.submit(state.copy(effect=Effect.FULL_WHEEL))
        rig.clock.runCurrent()
        rig.session.cancelLighting()
        rig.link.acknowledge(0)
        rig.clock.advanceBy(500)
        assertEquals(1,rig.link.writes.size)
        rig.session.submit(state.copy(effect=Effect.FULL_WHEEL),Change.FRAME)
        rig.session.disconnect(false)
        rig.clock.advanceBy(500)
        assertEquals(1,rig.link.writes.size)
        assertFalse(rig.session.ready)
        assertEquals(0,rig.session.queuedActions)
    }
    @Test fun savedDriverIdentityAndAliasRoundTripWithoutGuessingFromName() {
        var raw:String?=null
        val storage=object:DeviceListStorage{override fun read()=raw;override fun write(value:String):Boolean{raw=value;return true}}
        val devices=SavedDevices(storage){null}
        val light=LightDevice("02:AA:BB:CC:DD:12","ELK-BLEDOB")
        assertNull(devices.save(light).descriptor)
        devices.rename(light.id,"Shelf")
        val ref=DeviceCatalog.models.single{it.driverId==driver.id}.reference()
        devices.authenticated(light,ref)
        val next=SavedDevices(storage){null}.lastAuthenticated()!!
        assertEquals(ref,next.descriptor)
        assertEquals("Shelf",next.alias)
        assertEquals(light.id,next.id)
    }
}
