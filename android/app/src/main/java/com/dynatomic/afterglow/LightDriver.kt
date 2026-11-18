// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

data class DriverResponse(val command: Int, val payload: ByteArray)
data class AuthenticationRequest(val command: Int, val bytes: ByteArray, val phase: String)

/** Protocol boundary. Session scheduling and GATT callbacks do not construct wire packets. */
interface LightDriver {
    val id: String
    val capabilities: DeviceCapabilities
    fun beginAuthentication(): AuthenticationRequest
    fun response(bytes: ByteArray): DriverResponse
    /** null means authenticated; malformed or rejected exchanges throw IllegalArgumentException. */
    fun advanceAuthentication(request: AuthenticationRequest, response: DriverResponse): AuthenticationRequest?
    fun lighting(state: LightState, change: Change, restoreMode: Boolean = false): List<ByteArray>
}

object DriverRegistry {
    fun resolve(id: String): LightDriver? = if (id == DaybetterQcDriver.ID) DaybetterQcDriver else null
}
