// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

/** One explicit Connect intent: at most two links, and at most thirty seconds total. */
class ConnectAttempt(private val clock:SessionClock,private val expired:(Int)->Unit) {
    private var generation=0L
    private var deadline:Cancellation?=null
    private var retry:Cancellation?=null
    var attempts=0;private set
    var active=false;private set
    fun begin(){
        cancel();active=true;attempts=1;val token=generation
        deadline=clock.later(30000){
            if(active&&token==generation){val count=attempts;cancel();expired(count)}
        }
    }
    fun retryAuthentication(reconnect:()->Unit):Boolean {
        if(!active||attempts!=1)return false
        attempts=2;val token=generation
        // A new looper turn permits cancellation and does not invent a protocol delay.
        retry=clock.later(0){retry=null;if(active&&token==generation)reconnect()}
        return true
    }
    fun authenticated(){cancel()}
    fun cancel(){generation++;active=false;attempts=0;deadline?.cancel();deadline=null;retry?.cancel();retry=null}
}
