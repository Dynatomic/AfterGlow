// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import java.util.Locale
import java.util.UUID

/** WIFI is a future identity value, not an installed or usable transport.
 * Adding it requires discovery, transport, driver, and a typed network signature.
 * A row in this GATT catalog cannot register or implement WiFi support.
 */
enum class TransportType(val storageId: String) { BLE("bluetooth-le"), WIFI("wifi") }
object TransportRegistry {
    val registered: Set<TransportType> = setOf(TransportType.BLE)
    fun supports(type: TransportType) = type in registered
}

data class DeviceCapabilities(
    val power: Boolean,
    val brightness: Boolean,
    val color: Boolean,
    val foregroundColorStream: Boolean,
    val presets: Boolean,
    val controllerMicrophone: Boolean,
    val speed: Boolean
) {
    fun supports(state: LightState, change: Change): Boolean = when {
        !state.on || state.brightness == 0 || change == Change.OFF -> power
        change == Change.BRIGHTNESS -> brightness
        state.effect == Effect.SOLID -> color
        state.effect == Effect.FULL_WHEEL -> foregroundColorStream
        state.effect.isMusic -> controllerMicrophone
        else -> presets && (change != Change.SPEED || speed)
    }
}
enum class ImplementationEvidence { IMPLEMENTED, NOT_IMPLEMENTED }
enum class DocumentationEvidence { DOCUMENTED, NOT_DOCUMENTED }
enum class HardwareEvidence { NOT_VERIFIED, PARTIAL_REFERENCE, VERIFIED }
data class DeviceEvidence(
    val implementation: ImplementationEvidence,
    val documentation: DocumentationEvidence,
    val hardware: HardwareEvidence,
    val hardwareNotes: String? = null
)
data class GattSignature(val service: String, val write: String, val notify: String)
data class DeviceDescriptorRef(val transport: TransportType, val driverId: String, val modelId: String)
data class DeviceModelDescriptor(
    val id: String,
    val driverId: String,
    val transport: TransportType,
    val signature: GattSignature,
    val capabilities: DeviceCapabilities,
    val evidence: DeviceEvidence
) {
    fun reference() = DeviceDescriptorRef(transport, driverId, id)
}

/** Exact service/channel catalog only. Advertised names never identify a driver or model. */
object DeviceCatalog {
    val qcCapabilities = DeviceCapabilities(true, true, true, true, true, true, true)
    private val qcEvidence = DeviceEvidence(ImplementationEvidence.IMPLEMENTED,
        DocumentationEvidence.DOCUMENTED, HardwareEvidence.NOT_VERIFIED)
    private fun uuid(short: String) = "0000$short-0000-1000-8000-00805f9b34fb"
    private fun model(id: String, service: String, write: String, notify: String) = DeviceModelDescriptor(
        id, DaybetterQcDriver.ID, TransportType.BLE,
        GattSignature(uuid(service), uuid(write), uuid(notify)), qcCapabilities,
        if(id=="P001")qcEvidence.copy(hardware=HardwareEvidence.PARTIAL_REFERENCE,
            hardwareNotes="Reference evidence covers the exact signature, authentication, and one steady blue result only. Firmware variants and effects are not hardware verified.")
        else qcEvidence)
    val models: List<DeviceModelDescriptor> = listOf(
        model("P001", "ff10", "ff12", "ff11"),
        model("P01B", "e01b", "a01b", "f01b"),
        model("P026", "e026", "a026", "f026"),
        model("P02C", "e02c", "a02c", "f02c"),
        model("P031", "e031", "a031", "f031")
    )
    fun match(service: String, write: String, notify: String): DeviceModelDescriptor? {
        val signature = try { GattSignature(canonical(service), canonical(write), canonical(notify)) }
            catch (_: IllegalArgumentException) { return null }
        return models.firstOrNull { it.signature == signature }
    }
    fun resolve(reference: DeviceDescriptorRef): DeviceModelDescriptor? = models.firstOrNull {
        it.id == reference.modelId && it.driverId == reference.driverId && it.transport == reference.transport &&
            TransportRegistry.supports(it.transport)
    }
    private fun canonical(value: String): String = UUID.fromString(value).toString().lowercase(Locale.ROOT)
}
