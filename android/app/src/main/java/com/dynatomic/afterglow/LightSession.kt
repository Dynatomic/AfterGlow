// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

fun interface Cancellation { fun cancel() }
interface SessionClock { fun now():Long; fun later(delay:Long,action:()->Unit):Cancellation }
/** Protocol-neutral raw channel. Future transports must implement framing-free I/O here;
 * discovery and transport-specific signatures remain outside this session boundary.
 */
interface SessionLink {
    fun subscribe(receive:(ByteArray)->Unit,complete:(Boolean)->Unit)
    fun write(bytes:ByteArray,complete:(Boolean)->Unit)
    fun close()
}

/** Single-threaded state machine. Android callbacks and timers enter on the main looper. */
class LightSession(private val clock:SessionClock,private val status:(String,Boolean)->Unit,private val error:(String)->Unit,
    private val authenticationTimeout:((String)->Unit)?=null,private val failure:((String)->Unit)?=null,
    private val transportLost:((String)->Unit)?=null, private var driver:LightDriver=DaybetterQcDriver) {
    private var link:SessionLink?=null
    private var generation=0L
    private var epoch=0L
    private var timeout:Cancellation?=null
    private var pendingWrite:Cancellation?=null
    private var busy=false
    private var lastWriteAt=-1000L
    private var auth:Exchange?=null
    private var active:Action?=null
    private val queue=ArrayDeque<Action>()
    private var lastIntent:LightState?=null
    private var notificationsObserved=0
    var ready=false; private set
    val queuedActions get()=queue.size+(if(active==null)0 else 1)
    private data class Exchange(val request:AuthenticationRequest,var written:Boolean=false,var response:DriverResponse?=null)
    private data class Action(val frames:List<ByteArray>,val group:String,val epoch:Long,var index:Int=0)

    fun attach(next:SessionLink,driver:LightDriver=this.driver) {
        disconnect(false);this.driver=driver;link=next;val token=generation;notificationsObserved=0
        status("Authenticating · notifications",false)
        armAuthenticationTimeout(token,null)
        next.subscribe({bytes->if(owns(token))receive(bytes,token)},{ok->
            if(owns(token)){timeout?.cancel();if(!ok)fail("Could not enable light notifications") else exchange(driver.beginAuthentication(),token)}
        })
    }
    private fun owns(token:Long)=token==generation&&link!=null
    private fun armAuthenticationTimeout(token:Long,step:Exchange?){
        timeout?.cancel();timeout=clock.later(5000){
            if(owns(token)){
                val phase=if(step==null)"enabling notifications" else step.request.phase
                val detail=if(step==null)"subscription not confirmed" else
                    (if(step.written)"write confirmed" else "write confirmation pending")+", "+(if(step.response!=null)"response received" else "no response")
                val message="Authentication timed out ($phase: $detail; $notificationsObserved notifications)."
                disconnect(authenticationTimeout==null)
                if(authenticationTimeout!=null)authenticationTimeout.invoke(message) else error(message)
            }
        }
    }
    private fun exchange(request:AuthenticationRequest,token:Long){
        val step=Exchange(request);auth=step
        status("Authenticating · ${request.phase}",false)
        armAuthenticationTimeout(token,step)
        writePaced(request.bytes,token){ok->
            if(owns(token)&&auth===step){if(!ok)fail("Authentication write failed") else {step.written=true;advance(step,token)}}
        }
    }
    private fun receive(bytes:ByteArray,token:Long){
        notificationsObserved=minOf(999,notificationsObserved+1)
        val step=auth ?: return
        val parsed=try{driver.response(bytes)}catch(_:IllegalArgumentException){fail("Invalid authentication response");return}
        if(parsed.command!=step.request.command){fail("Unexpected authentication response");return}
        step.response=parsed;advance(step,token)
    }
    private fun advance(step:Exchange,token:Long){
        val response=step.response ?: return
        if(!step.written||!owns(token)||auth!==step)return
        timeout?.cancel();timeout=null
        val next=try{driver.advanceAuthentication(step.request,response)}catch(error:IllegalArgumentException){
            fail(error.message ?: "Unsupported authentication challenge");return
        }
        if(next!=null)exchange(next,token)
        else {auth=null;ready=true;lastIntent=null;status("Authenticated",true)}
    }
    private fun writePaced(bytes:ByteArray,token:Long,valid:()->Boolean={true},complete:(Boolean)->Unit){
        check(!busy);busy=true
        pendingWrite=clock.later(maxOf(0,40-(clock.now()-lastWriteAt))){
            pendingWrite=null
            if(!owns(token)){return@later}
            if(!valid()){busy=false;complete(true);return@later}
            lastWriteAt=clock.now();val current=link ?: return@later
            current.write(bytes){ok->if(owns(token)){busy=false;complete(ok)}}
        }
    }
    fun submit(state:LightState,change:Change=Change.APPEARANCE):Boolean {
        if(!ready||link==null){error("Connect a light first. Nothing was queued.");return false}
        val restore=lastIntent?.let{!it.on||it.brightness==0}==true
        if(!driver.capabilities.supports(state,change)){error("This light does not support that setting.");return false}
        val frames=try{driver.lighting(state,change,restore)}catch(_:IllegalArgumentException){error("Invalid light setting");return false}
        if(frames.isEmpty())return true
        if(change!=Change.FRAME)lastIntent=state
        val group=when(change){Change.FRAME->"frame";Change.SPEED->"speed";else->"appearance"}
        if(change==Change.APPEARANCE||change==Change.OFF||!state.on||state.brightness==0){
            // Superseding mode/power must cancel every remaining packet of older intent.
            cancelLighting()
        }
        queue.removeAll{it.group==group}
        if(queue.size>=12){error("Light is busy. Try the control again.");return false}
        queue.addLast(Action(frames,group,epoch));pump();return true
    }
    private fun pump(){
        if(!ready||busy||link==null)return
        var current=active
        if(current==null){current=queue.removeFirstOrNull() ?: return;active=current}
        if(current.epoch!=epoch||current.index>=current.frames.size){active=null;pump();return}
        val action=current;val token=generation
        writePaced(action.frames[action.index],token,{action.epoch==epoch&&active===action}){ok->
            if(!ok){
                val message="The light stopped accepting commands"
                if(ready&&transportLost!=null){disconnect(false);transportLost.invoke(message)}else fail(message)
                return@writePaced
            }
            if(active===action&&action.epoch==epoch)action.index++ else if(active===action)active=null
            pump()
        }
    }
    fun cancelLighting(){epoch++;queue.clear();active=null}
    fun disconnect(announce:Boolean=true){
        generation++;cancelLighting();ready=false;auth=null;lastIntent=null
        timeout?.cancel();timeout=null;pendingWrite?.cancel();pendingWrite=null;busy=false;lastWriteAt=-1000
        val old=link;link=null;old?.close();if(announce)status("Disconnected",false)
    }
    private fun fail(message:String){disconnect();if(failure!=null)failure.invoke(message) else error(message)}
}
