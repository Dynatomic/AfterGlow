// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

/** Independent encoder of ELK-BLEDOM protocol facts; BLEDOB has a different driver.
 * Reference: github.com/smarthomesven/homey-duoco-strip, drivers/led-strip/device.js.
 * No authentication, notification subscription, clock/timer or connection-time writes.
 */
object ElkBledomDriver : LightDriver {
    const val ID = "elk-bledom"
    override val id = ID
    override val capabilities get() = DeviceCatalog.elkCapabilities
    override val requiresAuthentication = false
    override fun beginAuthentication(): AuthenticationRequest? = null
    override fun response(bytes: ByteArray): DriverResponse = throw IllegalArgumentException("ELK has no response protocol")
    override fun advanceAuthentication(request: AuthenticationRequest, response: DriverResponse): AuthenticationRequest? =
        throw IllegalArgumentException("ELK does not authenticate")
    private fun packet(vararg bytes: Int) = bytes.map { it.toByte() }.toByteArray()
    private fun power(on: Boolean) = packet(0x7e,4,4,if(on)1 else 0,0,if(on)1 else 0,255,0,0xef)
    private fun rgb(color: Int) = packet(0x7e,7,5,3,(color ushr 16) and 255,(color ushr 8) and 255,color and 255,0x10,0xef)
    private fun brightness(value: Int) = packet(0x7e,4,1,value,255,255,255,0,0xef)
    private fun speed(value: Int) = packet(0x7e,4,2,value,255,255,255,0,0xef)
    private fun preset(effect: Effect): ByteArray {
        val id=when(effect){Effect.JUMP3->0x87;Effect.JUMP7->0x88;Effect.FADE3->0x89;Effect.FADE7->0x8a;Effect.STROBE7->0x95
            else->throw IllegalArgumentException("Unsupported ELK effect")}
        return packet(0x7e,5,3,id,3,255,255,0,0xef)
    }
    override fun lighting(state: LightState, change: Change, restoreMode: Boolean): List<ByteArray> {
        require(state.brightness in 0..100 && state.speed in 1..100)
        if(!state.on || state.brightness==0 || change==Change.OFF)return listOf(power(false))
        require(!state.effect.isMusic){"ELK microphone control is not documented"}
        if(change==Change.FRAME){require(state.effect==Effect.FULL_WHEEL);return listOf(rgb(state.color))}
        if(change==Change.SPEED)return if(state.effect.isPreset)listOf(speed(state.speed)) else emptyList()
        // Brightness restores power while preserving the controller's current mode.
        if(change==Change.BRIGHTNESS)return listOf(power(true),brightness(state.brightness))
        val mode=if(state.effect.isPreset)listOf(preset(state.effect),speed(state.speed)) else listOf(rgb(state.color))
        return listOf(power(true))+mode+brightness(state.brightness)
    }
}
