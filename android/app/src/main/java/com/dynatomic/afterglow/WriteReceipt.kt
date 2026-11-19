// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

/** A no-response write completes after local queue acceptance plus a pacing interval.
 * It does not prove device receipt. Native callbacks are ignored in that mode: a late
 * callback must never complete the following packet. Acknowledged QC writes still
 * require their native callback and fail on the existing five-second deadline.
 */
class WriteReceipt(private val clock:SessionClock,private val owns:()->Boolean) {
    private var completion:((Boolean)->Unit)?=null
    private var timer:Cancellation?=null
    private var mode=GattWriteMode.ACKNOWLEDGED
    private var generation=0L
    val pending get()=completion!=null
    fun accept(mode:GattWriteMode,complete:(Boolean)->Unit){
        check(!pending)
        this.mode=mode;completion=complete
        val token=++generation
        timer=clock.later(if(mode==GattWriteMode.WITHOUT_RESPONSE)40 else 5000){
            if(token==generation&&owns())finish(mode==GattWriteMode.WITHOUT_RESPONSE)
        }
    }
    fun acknowledge(ok:Boolean){if(mode==GattWriteMode.ACKNOWLEDGED&&owns())finish(ok)}
    private fun finish(ok:Boolean){
        timer?.cancel();timer=null
        val callback=completion;completion=null;callback?.invoke(ok)
    }
    fun cancel(){generation++;timer?.cancel();timer=null;completion=null}
}
