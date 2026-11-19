// Radio-free exact ELK-BLEDOB protocol, routing and lifecycle contracts for both distributions.
const {readVariants,createFixture}=require('./helpers/dom-fixture.cjs');
const vm=require('node:vm');
(async()=>{const {sources,scripts}=readVariants();for(let variant=0;variant<sources.length;variant++){
const context=createFixture(sources[variant],scripts[variant]);
await vm.runInContext(String.raw`(async()=>{
const hex=b=>Array.from(b,v=>v.toString(16).padStart(2,'0')).join('');
const state={on:true,brightness:60,color:'#123456',effect:'solid',speed:42};
assert.equal(hex(ElkDriver.lighting('on',state)[0]),'7e0704ff00010201ef');
assert.equal(hex(ElkDriver.lighting('off',state)[0]),'7e07040000000201ef');
assert.equal(hex(ElkDriver.lighting('brightness',state).at(-1)),'7e04013c01ff0201ef');
assert.equal(hex(ElkDriver.lighting('brightness',state)[0]),'7e0704ff00010201ef');
assert.equal(hex(ElkDriver.lighting('speed',state)[0]),'7e07022affffff00ef');
assert.equal(hex(ElkDriver.lighting('color',state).at(-1)),'7e07050312345610ef');
for(const [effect,id]of Object.entries({jump3:'87',jump7:'88',fade3:'89',fade7:'8a',strobe7:'95'}))assert.equal(hex(ElkDriver.lighting('effect',{...state,effect})[1]),'7e0703'+id+'03ffff00ef');
assert.equal(hex(ElkDriver.lighting('color',{...state,brightness:0})[0]),'7e07040000000201ef');
assert.throws(()=>ElkDriver.lighting('brightness',{...state,brightness:101}));assert.throws(()=>ElkDriver.lighting('speed',{...state,speed:0}));assert.throws(()=>ElkDriver.lighting('effect',{...state,effect:'music0'}),/not implemented/);
assert.equal(DriverRegistry.forProtocol('elk-bledob'),ElkDriver);assert.equal(CapabilityAdapter.forProfile('elk-bledob',null).controllerMicrophone,false);assert.equal(CapabilityAdapter.permits('elk-bledob',null,'music'),false);
assert.equal(Discovery.optionalServices('daybetter',null).includes(uuid('fff0')),true);
assert.equal(Discovery.matchService([{uuid:uuid('fff0')}]),null,'FFF0 must never route through QC');
let requested=[],writes=[],connects=0;const channel={properties:{writeWithoutResponse:true},async writeValueWithoutResponse(bytes){writes.push(hex(bytes));}};
const con={connected:false,async getPrimaryService(id){assert.equal(id,uuid('fff0'));return {async getCharacteristic(id){assert.equal(id,uuid('fff3'));return channel;}}}};
const selected={id:'elk-exact-browser-id',name:'ELK-BLEDOB       ',addEventListener(){},removeEventListener(){},gatt:{async connect(){connects++;con.connected=true;return con;},disconnect(){con.connected=false;}}};
navigator.bluetooth={async requestDevice(options){requested.push(options);return selected;},async getDevices(){return [selected];}};
protocol='daybetter';await connect();assert.equal(protocol,'elk-bledob');assert(lightReady(live));assert.equal(live.authenticated,false);assert.equal(live.notify,undefined);assert.equal(writes.length,0,'Connect must never send lighting/auth/time packets');assert.match($('btStatus').textContent,/Ready/);assert(!/Authenticated/.test($('btStatus').textContent));assert.equal(remembered.descriptor.driver,'elk-bledob');assert.equal(matchingRemembered(),true);
const saved=validateRemembered(remembered);assert.equal(saved.descriptor.catalog,'ELK-BLEDOB');assert.throws(()=>validateRemembered({...saved,descriptor:{...saved.descriptor,driver:'daybetter-qc'}}),/driver reference/);
dropConnection();remembered=saved;await connect({automatic:true});assert.equal(connects,2);assert.equal(requested.length,1);assert.equal(writes.length,0,'Remembered reconnect must never replay settings');
$('effectSelect').options=[{value:'solid'},{value:'music0'},{value:'fade3'}];renderEffects();assert.equal($('effectSelect').options[1].hidden,true);assert.equal($('music').hidden,true);assert.equal($('effectSelect').options[2].hidden,false);
selectedEffect='solid';desired={color:'#123456',brightness:60,on:true,name:'test'};effectSpeed=42;assert(send('color'));
const until=Date.now()+1500;while(writing){assert(Date.now()<until);await new Promise(r=>setTimeout(r,5));}assert.deepEqual(writes,['7e0704ff00010201ef','7e04013c01ff0201ef','7e07050312345610ef']);
// Full wheel uses the ELK ready channel and RGB-only ticks without an authentication flag.
writes=[];selectedEffect='spectrum';assert(startSpectrum());while(writing)await new Promise(r=>setTimeout(r,5));assert(spectrumRunning);stopSpectrum();spectrumRunning=true;spectrumSession=live;spectrumLastTime=Date.now()-200;const generation=spectrumGeneration;spectrumTick(generation);assert(spectrumRunning);stopSpectrum();await new Promise(r=>setTimeout(r,70));
// A newer off command interrupts the remaining packets of a pending color sequence.
selectedEffect='solid';writes=[];live.lastWrite=Date.now();assert(send('color'));desired.on=false;assert(send('off'));while(writing)await new Promise(r=>setTimeout(r,5));assert.deepEqual(writes,['7e07040000000201ef']);desired.on=true;
// An in-flight sequence cannot continue on a disconnected or superseded session.
writes=[];live.lastWrite=Date.now();assert(send('color'));dropConnection();await new Promise(r=>setTimeout(r,70));assert.equal(writes.length,0);
assert.equal(send('color'),false);assert.equal(queue.length,0);
// Padded real advertisement succeeds with explicit ELK selection, not just automatic family routing.
protocol='elk-bledob';const beforeExplicit=writes.length;await connect({chooseDifferent:true});assert(lightReady(live));assert.equal(writes.length,beforeExplicit);dropConnection();
// Browser namePrefix filters may offer near matches: reject before opening GATT or sending bytes.
selected.name='ELK-BLEDOB-other';const beforeReject=connects;await connect({chooseDifferent:true});assert.equal(live,null);assert.equal(connects,beforeReject);assert.equal(writes.length,beforeExplicit);
protocol='daybetter';await connect({chooseDifferent:true});assert.equal(live,null);assert.equal(connects,beforeReject);selected.name='ELK-BLEDOB       ';

// The exact name alone does not establish compatibility; the expected channel/properties remain mandatory.
session++;server={connected:true};let candidate={token:session,device:{name:'ELK-BLEDOM'},protocol:'elk-bledob'};live=candidate;await assert.rejects(ElkDriver.prepare(candidate,con),/exact advertised name/);
candidate.device.name='ELK-BLEDOB';await assert.rejects(ElkDriver.prepare(candidate,{async getPrimaryService(){throw Error('missing service');}}),/missing service/);
await assert.rejects(ElkDriver.prepare(candidate,{async getPrimaryService(){return {async getCharacteristic(){return {properties:{write:true}};}};}}),/write without response/);
live=null;server=null;if(toastTimer)clearTimeout(toastTimer);stopSpectrum();stopPresetPreview();
})()`,context);
console.log('PASS ELK '+(variant?'standalone':'local')+': packet vectors, safe routing, no-auth/no-notify connection, no replay reconnect, unsupported music and disconnect cancellation');
}})().catch(error=>{console.error(error);process.exitCode=1;});
