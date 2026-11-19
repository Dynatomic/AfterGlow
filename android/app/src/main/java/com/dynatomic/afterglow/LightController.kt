// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.UUID

/** Native BLE only; never scans or submits a lighting command during construction. */
@SuppressLint("MissingPermission")
class LightController(context:Context,private val listener:Listener) {
    interface Listener {
        fun onStatus(text:String,ready:Boolean,busy:Boolean)
        fun onDevices(devices:List<LightDevice>)
        fun onDiscovery(active:Boolean)
        fun onError(message:String)
    }
    private val app=context.applicationContext
    private val ui=Handler(Looper.getMainLooper())
    private val adapter=(app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private var generation=0L
    private val bleDiscovery:DeviceDiscovery=AndroidBleDiscovery(adapter,ui)
    private val connectionTransport:BleConnectionTransport=BluetoothGattTransport(app)
    private val found=linkedMapOf<String,LightDevice>()
    private var gatt:BluetoothGatt?=null
    private var port:GattSessionLink?=null
    private var connectionTimeout:Runnable?=null
    private var matchedModel:DeviceModelDescriptor?=null
    private var closed=false
    private val legacyPreferences=app.getSharedPreferences("last_authenticated_light",Context.MODE_PRIVATE)
    private val preferences=app.getSharedPreferences("saved_devices",Context.MODE_PRIVATE)
    private val devices=SavedDevices(object:DeviceListStorage {
        override fun read()=preferences.getString("snapshot",null)
        override fun write(value:String)=preferences.edit().putString("snapshot",value).commit()
    },legacy={
        legacyPreferences.getString("address",null)?.let{LightDevice(it,legacyPreferences.getString("name","") ?: "")}
    })
    private val memory=LightMemory(object:SavedLightStorage {
        override fun read()=devices.lastAuthenticated()?.let{LightDevice(it.id,it.alias)}
        override fun write(light:LightDevice){devices.authenticated(light,matchedModel?.reference())}
        override fun clear(){devices.lastAuthenticated()?.let{devices.forget(it.id)}}
    })
    private var rememberAttempt:Long?=null
    private var connectingIdentity:LightDevice?=null
    private var selectedForAttempt:BluetoothDevice?=null
    private var selectedName=""
    private var expectedDescriptorForAttempt:DeviceDescriptorRef?=null
    private var selectedIdentityForAttempt:LightDevice?=null
    private var automaticGatt=false
    private val rangeReconnect=ForegroundReconnect()
    private val forgetAction=ForgetDevice(devices,{selectedIdentityForAttempt?.id ?: connectingIdentity?.id},{disconnect()})
    private var attemptStage="connecting"
    private val clock=object:SessionClock {
        override fun now()=SystemClock.uptimeMillis()
        override fun later(delay:Long,action:()->Unit):Cancellation {
            val task=Runnable(action);ui.postDelayed(task,delay);return Cancellation{ui.removeCallbacks(task)}
        }
    }
    private val connectionAttempt=ConnectAttempt(clock){count->
        fail("Connection stopped after 30 seconds ($count attempts; last stage: $attemptStage).")
    }
    private val discovery=DiscoverySession(clock){
        stopScan()
        if(found.isEmpty())listener.onError("No devices found. Check power and distance, then choose another light.")
    }
    private val session=LightSession(clock,{text,ready->
        var saveFailed=false
        if(ready){
            connectionAttempt.authenticated()
            connectingIdentity?.let{rangeReconnect.authenticated(it)}
            try{rememberAttempt?.let{memory.authenticated(it)}}catch(_:Exception){saveFailed=true}
        }
        else attemptStage=text
        listener.onStatus(if(ready)"$text · ${matchedModel?.id ?: "light"}" else text,ready,!ready&&text.startsWith("Authenticating"))
        if(saveFailed)listener.onError("Connected, but the last light could not be saved on this phone.")
    },{listener.onError(it)},authenticationTimeout={authenticationTimedOut(it)},failure={fail(it)},transportLost={postAuthTransportLost(it)})
    private fun connectPermission()=Build.VERSION.SDK_INT<31||app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED
    private fun scanPermission()=if(Build.VERSION.SDK_INT>=31)app.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED&&connectPermission() else app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED

    fun scan(){
        if(closed)return
        if(!scanPermission()){listener.onError("Allow Nearby devices access to find your light.");return}
        if(adapter==null||!adapter.isEnabled){listener.onError("Turn on Bluetooth, then try Connect.");return}
        stopScan();found.clear();listener.onDevices(emptyList())
        val token=discovery.begin()
        val started=bleDiscovery.start({device->
            if(discovery.owns(token)&&!closed&&(found.size<32||found.containsKey(device.id))){
                found[device.id]=device
                listener.onDevices(found.values.toList())
            }
        },{message->if(discovery.owns(token)){stopScan();listener.onError(message)}})
        if(started&&discovery.owns(token))listener.onDiscovery(true)
    }
    private fun stopScan(){
        val wasActive=discovery.stop()
        val transportActive=bleDiscovery.active
        bleDiscovery.stop()
        if(wasActive||transportActive)listener.onDiscovery(false)
    }
    fun stopDiscovery()=stopScan()
    fun connect(deviceId:String){
        if(closed)return
        if(isConnectedDevice(deviceId))return
        val selected=found[deviceId] ?: run{listener.onError("Choose the light again from a fresh scan.");return}
        if(!connectPermission()){listener.onError("Allow Nearby devices access to connect.");return}
        try{
            val native=adapter?.getRemoteDevice(selected.id) ?: run{listener.onError("Bluetooth is unavailable.");return}
            connectSelected(native,selected.name)
        }
        catch(_:SecurityException){fail("Bluetooth permission was removed. Allow Nearby devices and retry.")}
    }
    fun savedLight():LightDevice?=memory.saved()
    fun savedDevices():List<SavedDevice> = devices.all()
    fun savedDevicesIssue():String?=devices.loadIssue
    fun isConnectedDevice(id:String):Boolean=session.ready&&LightMemory.validated(LightDevice(id,""))?.id==connectingIdentity?.id
    fun isSelectedDevice(id:String):Boolean=LightMemory.validated(LightDevice(id,""))?.id?.let{it==selectedIdentityForAttempt?.id}==true
    fun hasPendingConnection()=connectionAttempt.active||rangeReconnect.phase==ForegroundReconnect.Phase.WAITING
    fun currentDeviceName():String?=if(session.ready)connectingIdentity?.let{devices.find(it.id)?.alias ?: it.name} else null
    /** A live capability source is published only after exact channel match and driver-required setup. */
    fun currentDescriptor():DeviceModelDescriptor?=matchedModel.takeIf{session.ready}
    fun currentCapabilities():DeviceCapabilities?=currentDescriptor()?.capabilities
    fun saveDevice(light:LightDevice):Boolean=editDevices {
        if(isConnectedDevice(light.id))devices.authenticated(light,matchedModel?.reference()) else devices.save(light)
    }
    fun renameSavedDevice(id:String,alias:String):Boolean=editDevices{devices.rename(id,alias)}
    fun forgetSavedDevice(id:String):Boolean=editDevices {forgetAction.forget(id)}
    private fun editDevices(action:()->Unit):Boolean=try{action();true}
        catch(error:DeviceListException){listener.onError(error.message ?: "The saved list could not be changed.");false}
        catch(_:Exception){listener.onError("The saved list could not be changed. Try again.");false}
    fun forgetSavedLight(){
        savedLight()?.let{forgetSavedDevice(it.id)}
    }
    fun connectSaved(deviceId:String?=null){
        if(closed)return
        val saved=if(deviceId==null)devices.lastAuthenticated() else devices.find(deviceId)
        if(saved==null){listener.onError("No saved light. Use Device Option first.");return}
        if(isConnectedDevice(saved.id))return
        if(!connectPermission()){listener.onError("Allow Nearby devices access to connect.");return}
        if(adapter==null||!adapter.isEnabled){listener.onError("Turn on Bluetooth, then connect the saved light.");return}
        try{
            val native=adapter.getRemoteDevice(saved.id)
            connectSelected(native,native.name?.takeIf{it.isNotBlank()} ?: saved.advertisedName)
        }
        catch(_:Exception){listener.onError("Could not open the saved light. Use Device Option to scan again.")}
    }
    private fun connectSelected(selected:BluetoothDevice,name:String){
        if(!connectPermission()){listener.onError("Allow Nearby devices access to connect.");return}
        val identity=LightMemory.validated(LightDevice(selected.address,name)) ?: run{listener.onError("Choose a valid device from Device Option.");return}
        disconnect();connectionAttempt.begin();selectedForAttempt=selected;selectedName=name;selectedIdentityForAttempt=identity
        expectedDescriptorForAttempt=devices.find(identity.id)?.descriptor
        openSelected(selected,name)
    }
    private fun openSelected(selected:BluetoothDevice,name:String){
        if(closed||!connectionAttempt.active)return
        if(!connectPermission()){fail("Bluetooth permission was removed. Allow Nearby devices and retry.");return}
        val token=generation
        attemptStage="connecting"
        listener.onStatus("Connecting · tap to cancel",false,true)
        try{
            connectingIdentity=LightMemory.validated(LightDevice(selected.address,name))
            rememberAttempt=connectingIdentity?.let{memory.begin(it)}
            gatt=connectionTransport.open(selected,false,callbacks(token))
            if(gatt==null){fail("Could not connect to this light.");return}
            armConnectionTimeout(token,"Connection timed out. Check that the light is nearby and not busy.",10000)
        }catch(_:SecurityException){fail("Bluetooth permission was removed. Allow Nearby devices and retry.")}
        catch(_:Exception){fail("Could not connect. Check power and distance, then retry.")}
    }
    private fun authenticationTimedOut(message:String){
        if(rangeReconnect.phase==ForegroundReconnect.Phase.AUTHENTICATING){fail(message+" Automatic reconnection stopped. Use Device Option to connect.");return}
        val selected=selectedForAttempt
        val retrySpent=connectionAttempt.attempts>=2
        if(selected!=null&&connectionAttempt.retryAuthentication{openSelected(selected,selectedName)}){
            // Invalidate the old GATT before its asynchronous disconnect callback arrives.
            clearTransport()
            listener.onStatus("Retrying authentication · 2 of 2",false,true)
        }else fail(message+if(retrySpent)" One retry used." else "")
    }
    private fun waitForRange(selected:BluetoothDevice){
        clearTransport()
        if(!rangeReconnect.foreground||!connectPermission()||adapter?.isEnabled!=true){fail("Reconnection stopped. Use Device Option to connect.");return}
        val token=generation
        listener.onStatus("Waiting for device",false,false)
        try{
            automaticGatt=true
            // One native waiting link; no scan, polling, app retry loop, or lighting replay.
            gatt=connectionTransport.open(selected,true,callbacks(token))
            if(gatt==null)fail("Could not wait for this device. Use Device Option to connect.")
        }catch(_:Exception){fail("Could not wait for this device. Use Device Option to connect.")}
    }
    private fun postAuthTransportLost(message:String){
        val selected=selectedForAttempt
        val identity=selectedIdentityForAttempt
        if(selected!=null&&identity!=null&&rangeReconnect.unexpectedLoss(identity.id))waitForRange(selected)
        else fail(message)
    }
    private fun current(candidate:BluetoothGatt,token:Long)=!closed&&token==generation&&gatt===candidate
    private fun armConnectionTimeout(token:Long,message:String,ms:Long){
        connectionTimeout?.let(ui::removeCallbacks)
        connectionTimeout=Runnable{if(token==generation)fail(message)};ui.postDelayed(connectionTimeout!!,ms)
    }
    private fun callbacks(token:Long)=object:BluetoothGattCallback(){
        override fun onConnectionStateChange(candidate:BluetoothGatt,status:Int,newState:Int){ui.post{
            if(!current(candidate,token)){if(newState==BluetoothProfile.STATE_CONNECTED)try{candidate.disconnect();candidate.close()}catch(_:Exception){};return@post}
            if(status!=BluetoothGatt.GATT_SUCCESS||newState==BluetoothProfile.STATE_DISCONNECTED){
                if(rangeReconnect.phase==ForegroundReconnect.Phase.WAITING){
                    if(status==BluetoothGatt.GATT_SUCCESS){listener.onStatus("Waiting for device",false,false)}
                    else fail("Reconnection could not wait for this device. Use Device Option to connect.")
                    return@post
                }
                val selected=selectedForAttempt
                val identity=connectingIdentity
                if(session.ready&&selected!=null&&identity!=null&&rangeReconnect.unexpectedLoss(identity.id))waitForRange(selected)
                else fail("Light disconnected. Check power and distance, then reconnect.")
                return@post
            }
            if(newState==BluetoothProfile.STATE_CONNECTED){
                if(rangeReconnect.phase==ForegroundReconnect.Phase.WAITING){
                    if(!rangeReconnect.arrived()){fail("Reconnection cancelled.");return@post}
                    connectingIdentity=selectedIdentityForAttempt
                    rememberAttempt=connectingIdentity?.let{memory.begin(it)}
                    if(connectingIdentity==null){fail("Reconnection cancelled.");return@post}
                    connectionAttempt.begin() // Waiting itself has no app timer; establishment is capped at 30s.
                }
                attemptStage="service discovery"
                listener.onStatus("Checking light…",false,true)
                armConnectionTimeout(token,"The light did not finish service discovery.",8000)
                try{if(!candidate.discoverServices())fail("Could not read this light’s services.")}catch(_:Exception){fail("Could not read this light’s services.")}
            }
        }}
        override fun onServicesDiscovered(candidate:BluetoothGatt,status:Int){ui.post{
            if(!current(candidate,token))return@post
            connectionTimeout?.let(ui::removeCallbacks);connectionTimeout=null
            if(status!=BluetoothGatt.GATT_SUCCESS){fail("Could not read this light’s services.");return@post}
            val match=DeviceCatalog.models.firstOrNull{model->
                val signature=model.signature
                val service=candidate.getService(UUID.fromString(signature.service))
                val w=service?.getCharacteristic(UUID.fromString(signature.write))
                val n=signature.notify?.let{service?.getCharacteristic(UUID.fromString(it))}
                DeviceCatalog.matchesName(model,selectedName)&&DeviceCatalog.acceptsSaved(model,expectedDescriptorForAttempt)&&
                    w!=null&&DeviceCatalog.writeMode(model,w.properties and BluetoothGattCharacteristic.PROPERTY_WRITE!=0,
                        w.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE!=0)!=null&&
                    (signature.notify==null || (n!=null&&n.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY!=0))
            }
            if(match==null){fail("Not yet supported: this device has no matching supported name and channel, or its saved driver identity changed. No lighting commands were sent.");return@post}
            val driver=DriverRegistry.resolve(match.driverId)
            if(driver==null||!TransportRegistry.supports(match.transport)){fail("This device driver is unavailable.");return@post}
            matchedModel=match
            val service=candidate.getService(UUID.fromString(match.signature.service))
            val writer=service.getCharacteristic(UUID.fromString(match.signature.write))
            val mode=DeviceCatalog.writeMode(match,writer.properties and BluetoothGattCharacteristic.PROPERTY_WRITE!=0,
                writer.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE!=0)!!
            val next=GattSessionLink(candidate,writer,
                match.signature.notify?.let{service.getCharacteristic(UUID.fromString(it))},clock,mode){current(candidate,token)}
            port=next;session.attach(next,driver)
        }}
        override fun onDescriptorWrite(candidate:BluetoothGatt,descriptor:BluetoothGattDescriptor,status:Int){ui.post{if(current(candidate,token))port?.descriptorWritten(descriptor,status==BluetoothGatt.GATT_SUCCESS)}}
        override fun onCharacteristicWrite(candidate:BluetoothGatt,characteristic:BluetoothGattCharacteristic,status:Int){ui.post{if(current(candidate,token))port?.written(characteristic,status==BluetoothGatt.GATT_SUCCESS)}}
        @Deprecated("Older Android callback")
        override fun onCharacteristicChanged(candidate:BluetoothGatt,characteristic:BluetoothGattCharacteristic){
            val bytes=characteristic.value?.clone() ?: return
            ui.post{if(current(candidate,token))port?.received(characteristic,bytes)}
        }
        override fun onCharacteristicChanged(candidate:BluetoothGatt,characteristic:BluetoothGattCharacteristic,value:ByteArray){
            val bytes=value.clone();ui.post{if(current(candidate,token))port?.received(characteristic,bytes)}
        }
    }
    fun submit(state:LightState,change:Change=Change.APPEARANCE):Boolean=if(closed)false else session.submit(state,change)
    fun cancelLighting()=session.cancelLighting()
    fun setForeground(value:Boolean){
        val cancelWaiting=rangeReconnect.setForeground(value)
        if(!value&&(cancelWaiting||automaticGatt||(connectionAttempt.active&&!session.ready)))disconnect()
        else if(value&&session.ready)connectingIdentity?.let{rangeReconnect.authenticated(it)}
    }
    fun disconnect(){
        rangeReconnect.stop()
        connectionAttempt.cancel();selectedForAttempt=null;selectedName="";expectedDescriptorForAttempt=null
        selectedIdentityForAttempt=null
        clearTransport()
        listener.onStatus("Disconnected",false,false)
    }
    private fun clearTransport(){
        generation++;stopScan();connectionTimeout?.let(ui::removeCallbacks);connectionTimeout=null
        memory.cancel();rememberAttempt=null
        connectingIdentity=null
        matchedModel=null
        val old=gatt;val ownedPort=port;gatt=null;port=null
        automaticGatt=false
        session.disconnect(false)
        if(ownedPort==null){try{old?.disconnect()}catch(_:Exception){};try{old?.close()}catch(_:Exception){}}
    }
    private fun fail(message:String){disconnect();listener.onError(message)}
    fun close(){if(!closed){disconnect();closed=true}}
}
