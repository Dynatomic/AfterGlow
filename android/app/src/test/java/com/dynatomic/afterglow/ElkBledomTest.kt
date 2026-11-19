// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import org.junit.Assert.*
import org.junit.Test

class ElkBledomTest {
    private val driver=ElkBledomDriver
    private val state=LightState(color=0xff123456.toInt(),brightness=78,speed=50)
    private fun frames(value:LightState=state,change:Change=Change.APPEARANCE,restore:Boolean=false)=driver.lighting(value,change,restore).map{it.hex()}
    private fun uuid(short:String)="0000$short-0000-1000-8000-00805f9b34fb"
    @Test fun similarElkNamesResolveOnlyTheirOwnExactDocumentedDriver() {
        listOf("ELK-BLEDOB","ELK-BLEDOM").forEach { name ->
            listOf(name," $name  ",name.lowercase()).forEach { raw ->
                val mode=if(name.endsWith("B"))GattWriteMode.WITHOUT_RESPONSE else GattWriteMode.ACKNOWLEDGED
                val model=DeviceCatalog.match(uuid("fff0"),uuid("fff3"),null,raw,mode)!!
                assertEquals(name,model.id)
                assertEquals(name.lowercase(),DriverRegistry.resolve(model.driverId)?.id)
            }
        }
        listOf("ELK-BLED","ELK-BLEDOB-extra","ELK-BLEDOM2","ELK-BLEDONG","Shelf").forEach { name ->
            GattWriteMode.values().forEach { assertNull(DeviceCatalog.match(uuid("fff0"),uuid("fff3"),null,name,it)) }
        }
    }
    @Test fun unknownElkCannotFallThroughToQcButUnknownNonElkCanValidateAnExactQcTriplet() {
        val qc=DeviceCatalog.models.first()
        listOf("ELK-BLEDXY","elk-bledom2 ","ELK-BLEDOB","ELK-BLEDOM").forEach {
            assertFalse(DeviceCatalog.matchesName(qc,it))
            assertNull(DeviceCatalog.match(qc.signature.service,qc.signature.write,qc.signature.notify,it))
        }
        assertEquals(qc,DeviceCatalog.match(qc.signature.service,qc.signature.write,qc.signature.notify,"Unknown light"))
    }
    @Test fun writePropertiesChooseOnlyAllowedModesWithoutAssumingAcknowledgement() {
        val b=DeviceCatalog.models.single{it.id=="ELK-BLEDOB"}
        val m=DeviceCatalog.models.single{it.id=="ELK-BLEDOM"}
        val qc=DeviceCatalog.models.first()
        assertNull(DeviceCatalog.writeMode(b,true,false))
        assertEquals(GattWriteMode.WITHOUT_RESPONSE,DeviceCatalog.writeMode(b,true,true))
        assertEquals(GattWriteMode.ACKNOWLEDGED,DeviceCatalog.writeMode(m,true,true))
        assertEquals(GattWriteMode.WITHOUT_RESPONSE,DeviceCatalog.writeMode(m,false,true))
        assertNull(DeviceCatalog.writeMode(m,false,false))
        assertNull(DeviceCatalog.writeMode(qc,false,true))
        assertNotNull(DeviceCatalog.match(uuid("fff0"),uuid("fff3"),null,"ELK-BLEDOM",GattWriteMode.WITHOUT_RESPONSE))
    }
    @Test fun savedAliasNeverChangesProtocolAndSavedDriverMismatchIsRefused() {
        var raw:String?=null
        val storage=object:DeviceListStorage { override fun read()=raw;override fun write(value:String):Boolean {raw=value;return true} }
        val devices=SavedDevices(storage){null}
        val light=LightDevice("02:AA:BB:CC:DD:13","ELK-BLEDOM")
        val model=DeviceCatalog.models.single{it.driverId==driver.id}
        assertNull(devices.save(light).descriptor)
        devices.rename(light.id,"ELK-BLEDOB")
        devices.authenticated(light,model.reference())
        val next=SavedDevices(storage){null}.lastAuthenticated()!!
        assertEquals("ELK-BLEDOM",next.advertisedName)
        assertEquals("ELK-BLEDOB",next.alias)
        assertEquals(model.reference(),next.descriptor)
        assertTrue(DeviceCatalog.acceptsSaved(model,next.descriptor))
        assertFalse(DeviceCatalog.acceptsSaved(DeviceCatalog.models.single{it.id=="ELK-BLEDOB"},next.descriptor))
    }
    @Test fun powerRgbAndBrightnessMatchBledomVectorsInsteadOfBledob() {
        assertEquals(listOf("7e0404010001ff00ef","7e07050312345610ef","7e04014effffff00ef"),frames())
        assertEquals(listOf("7e0404000000ff00ef"),frames(state.copy(on=false),Change.OFF))
        assertEquals(listOf("7e0404000000ff00ef"),frames(state.copy(brightness=0),Change.BRIGHTNESS))
        assertEquals(listOf("7e070503ff000010ef"),frames(state.copy(color=0xffff0000.toInt(),effect=Effect.FULL_WHEEL),Change.FRAME))
        assertEquals(listOf("7e0705030000ff10ef"),frames(state.copy(color=0xff0000ff.toInt(),effect=Effect.FULL_WHEEL),Change.FRAME))
    }
    @Test fun presetsHaveExactBledomEffectIdsAndSpeedEndpoints() {
        mapOf(Effect.JUMP3 to "87",Effect.JUMP7 to "88",Effect.FADE3 to "89",Effect.FADE7 to "8a",Effect.STROBE7 to "95").forEach { (effect,id) ->
            assertEquals(listOf("7e0404010001ff00ef","7e0503${id}03ffff00ef","7e040232ffffff00ef","7e04014effffff00ef"),frames(state.copy(effect=effect)))
        }
        assertEquals(listOf("7e040201ffffff00ef"),frames(state.copy(effect=Effect.FADE3,speed=1),Change.SPEED))
        assertEquals(listOf("7e040264ffffff00ef"),frames(state.copy(effect=Effect.FADE3,speed=100),Change.SPEED))
        assertTrue(frames(state,Change.SPEED).isEmpty())
    }
    @Test fun positiveBrightnessRestoresPowerWithoutResettingModeEvenAfterOff() {
        listOf(false,true).forEach { restore ->
            assertEquals(listOf("7e0404010001ff00ef","7e040101ffffff00ef"),frames(state.copy(effect=Effect.FADE7,brightness=1),Change.BRIGHTNESS,restore))
            assertEquals(listOf("7e0404010001ff00ef","7e040164ffffff00ef"),frames(state.copy(effect=Effect.FULL_WHEEL,brightness=100),Change.BRIGHTNESS,restore))
        }
    }
    @Test fun microphoneAndInvalidParametersNeverReachTransport() {
        val clock=FakeClock();val link=FakeLink(clock)
        val session=LightSession(clock,{_,_->},{})
        session.attach(link,driver)
        assertFalse(driver.requiresAuthentication);assertNull(driver.beginAuthentication())
        Effect.values().filter{it.isMusic}.forEach { assertFalse(session.submit(state.copy(effect=it))) }
        assertFalse(session.submit(state.copy(brightness=101)))
        assertFalse(session.submit(state.copy(speed=0)))
        clock.advanceBy(6000)
        assertTrue(link.events.isEmpty())
    }
    @Test fun reconnectMarksReadyAndNeverSubscribesAuthenticatesOrReplays() {
        val clock=FakeClock();val statuses=mutableListOf<String>()
        val session=LightSession(clock,{text,_->statuses+=text},{})
        val link=FakeLink(clock);session.attach(link,driver)
        assertTrue(session.ready)
        session.submit(state.copy(effect=Effect.FULL_WHEEL))
        clock.runCurrent();link.acknowledge(0)
        session.disconnect(false)
        val next=FakeLink(clock);session.attach(next,driver)
        clock.advanceBy(6000)
        assertTrue(session.ready);assertEquals(listOf("Ready","Ready"),statuses)
        assertTrue(next.events.isEmpty());assertEquals(0,session.queuedActions)
        assertEquals(1,link.writes.size)
    }
    @Test fun elkReadyLinkArmsExactForegroundRangePolicyWithoutQcAuthentication() {
        val reconnect=ForegroundReconnect();reconnect.setForeground(true)
        val target=LightDevice("02:AA:BB:CC:DD:13","ELK-BLEDOM")
        val clock=FakeClock()
        val session=LightSession(clock,{_,ready->if(ready)reconnect.authenticated(target)},{})
        session.attach(FakeLink(clock),driver)
        assertTrue(reconnect.unexpectedLoss(target.id));assertTrue(reconnect.arrived())
        session.attach(FakeLink(clock),driver)
        assertEquals(ForegroundReconnect.Phase.ARMED,reconnect.phase)
        assertFalse(reconnect.unexpectedLoss("02:AA:BB:CC:DD:14"))
        reconnect.setForeground(false)
        assertFalse(reconnect.unexpectedLoss(target.id))
    }
    @Test fun discoveryHintsNeverAssertHardwareSupportFromNameOrAlias() {
        assertEquals("Known model · verification needed",DeviceCatalog.discoveryLabel(" ELK-BLEDOM  "))
        assertEquals("Known model · verification needed",DeviceCatalog.discoveryLabel("elk-bledob"))
        assertEquals("Possible QC light · verification needed",DeviceCatalog.discoveryLabel("DayBetter"))
        assertEquals("Not yet supported · connect to check",DeviceCatalog.discoveryLabel("ELK-BLEDXY"))
        assertEquals("Not yet supported · connect to check",DeviceCatalog.discoveryLabel("Headphones"))
    }
}
