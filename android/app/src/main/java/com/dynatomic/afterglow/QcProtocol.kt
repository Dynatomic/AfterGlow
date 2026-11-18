// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

enum class Effect(val label: String) {
    SOLID("Solid color"), FULL_WHEEL("Full color wheel"),
    FADE3("Fade · 3 colors"), FADE7("Fade · 7 colors"),
    JUMP3("Jump · 3 colors"), JUMP7("Jump · 7 colors"), STROBE7("Strobe · 7 colors"),
    MUSIC0("Music · mode 1"), MUSIC1("Music · mode 2"),
    MUSIC2("Music · mode 3"), MUSIC3("Music · mode 4");
    val isMusic get() = this in setOf(MUSIC0,MUSIC1,MUSIC2,MUSIC3)
    val isPreset get() = this != SOLID && this != FULL_WHEEL && !isMusic
    // Legacy metadata accessors; wire values are owned by the QC driver.
    @Deprecated("QC compatibility accessor; use the selected protocol driver.")
    val modeId get() = DaybetterQcDriver.modeId(this)
    @Deprecated("QC compatibility accessor; use the selected protocol driver.")
    val musicSub get() = DaybetterQcDriver.musicSub(this)
}
data class LightState(val color: Int = 0xffff8648.toInt(), val brightness: Int = 78,
    val on: Boolean = true, val effect: Effect = Effect.SOLID, val speed: Int = 50)
enum class Change { APPEARANCE, BRIGHTNESS, SPEED, FRAME, OFF }

/** Compatibility facade. The registered QC driver owns the wire protocol implementation. */
object QcProtocol {
    val chip get() = DaybetterQcDriver.chip
    fun crc(bytes: ByteArray) = DaybetterQcDriver.crc(bytes)
    fun frame(command: Int, vararg payload: Int) = DaybetterQcDriver.frame(command, *payload)
    data class Response(val command: Int, val payload: ByteArray)
    fun response(bytes: ByteArray): Response = DaybetterQcDriver.response(bytes).let { Response(it.command, it.payload) }
    fun challengeReply(payload: ByteArray) = DaybetterQcDriver.challengeReply(payload)
    fun accepted(payload: ByteArray) = DaybetterQcDriver.accepted(payload)
    fun lighting(state: LightState, change: Change, restoreMode: Boolean = false) = DaybetterQcDriver.lighting(state, change, restoreMode)
}
