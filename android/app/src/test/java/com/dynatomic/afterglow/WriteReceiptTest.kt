// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import org.junit.Assert.*
import org.junit.Test

class WriteReceiptTest {
    @Test fun noResponseCompletesLocallyOnceWithoutWaitingForNativeCallback() {
        val clock=FakeClock();val outcomes=mutableListOf<Boolean>();val receipt=WriteReceipt(clock){true}
        receipt.accept(GattWriteMode.WITHOUT_RESPONSE){outcomes+=it}
        receipt.acknowledge(false)
        clock.advanceBy(39)
        assertTrue(receipt.pending);assertTrue(outcomes.isEmpty())
        clock.advanceBy(1)
        assertEquals(listOf(true),outcomes);assertFalse(receipt.pending)
        receipt.acknowledge(true);clock.advanceBy(6000)
        assertEquals(1,outcomes.size)
    }
    @Test fun lateNoResponseCallbackCannotCompleteFollowingWrite() {
        val clock=FakeClock();val outcomes=mutableListOf<Boolean>();val receipt=WriteReceipt(clock){true}
        receipt.accept(GattWriteMode.WITHOUT_RESPONSE){outcomes+=it};clock.advanceBy(40)
        receipt.accept(GattWriteMode.WITHOUT_RESPONSE){outcomes+=it};receipt.acknowledge(false)
        assertEquals(1,outcomes.size);assertTrue(receipt.pending)
        clock.advanceBy(40);assertEquals(listOf(true,true),outcomes)
    }
    @Test fun acknowledgedQcStillRequiresNativeAckAndKeepsFiveSecondFailureDeadline() {
        val clock=FakeClock();val outcomes=mutableListOf<Boolean>();val receipt=WriteReceipt(clock){true}
        receipt.accept(GattWriteMode.ACKNOWLEDGED){outcomes+=it};clock.advanceBy(4999)
        assertTrue(outcomes.isEmpty());receipt.acknowledge(true);clock.advanceBy(5000)
        assertEquals(listOf(true),outcomes)
        receipt.accept(GattWriteMode.ACKNOWLEDGED){outcomes+=it};clock.advanceBy(5000)
        assertEquals(listOf(true,false),outcomes)
    }
    @Test fun cancelAndOwnershipLossSuppressExpiredGeneration() {
        val clock=FakeClock();var owns=true;val outcomes=mutableListOf<Boolean>();val receipt=WriteReceipt(clock){owns}
        receipt.accept(GattWriteMode.WITHOUT_RESPONSE){outcomes+=it};receipt.cancel()
        receipt.accept(GattWriteMode.WITHOUT_RESPONSE){outcomes+=it};owns=false;clock.advanceBy(6000)
        assertTrue(outcomes.isEmpty())
        receipt.cancel();owns=true
        receipt.accept(GattWriteMode.WITHOUT_RESPONSE){outcomes+=it};clock.advanceBy(40)
        assertEquals(listOf(true),outcomes)
    }
}
