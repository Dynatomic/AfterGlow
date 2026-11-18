// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import java.util.UUID

/** Native connection opening is distinct from discovery and from protocol authentication. */
interface BleConnectionTransport {
    val transport: TransportType
    fun open(device: BluetoothDevice, autoConnect: Boolean, callbacks: BluetoothGattCallback): BluetoothGatt?
}
@SuppressLint("MissingPermission")
class BluetoothGattTransport(context: Context) : BleConnectionTransport {
    private val app = context.applicationContext
    override val transport = TransportType.BLE
    override fun open(device: BluetoothDevice, autoConnect: Boolean, callbacks: BluetoothGattCallback): BluetoothGatt? =
        device.connectGatt(app, autoConnect, callbacks, BluetoothDevice.TRANSPORT_LE)
}

/** One acknowledged GATT channel. It knows neither QC framing nor application lighting state. */
@SuppressLint("MissingPermission")
class GattSessionLink(
    private val connection: BluetoothGatt,
    private val writer: BluetoothGattCharacteristic,
    private val notifier: BluetoothGattCharacteristic,
    private val clock: SessionClock,
    private val owns: () -> Boolean
) : SessionLink {
    private var receive: ((ByteArray) -> Unit)? = null
    private var subscription: ((Boolean) -> Unit)? = null
    private var written: ((Boolean) -> Unit)? = null
    private var writeTimeout: Cancellation? = null
    private var ended = false
    private val cccd = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    override fun subscribe(receive: (ByteArray) -> Unit, complete: (Boolean) -> Unit) {
        this.receive = receive
        subscription = complete
        try {
            val descriptor = notifier.getDescriptor(cccd)
            if (ended || !owns() || descriptor == null || !connection.setCharacteristicNotification(notifier, true)) {
                finishSubscribe(false); return
            }
            val ok = if (Build.VERSION.SDK_INT >= 33)
                connection.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
            else { descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; connection.writeDescriptor(descriptor) }
            if (!ok) finishSubscribe(false)
        } catch (_: Exception) { finishSubscribe(false) }
    }
    private fun finishSubscribe(ok: Boolean) { val callback = subscription; subscription = null; callback?.invoke(ok) }
    fun descriptorWritten(descriptor: BluetoothGattDescriptor, ok: Boolean) {
        if (!ended && owns() && descriptor.characteristic.uuid == notifier.uuid && descriptor.uuid == cccd) finishSubscribe(ok)
    }
    fun received(characteristic: BluetoothGattCharacteristic, bytes: ByteArray) {
        if (!ended && owns() && characteristic.uuid == notifier.uuid) receive?.invoke(bytes)
    }
    override fun write(bytes: ByteArray, complete: (Boolean) -> Unit) {
        if (ended || written != null || !owns()) { complete(false); return }
        written = complete
        try {
            writer.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            val ok = if (Build.VERSION.SDK_INT >= 33)
                connection.writeCharacteristic(writer, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            else { writer.value = bytes; connection.writeCharacteristic(writer) }
            if (!ok) { finishWrite(false); return }
            writeTimeout = clock.later(5000) { if (!ended && owns()) finishWrite(false) }
        } catch (_: Exception) { finishWrite(false) }
    }
    fun written(characteristic: BluetoothGattCharacteristic, ok: Boolean) {
        if (!ended && owns() && characteristic.uuid == writer.uuid) finishWrite(ok)
    }
    private fun finishWrite(ok: Boolean) {
        writeTimeout?.cancel(); writeTimeout = null
        val callback = written; written = null; callback?.invoke(ok)
    }
    override fun close() {
        if (ended) return
        ended = true; receive = null; subscription = null; written = null
        writeTimeout?.cancel(); writeTimeout = null
        try { connection.setCharacteristicNotification(notifier, false) } catch (_: Exception) {}
        try { connection.disconnect() } catch (_: Exception) {}
        try { connection.close() } catch (_: Exception) {}
    }
}
