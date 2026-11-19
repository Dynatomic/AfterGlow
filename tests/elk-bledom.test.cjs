// Radio-free exact ELK-BLEDOM protocol, routing and lifecycle contracts for both distributions.
const {readVariants,createFixture}=require('./helpers/dom-fixture.cjs');
const vm=require('node:vm');
(async()=>{const {sources,scripts}=readVariants();for(let variant=0;variant<sources.length;variant++)for(const withResponse of [false,true]){
const context=createFixture(sources[variant],scripts[variant]);context.withResponse=withResponse;
await vm.runInContext(String.raw`(async()=>{
const hex=b=>Array.from(b,v=>v.toString(16).padStart(2,'0')).join('');
const state={on:true,brightness:60,color:'#123456',effect:'solid',speed:42};
assert.equal(hex(BledomDriver.lighting('on',state)[0]),'7e0404010001ff00ef');
assert.equal(hex(BledomDriver.lighting('off',state)[0]),'7e0404000000ff00ef');
assert.equal(hex(BledomDriver.lighting('brightness',state).at(-1)),'7e04013cffffff00ef');
assert.equal(hex(BledomDriver.lighting('brightness',state)[0]),'7e0404010001ff00ef');
assert.equal(hex(BledomDriver.lighting('speed',state)[0]),'7e04022affffff00ef');
assert.equal(hex(BledomDriver.lighting('color',state).at(-1)),'7e07050312345610ef');
for(const [effect,id]of Object.entries({jump3:'87',jump7:'88',fade3:'89',fade7:'8a',strobe7:'95'}))assert.equal(hex(BledomDriver.lighting('effect',{...state,effect})[1]),'7e0503'+id+'03ffff00ef');
assert.equal(hex(BledomDriver.lighting('color',{...state,brightness:0})[0]),'7e0404000000ff00ef');
assert.throws(()=>BledomDriver.lighting('brightness',{...state,brightness:101}));assert.throws(()=>BledomDriver.lighting('speed',{...state,speed:0}));assert.throws(()=>BledomDriver.lighting('effect',{...state,effect:'music0'}),/not implemented/);
assert.equal(DriverRegistry.forProtocol('elk-bledom'),BledomDriver);assert.equal(CapabilityAdapter.forProfile('elk-bledom',null).controllerMicrophone,false);assert.equal(CapabilityAdapter.permits('elk-bledom',null,'music'),false);
assert.equal(Discovery.optionalServices('daybetter',null).includes(uuid('fff0')),true);
assert.equal(Discovery.matchService([{uuid:uuid('fff0')}]),null,'FFF0 must never route through QC');assert.equal(DriverRegistry.byId('elk-bledom'),BledomDriver);assert.equal(hex(ElkDriver.lighting('on',state)[0]),'7e0704ff00010201ef','BLEDOB framing stays separate');
let requested=[],writes=[],connects=0;const channel={properties:withResponse?{write:true}:{writeWithoutResponse:true},async writeValueWithResponse(bytes){assert(withResponse);writes.push(hex(bytes));},async writeValueWithoutResponse(bytes){assert(!withResponse);writes.push(hex(bytes));}};
const con={connected:false,async getPrimaryService(id){assert.equal(id,uuid('fff0'));return {async getCharacteristic(id){assert.equal(id,uuid('fff3'));return channel;}}}};
const selected={id:'elk-exact-browser-id',name:'ELK-BLEDOM       ',addEventListener(){},removeEventListener(){},gatt:{async connect(){connects++;con.connected=true;return con;},disconnect(){con.connected=false;}}};
navigator.bluetooth={async requestDevice(options){requested.push(options);return selected;},async getDevices(){return [selected];}};
protocol='daybetter';await connect();assert.equal(protocol,'elk-bledom');assert(lightReady(live));assert.equal(live.authenticated,false);assert.equal(live.notify,undefined);assert.equal(writes.length,0,'Connect must never send lighting/auth/time packets');assert.match($('btStatus').textContent,/Ready/);assert(!/Authenticated/.test($('btStatus').textContent));assert.equal(remembered.descriptor.driver,'elk-bledom');assert.equal(matchingRemembered(),true);
assert.throws(()=>validateRemembered({...remembered,descriptor:{transport:'bluetooth-le',driver:'elk-bledob',catalog:'ELK-BLEDOB'}}),/driver reference/);const saved=validateRemembered(remembered);assert.equal(saved.descriptor.catalog,'ELK-BLEDOM');assert.throws(()=>validateRemembered({...saved,descriptor:{...saved.descriptor,driver:'daybetter-qc'}}),/driver reference/);
dropConnection();remembered=saved;await connect({automatic:true});assert.equal(connects,2);assert.equal(requested.length,1);assert.equal(writes.length,0,'Remembered reconnect must never replay settings');
$('effectSelect').options=[{value:'solid'},{value:'music0'},{value:'fade3'}];renderEffects();assert.equal($('effectSelect').options[1].hidden,true);assert.equal($('music').hidden,true);assert.equal($('effectSelect').options[2].hidden,false);
selectedEffect='solid';desired={color:'#123456',brightness:60,on:true,name:'test'};effectSpeed=42;assert(send('color'));
const until=Date.now()+1500;while(writing){assert(Date.now()<until);await new Promise(r=>setTimeout(r,5));}assert.deepEqual(writes,['7e0404010001ff00ef','7e04013cffffff00ef','7e07050312345610ef']);
// Full wheel uses the ELK ready channel and RGB-only ticks without an authentication flag.
writes=[];selectedEffect='spectrum';assert(startSpectrum());while(writing)await new Promise(r=>setTimeout(r,5));assert(spectrumRunning);stopSpectrum();spectrumRunning=true;spectrumSession=live;spectrumLastTime=Date.now()-200;const generation=spectrumGeneration;spectrumTick(generation);assert(spectrumRunning);stopSpectrum();await new Promise(r=>setTimeout(r,70));
// A newer off command interrupts the remaining packets of a pending color sequence.
selectedEffect='solid';writes=[];live.lastWrite=Date.now();assert(send('color'));desired.on=false;assert(send('off'));while(writing)await new Promise(r=>setTimeout(r,5));assert.deepEqual(writes,['7e0404000000ff00ef']);desired.on=true;
// An in-flight sequence cannot continue on a disconnected or superseded session.
writes=[];live.lastWrite=Date.now();assert(send('color'));dropConnection();await new Promise(r=>setTimeout(r,70));assert.equal(writes.length,0);
assert.equal(send('color'),false);assert.equal(queue.length,0);
// Padded real advertisement succeeds with explicit ELK selection, not just automatic family routing.
protocol='elk-bledom';const beforeExplicit=writes.length;await connect({chooseDifferent:true});assert(lightReady(live));assert.equal(writes.length,beforeExplicit);dropConnection();

// Browser namePrefix filters may offer near matches: reject before opening GATT or sending bytes.
selected.name='ELK-BLEDOM-other';const beforeReject=connects;await connect({chooseDifferent:true});assert.equal(live,null);assert.equal(connects,beforeReject);assert.equal(writes.length,beforeExplicit);
protocol='daybetter';await connect({chooseDifferent:true});assert.equal(live,null);assert.equal(connects,beforeReject);selected.name='ELK-BLEDOM       ';

// The exact name alone does not establish compatibility; the expected channel/properties remain mandatory.
session++;server={connected:true};let candidate={token:session,device:{name:'ELK-BLEDOB'},protocol:'elk-bledom'};live=candidate;await assert.rejects(BledomDriver.prepare(candidate,con),/exact advertised model/);
candidate.device.name='ELK-BLEDOM';await assert.rejects(BledomDriver.prepare(candidate,{async getPrimaryService(){throw Error('missing service');}}),/missing service/);
await assert.rejects(BledomDriver.prepare(candidate,{async getPrimaryService(){return {async getCharacteristic(){return {properties:{}};}};}}),/writable characteristic/);
live=null;server=null;if(toastTimer)clearTimeout(toastTimer);stopSpectrum();stopPresetPreview();
})()`,context);
console.log('PASS BLEDOM '+(variant?'standalone':'local')+' '+(withResponse?'with-response':'without-response')+': packet vectors, safe routing, no-auth/no-notify connection, no replay reconnect, unsupported music and disconnect cancellation');
}})().catch(error=>{console.error(error);process.exitCode=1;});
