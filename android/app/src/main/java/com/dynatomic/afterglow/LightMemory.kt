// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import java.util.Locale

data class LightDevice(val id:String,val name:String)
interface SavedLightStorage {
    fun read():LightDevice?
    fun write(light:LightDevice)
    fun clear()
}

/** Stores only the last authenticated identity, never lighting state or a pending choice. */
class LightMemory(private val storage:SavedLightStorage) {
    private var generation=0L
    private var pending:Pair<Long,LightDevice>?=null
    fun saved():LightDevice?=try{storage.read()?.let{validated(it)}}catch(_:Exception){null}
    fun begin(light:LightDevice):Long? {
        cancel()
        val valid=validated(light) ?: return null
        val token=generation;pending=token to valid;return token
    }
    fun authenticated(token:Long):Boolean {
        val candidate=pending ?: return false
        if(candidate.first!=token||token!=generation)return false
        storage.write(candidate.second);pending=null;return true
    }
    fun cancel(){generation++;pending=null}
    fun forget(){cancel();storage.clear()}
    companion object {
        fun validated(light:LightDevice):LightDevice? {
            if(!Regex("[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}").matches(light.id))return null
            val name=light.name.replace(Regex("[\\p{Cntrl}]")," ").trim().take(80).ifBlank{"Last light"}
            return LightDevice(light.id.uppercase(Locale.ROOT),name)
        }
    }
}
