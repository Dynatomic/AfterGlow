// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

/** DayBetter QC framing, authentication, and command encoder. No transport or scheduler. */
object DaybetterQcDriver : LightDriver {
    const val ID = "daybetter-qc"
    override val id = ID
    override val capabilities get() = DeviceCatalog.qcCapabilities
    fun modeId(effect: Effect): Int = when(effect) {
        Effect.FADE3 -> 11; Effect.FADE7 -> 12; Effect.JUMP3 -> 9; Effect.JUMP7 -> 10; Effect.STROBE7 -> 23
        Effect.MUSIC0,Effect.MUSIC1,Effect.MUSIC2,Effect.MUSIC3 -> 160
        else -> 1
    }
    fun musicSub(effect: Effect): Int = when(effect) {
        Effect.MUSIC0 -> 0; Effect.MUSIC1 -> 1; Effect.MUSIC2 -> 2; Effect.MUSIC3 -> 3; else -> -1
    }
    override fun beginAuthentication() = AuthenticationRequest(0, frame(0, *chip.map { it.toInt() and 255 }.toIntArray()), "challenge")
    override fun advanceAuthentication(request: AuthenticationRequest, response: DriverResponse): AuthenticationRequest? {
        require(response.command == request.command) { "Unexpected authentication response" }
        return when (request.command) {
            0 -> AuthenticationRequest(1, frame(1, *challengeReply(response.payload)), "confirmation")
            1 -> { require(accepted(response.payload)) { "Light rejected authentication" }; null }
            else -> throw IllegalArgumentException("Unsupported authentication exchange")
        }
    }
    val chip = "CCHIP".toByteArray(Charsets.US_ASCII)
    fun crc(bytes: ByteArray): Int {
        var crc=0xffff
        for(byte in bytes){crc=crc xor (byte.toInt() and 255);repeat(8){crc=if(crc and 1!=0)(crc ushr 1) xor 0xa001 else crc ushr 1}}
        return crc
    }
    fun frame(command: Int, vararg payload: Int): ByteArray {
        require(command in 0..255 && payload.size<=14 && payload.all { it in 0..255 })
        val body=byteArrayOf(0xa0.toByte(),command.toByte(),(payload.size+3).toByte())+payload.map{it.toByte()}.toByteArray()
        val sum=crc(body);return body+byteArrayOf(sum.toByte(),(sum ushr 8).toByte())
    }

    override fun response(bytes:ByteArray):DriverResponse {
        require(bytes.size in 5..20 && (bytes[0].toInt() and 255)==0xa1) { "Invalid response frame" }
        require((bytes[2].toInt() and 255)==bytes.size-2) { "Invalid response length" }
        val expected=(bytes[bytes.size-2].toInt() and 255) or ((bytes.last().toInt() and 255) shl 8)
        require(crc(bytes.copyOfRange(0,bytes.size-2))==expected) { "Invalid response checksum" }
        return DriverResponse(bytes[1].toInt() and 255,bytes.copyOfRange(3,bytes.size-2))
    }
    fun challengeReply(payload:ByteArray):IntArray {
        require(payload.size==7 && payload.copyOfRange(0,5).contentEquals(chip)) { "Unsupported authentication challenge" }
        return chip.map{it.toInt() and 255}.toIntArray()+intArrayOf((payload[5].toInt() and 255) xor 36,(payload[6].toInt() and 255) xor 76)
    }
    fun accepted(payload:ByteArray)=payload.size==6 && payload.copyOfRange(0,5).contentEquals(chip) && payload[5].toInt()==0
    override fun lighting(state:LightState,change:Change,restoreMode:Boolean):List<ByteArray> {
        require(state.brightness in 0..100 && state.speed in 1..100)
        val stop=listOf(frame(0x21,0),frame(0x1a,0))
        if(!state.on || state.brightness==0 || change==Change.OFF)return stop+frame(0x11,0)
        if(change==Change.FRAME){require(state.effect==Effect.FULL_WHEEL);return listOf(rgb(state.color))}
        if(change==Change.SPEED)return if(state.effect.isPreset)listOf(frame(0x16,state.speed)) else emptyList()
        if(change==Change.BRIGHTNESS && !restoreMode)return listOf(frame(0x11,1),frame(0x13,state.brightness))
        if(state.effect.isMusic)return listOf(frame(0x11,1),frame(0x12,modeId(state.effect),musicSub(state.effect)),frame(0x13,state.brightness),frame(0x1a,1),frame(0x21,1))
        val base=stop+frame(0x11,1)
        if(state.effect.isPreset)return base+listOf(frame(0x12,modeId(state.effect),255),frame(0x13,state.brightness),frame(0x16,state.speed))
        return base+listOf(frame(0x12,1,0),frame(0x13,state.brightness),rgb(state.color))
    }
    private fun rgb(color:Int)=frame(0x15,(color ushr 16) and 255,(color ushr 8) and 255,color and 255,0)
}
