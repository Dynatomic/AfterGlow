// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

/** No timers, scan, storage, startup connection, or background intent. */
class ForegroundReconnect {
    enum class Phase { DISABLED, ARMED, WAITING, AUTHENTICATING }
    var phase=Phase.DISABLED;private set
    var target:LightDevice?=null;private set
    var foreground=false;private set
    fun setForeground(value:Boolean):Boolean {
        val cancel=!value&&(phase==Phase.WAITING||phase==Phase.AUTHENTICATING)
        foreground=value
        if(!value)stop()
        return cancel
    }
    fun authenticated(identity:LightDevice){
        if(!foreground){stop();return}
        target=LightMemory.validated(identity)
        phase=if(target!=null)Phase.ARMED else Phase.DISABLED
    }
    fun unexpectedLoss(id:String):Boolean {
        val canonical=LightMemory.validated(LightDevice(id,""))?.id
        if(!foreground||phase!=Phase.ARMED||canonical==null||canonical!=target?.id)return false
        phase=Phase.WAITING;return true
    }
    fun arrived():Boolean {
        if(!foreground||phase!=Phase.WAITING)return false
        phase=Phase.AUTHENTICATING;return true
    }
    fun stop(){target=null;phase=Phase.DISABLED}
}
