// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import org.junit.Assert.*
import org.junit.Test

class ConnectAttemptTest {
    @Test fun constructionNeverConnectsOrRetries(){
        val clock=FakeClock();val expired=mutableListOf<Int>();val attempt=ConnectAttempt(clock){expired+=it}
        clock.advanceBy(60000);assertFalse(attempt.active);assertEquals(0,attempt.attempts);assertTrue(expired.isEmpty());assertFalse(attempt.retryAuthentication{fail("Unexpected connection")})
    }
    @Test fun onlyOneRetryIsAllowedPerExplicitConnect(){
        val clock=FakeClock();val attempt=ConnectAttempt(clock){};var links=1
        attempt.begin();assertTrue(attempt.retryAuthentication{links++});assertFalse(attempt.retryAuthentication{links++})
        assertEquals(1,links);clock.runCurrent();assertEquals(2,links);assertEquals(2,attempt.attempts)
        assertFalse(attempt.retryAuthentication{links++});clock.advanceBy(5000);assertEquals(2,links)
    }
    @Test fun cancelBeforeNextLooperTurnPreventsRetry(){
        val clock=FakeClock();val attempt=ConnectAttempt(clock){};var retries=0
        attempt.begin();attempt.retryAuthentication{retries++};attempt.cancel();clock.advanceBy(60000)
        assertEquals(0,retries);assertFalse(attempt.active);assertFalse(clock.hasTimers)
    }
    @Test fun aNewConnectIntentInvalidatesTheOldPendingRetry(){
        val clock=FakeClock();val attempt=ConnectAttempt(clock){};var old=0;var fresh=0
        attempt.begin();attempt.retryAuthentication{old++};attempt.begin();attempt.retryAuthentication{fresh++};clock.runCurrent()
        assertEquals(0,old);assertEquals(1,fresh)
    }
    @Test fun successCancelsRetryAndDeadlineWithoutAutomaticFutureWork(){
        val clock=FakeClock();var expired=0;val attempt=ConnectAttempt(clock){expired++}
        attempt.begin();attempt.retryAuthentication{fail("Retry after authentication")};attempt.authenticated();clock.advanceBy(60000)
        assertEquals(0,expired);assertFalse(attempt.active);assertFalse(clock.hasTimers)
    }
    @Test fun thirtySecondDeadlineIncludesTheRetryTime(){
        val clock=FakeClock();val expired=mutableListOf<Int>();val attempt=ConnectAttempt(clock){expired+=it};var retries=0
        attempt.begin();clock.advanceBy(25000);assertTrue(attempt.retryAuthentication{retries++});clock.runCurrent()
        clock.advanceBy(4999);assertTrue(attempt.active);assertEquals(1,retries);clock.advanceBy(1)
        assertFalse(attempt.active);assertEquals(listOf(2),expired);assertFalse(attempt.retryAuthentication{retries++});clock.advanceBy(60000);assertEquals(1,retries)
    }
    private class Rig {
        val clock=FakeClock();val links=mutableListOf<FakeLink>();val errors=mutableListOf<String>();val timeouts=mutableListOf<String>()
        val attempt:ConnectAttempt=ConnectAttempt(clock){count->session.disconnect();errors+="Deadline after $count attempts"}
        val session:LightSession=LightSession(clock,{_,ready->if(ready)attempt.authenticated()},{errors+=it},authenticationTimeout={message->
            timeouts+=message
            if(!attempt.retryAuthentication{openFreshLink()}){attempt.cancel();errors+=message+" One retry used."}
        },failure={message->attempt.cancel();errors+=message})
        init{attempt.begin();openFreshLink()}
        fun openFreshLink(){val next=FakeLink(clock);links+=next;session.attach(next)}
        fun startChallenge(link:FakeLink=links.last(),ack:Boolean=true){link.finishSubscription();clock.runCurrent();if(ack)link.acknowledge(0)}
        fun finish(link:FakeLink=links.last()){
            startChallenge(link);link.notify(responseFrame(0,67,67,72,73,80,0x12,0x34));clock.advanceBy(40)
            link.acknowledge(1);link.notify(responseFrame(1,67,67,72,73,80,0))
        }
        fun cancel(){attempt.cancel();session.disconnect()}
    }
    @Test fun silentChallengeClosesOldLinkRetriesFreshAndAuthenticatesWithoutLighting(){
        val rig=Rig();val first=rig.links.first();rig.startChallenge();assertFalse(rig.session.submit(LightState()))
        rig.clock.advanceBy(5000);assertEquals(1,first.closeCount);assertEquals(2,rig.links.size)
        assertTrue(rig.timeouts.single().contains("challenge: write confirmed, no response; 0 notifications"))
        rig.finish();rig.clock.advanceBy(60000);assertTrue(rig.session.ready)
        assertEquals(listOf(0),first.writes.map{it.bytes[1].toInt() and 255})
        assertEquals(listOf(0,1),rig.links.last().writes.map{it.bytes[1].toInt() and 255})
        assertEquals("a000084343484950acca",rig.links.last().writes.first().bytes.hex())
        assertEquals(QcProtocol.frame(1,67,67,72,73,80,0x36,0x78).hex(),rig.links.last().writes.last().bytes.hex())
        assertEquals(0,rig.session.queuedActions)
    }
    @Test fun lateOldNotificationsAndWriteCallbacksCannotAuthenticateOrCloseFreshLink(){
        val rig=Rig();val old=rig.links.first();rig.startChallenge(ack=false);val stale=old.writes.first().complete
        rig.clock.advanceBy(5000);val fresh=rig.links.last();assertNotSame(old,fresh)
        old.notify(responseFrame(0,67,67,72,73,80,1,2));stale(true);old.finishSubscription()
        assertTrue(fresh.writes.isEmpty());assertFalse(rig.session.ready)
        rig.finish();old.notify(responseFrame(1,67,67,72,73,80,0));stale(false)
        assertTrue(rig.session.ready);assertEquals(0,fresh.closeCount)
    }
    @Test fun subscriptionTimeoutRetriesOnceAndExhaustionIsFinal(){
        val rig=Rig();rig.clock.advanceBy(10000)
        assertEquals(2,rig.links.size);assertEquals(2,rig.timeouts.size);assertTrue(rig.timeouts.all{it.contains("enabling notifications")})
        assertTrue(rig.errors.single().contains("One retry used"));assertFalse(rig.session.ready)
        assertTrue(rig.links.all{it.closeCount==1&&it.writes.isEmpty()});rig.clock.advanceBy(60000);assertEquals(2,rig.links.size)
    }
    @Test fun challengeWriteAndConfirmationResponseFailuresHaveDifferentSanitizedDetails(){
        val pending=Rig();pending.startChallenge(ack=false);pending.clock.advanceBy(5000)
        assertTrue(pending.timeouts.first().contains("challenge: write confirmation pending, no response; 0 notifications"))
        val confirmation=Rig();confirmation.startChallenge();confirmation.links.first().notify(responseFrame(0,67,67,72,73,80,1,2));confirmation.clock.advanceBy(40)
        confirmation.links.first().acknowledge(1);confirmation.clock.advanceBy(5000)
        assertTrue(confirmation.timeouts.first().contains("confirmation: write confirmed, no response; 1 notifications"))
        assertFalse(confirmation.timeouts.first().contains("CCHIP"));assertFalse(confirmation.timeouts.first().contains("02:"))
    }
    @Test fun receivedResponseWithMissingWriteAckIsReportedWithoutBecomingReady(){
        val rig=Rig();rig.startChallenge(ack=false);rig.links.first().notify(responseFrame(0,67,67,72,73,80,1,2));rig.clock.advanceBy(5000)
        assertTrue(rig.timeouts.first().contains("write confirmation pending, response received; 1 notifications"));assertFalse(rig.session.ready)
    }
    @Test fun rejectAndMalformedResponsesNeverTriggerRecovery(){
        for(malformed in listOf(false,true)){
            val rig=Rig();rig.startChallenge()
            if(malformed)rig.links.first().notify(byteArrayOf(0,1,2)) else {
                rig.links.first().notify(responseFrame(0,67,67,72,73,80,1,2));rig.clock.advanceBy(40);rig.links.first().acknowledge(1)
                rig.links.first().notify(responseFrame(1,67,67,72,73,80,1))
            }
            rig.clock.advanceBy(60000);assertEquals(1,rig.links.size);assertTrue(rig.timeouts.isEmpty());assertEquals(1,rig.errors.size)
        }
    }
    @Test fun backgroundOrDisconnectCancelsAuthTimeoutAndAllLaterRecovery(){
        val rig=Rig();rig.startChallenge();rig.cancel();rig.clock.advanceBy(60000)
        assertEquals(1,rig.links.size);assertTrue(rig.timeouts.isEmpty());assertFalse(rig.session.ready);assertEquals(0,rig.session.queuedActions)
    }
    @Test fun thirtySecondDeadlineClosesAStalledConnectionWithoutAnyRetryLoop(){
        val rig=Rig();rig.attempt.cancel();rig.session.disconnect();rig.attempt.begin()
        rig.clock.advanceBy(30000);assertEquals(listOf("Deadline after 1 attempts"),rig.errors)
        assertFalse(rig.attempt.active);rig.clock.advanceBy(60000);assertEquals(1,rig.links.size)
    }
}
