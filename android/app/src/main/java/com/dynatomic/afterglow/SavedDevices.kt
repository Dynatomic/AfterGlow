// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class SavedDevice(val id:String,val advertisedName:String,val alias:String,val authenticated:Boolean,
    val descriptor:DeviceDescriptorRef?=null)
data class DeviceSnapshot(val records:List<SavedDevice>,val lastAuthenticatedId:String?,val legacyMigrated:Boolean=true)
interface DeviceListStorage { fun read():String?;fun write(value:String):Boolean }
class DeviceListException(message:String):Exception(message)

object SavedDeviceCodec {
    const val LIMIT=32
    const val VERSION=2
    fun encode(snapshot:DeviceSnapshot):String {
        val records=JSONArray()
        snapshot.records.forEach{
            val ref=it.descriptor?.takeIf{ref->it.authenticated&&DeviceCatalog.resolve(ref)!=null}
            val descriptor=ref?.let{value->JSONObject().put("transport",value.transport.storageId)
                .put("driver",value.driverId).put("catalog",value.modelId)} ?: JSONObject.NULL
            records.put(JSONObject().put("id",it.id).put("name",it.advertisedName).put("alias",it.alias)
                .put("authenticated",it.authenticated).put("descriptor",descriptor))
        }
        return JSONObject().put("version",VERSION).put("legacyMigrated",snapshot.legacyMigrated)
            .put("lastAuthenticatedId",snapshot.lastAuthenticatedId ?: JSONObject.NULL).put("devices",records).toString()
    }
    fun decode(text:String):DeviceSnapshot?=try {
        require(text.length<=32768)
        require(boundedStructure(text))
        val input=JSONTokener(text)
        val root=input.nextValue() as? JSONObject ?: throw IllegalArgumentException()
        require(input.nextClean()=='\u0000')
        require(root.opt("version") is Int&&root.getInt("version") in 1..VERSION)
        val version=root.getInt("version")
        require(root.opt("legacyMigrated") is Boolean&&root.getBoolean("legacyMigrated"))
        val array=root.opt("devices") as? JSONArray ?: throw IllegalArgumentException()
        require(array.length()<=LIMIT)
        val records=linkedMapOf<String,SavedDevice>()
        for(index in 0 until array.length()){
            val item=array.opt(index) as? JSONObject ?: throw IllegalArgumentException()
            val id=item.opt("id") as? String ?: throw IllegalArgumentException()
            val name=item.opt("name") as? String ?: throw IllegalArgumentException()
            val alias=item.opt("alias") as? String ?: throw IllegalArgumentException()
            require(item.opt("authenticated") is Boolean&&name.length<=80)
            val light=LightMemory.validated(LightDevice(id,name)) ?: throw IllegalArgumentException()
            val validAlias=validateAlias(alias)
            val authenticated=item.getBoolean("authenticated")
            // A v1 record proves past authentication only, not which exact channel was matched.
            val descriptor=if(version>=2&&authenticated)decodeDescriptor(item.opt("descriptor")) else null
            records.putIfAbsent(light.id,SavedDevice(light.id,light.name,validAlias,authenticated,descriptor))
        }
        val pointer=root.opt("lastAuthenticatedId")
        require(pointer==null||pointer===JSONObject.NULL||pointer is String)
        val last=(pointer as? String)?.let{LightMemory.validated(LightDevice(it,""))?.id}
            ?.takeIf{id->records[id]?.authenticated==true}
        DeviceSnapshot(records.values.toList(),last)
    }catch(_:Exception){null}
    fun requiresMigration(text:String):Boolean=try{JSONObject(text).optInt("version")==1}catch(_:Exception){false}
    private fun decodeDescriptor(value:Any?):DeviceDescriptorRef? {
        val item=value as? JSONObject ?: return null
        val transport=when(item.opt("transport")){"bluetooth-le"->TransportType.BLE;"wifi"->TransportType.WIFI;else->return null}
        val driver=item.opt("driver") as? String ?: return null
        val model=item.opt("catalog") as? String ?: return null
        return DeviceDescriptorRef(transport,driver,model).takeIf{DeviceCatalog.resolve(it)!=null}
    }
    private fun boundedStructure(text:String):Boolean {
        var depth=0;var quoted=false;var escaped=false
        for(char in text){
            if(quoted){if(escaped)escaped=false else if(char=='\\')escaped=true else if(char=='"')quoted=false}
            else when(char){'"'->quoted=true;'[','{'->{depth++;if(depth>6)return false};']','}'->{depth--;if(depth<0)return false}}
        }
        return !quoted&&depth==0
    }
    fun validateAlias(value:String):String {
        if(value.any{it.isISOControl()})throw DeviceListException("Names cannot contain control characters.")
        val trimmed=value.trim()
        if(trimmed.isEmpty()||trimmed.length>40)throw DeviceListException("Use a name from 1 to 40 characters.")
        return trimmed
    }
}

/** Small local address book. It has no Bluetooth or lighting capability. */
class SavedDevices(private val storage:DeviceListStorage,legacy:()->LightDevice?) {
    private var snapshot=DeviceSnapshot(emptyList(),null)
    var loadIssue:String?=null;private set
    init {
        val raw=try{storage.read()}catch(_:Exception){loadIssue="Saved devices could not be read.";""}
        if(raw!=null){
            val decoded=SavedDeviceCodec.decode(raw)
            snapshot=decoded ?: snapshot.also{loadIssue="Saved devices could not be read. Save them again."}
            if(decoded!=null&&SavedDeviceCodec.requiresMigration(raw)){
                try{write(decoded)}catch(_:DeviceListException){loadIssue="Saved devices are available, but their format could not yet be updated."}
            }
        }
        else {
            val old=try{legacy()?.let{LightMemory.validated(it)}}catch(_:Exception){null}
            if(old!=null){
                val imported=SavedDevice(old.id,old.name,old.name.take(40),true)
                snapshot=DeviceSnapshot(listOf(imported),old.id)
                try{write(snapshot)}catch(_:DeviceListException){loadIssue="The previous light is available, but could not yet be saved."}
            }
        }
    }
    fun all()=snapshot.records.toList()
    fun find(id:String)=canonical(id)?.let{key->snapshot.records.find{it.id==key}}
    fun lastAuthenticated()=snapshot.lastAuthenticatedId?.let(::find)
    fun save(light:LightDevice):SavedDevice=upsert(light,false,null)
    fun authenticated(light:LightDevice,descriptor:DeviceDescriptorRef?=null):SavedDevice=upsert(light,true,descriptor)
    private fun upsert(input:LightDevice,authenticated:Boolean,descriptor:DeviceDescriptorRef?):SavedDevice {
        val light=LightMemory.validated(input) ?: throw DeviceListException("That device identity is invalid. Scan again.")
        val old=find(light.id)
        if(old==null&&snapshot.records.size>=SavedDeviceCodec.LIMIT)throw DeviceListException("Save up to 32 devices. Forget one before adding another.")
        val verified=if(authenticated)descriptor?.takeIf{DeviceCatalog.resolve(it)!=null} else null
        val record=SavedDevice(light.id,light.name,old?.alias ?: light.name.take(40),authenticated||old?.authenticated==true,
            verified ?: old?.descriptor)
        val records=if(old==null)snapshot.records+record else snapshot.records.map{if(it.id==light.id)record else it}
        write(snapshot.copy(records=records,lastAuthenticatedId=if(authenticated)light.id else snapshot.lastAuthenticatedId))
        return record
    }
    fun rename(id:String,alias:String){
        val old=find(id) ?: throw DeviceListException("The saved device is no longer in the list.")
        val valid=SavedDeviceCodec.validateAlias(alias)
        write(snapshot.copy(records=snapshot.records.map{if(it.id==old.id)it.copy(alias=valid) else it}))
    }
    fun forget(id:String){
        val key=canonical(id) ?: return
        val remaining=snapshot.records.filter{it.id!=key}
        val last=if(snapshot.lastAuthenticatedId==key)remaining.lastOrNull{it.authenticated}?.id else snapshot.lastAuthenticatedId
        write(snapshot.copy(records=remaining,lastAuthenticatedId=last))
    }
    private fun canonical(id:String)=LightMemory.validated(LightDevice(id,""))?.id
    private fun write(next:DeviceSnapshot){
        try{if(!storage.write(SavedDeviceCodec.encode(next)))throw DeviceListException("Saved devices could not be stored. Try again.")}
        catch(error:DeviceListException){throw error}
        catch(_:Exception){throw DeviceListException("Saved devices could not be stored. Try again.")}
        snapshot=next;loadIssue=null
    }
}
