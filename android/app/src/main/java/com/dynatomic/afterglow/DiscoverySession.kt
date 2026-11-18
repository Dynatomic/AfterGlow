// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

/** Discovery owns no connection, authentication, memory, or lighting capability. */
class DiscoverySession(private val clock:SessionClock,private val expired:()->Unit) {
    private var generation=0L
    private var deadline:Cancellation?=null
    var active=false;private set
    fun begin():Long {
        stop();active=true;val token=generation
        deadline=clock.later(10000){if(owns(token)){stop();expired()}}
        return token
    }
    fun owns(token:Long)=active&&token==generation
    fun stop():Boolean {
        val wasActive=active;generation++;active=false;deadline?.cancel();deadline=null
        return wasActive
    }
}
