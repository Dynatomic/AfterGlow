// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

/** Successful removal of the selected identity also ends that connection intent. */
class ForgetDevice(private val devices:SavedDevices,private val selectedId:()->String?,private val disconnect:()->Unit) {
    fun forget(id:String){
        val canonical=LightMemory.validated(LightDevice(id,""))?.id ?: return
        devices.forget(canonical)
        val selected=selectedId()?.let{LightMemory.validated(LightDevice(it,""))?.id}
        if(selected==canonical)disconnect()
    }
}
