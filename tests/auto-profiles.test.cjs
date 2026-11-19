// Radio-free automatic driver selection, strict saved identity, and custom override contracts.
const vm=require('node:vm');
const {readVariants,createFixture}=require('./helpers/dom-fixture.cjs');
(async()=>{const {sources,scripts}=readVariants();for(let variant=0;variant<sources.length;variant++){
const context=createFixture(sources[variant],scripts[variant]);await vm.runInContext(String.raw`(async()=>{
const stored=new Map();localStorage.setItem=(key,value)=>stored.set(key,value);localStorage.getItem=key=>stored.get(key)||null;
function response(cmd,payload){const bytes=qcFrame(cmd,payload);bytes[0]=0xa1;const sum=crc16(bytes.slice(0,-2));bytes[bytes.length-2]=sum&255;bytes[bytes.length-1]=sum>>>8;return bytes;}
function fake(kind,id,name,catalog='P001'){
 let handler;const stats={opens:0,writes:[],subscriptions:0},model=DeviceCatalog.find(catalog),write={properties:{write:true,writeWithoutResponse:kind!=='qc'},async writeValueWithResponse(bytes){stats.writes.push(Array.from(bytes));if(kind==='qc'){const reply=response(bytes[1],bytes[1]===0?[...CHIP,1,2]:[...CHIP,0]);queueMicrotask(()=>handler({target:{value:new DataView(reply.buffer)}}));}},async writeValueWithoutResponse(bytes){stats.writes.push(Array.from(bytes));}};
 const notify={properties:{notify:true},addEventListener(_,fn){handler=fn;},removeEventListener(){},async startNotifications(){stats.subscriptions++;},async stopNotifications(){}};
 const server={connected:false,async getPrimaryService(service){assert.equal(service,uuid('fff0'));return {async getCharacteristic(characteristic){assert.equal(characteristic,uuid('fff3'));return write;},async getCharacteristics(){return [{...write,uuid:uuid('fff3')}];}};},async getPrimaryServices(){return kind==='qc'?[{uuid:model.signature.service,async getCharacteristic(characteristic){return characteristic===model.signature.write?write:notify;}}]:[];}};
 const device={id,name,stats,addEventListener(){},removeEventListener(){},gatt:{async connect(){stats.opens++;server.connected=true;return server;},disconnect(){server.connected=false;}}};return device;
}
let picked,granted=[],pickers=0;navigator.bluetooth={async requestDevice(options){assert.equal(options.acceptAllDevices,true);pickers++;return picked;},async getDevices(){return granted;}};
// Every chooser selection overrides the previous built-in selection without manual profile work.
const bob=fake('elk','bob',' elk-bledob   '),dom=fake('elk','dom','ELK-BLEDOM   '),qc=fake('qc','qc','Smart Light');
protocol='elk-bledom';render();picked=bob;await $('connect').onclick();assert.equal(protocol,'elk-bledob');assert.equal($('protocolMode').value,protocol);assert.equal(stored.get('afterglow.protocol'),protocol);assert.equal(remembered.id,'bob');assert.equal(remembered.descriptor.driver,'elk-bledob');assert.equal(bob.stats.writes.length,0);
picked=dom;await $('connect').onclick();assert.equal(protocol,'elk-bledom');assert.equal($('protocolMode').value,protocol);assert.equal(remembered.id,'dom');assert.equal(dom.stats.writes.length,0);assert.equal(dom.stats.subscriptions,0);
picked=qc;await $('connect').onclick();assert.equal(protocol,'daybetter');assert.equal($('protocolMode').value,protocol);assert.equal(remembered.descriptor.catalog,'P001');assert.equal(qc.stats.writes.map(bytes=>bytes[1]).join(','),'0,1');assert.equal(qc.stats.subscriptions,1);assert.equal(queue.length,0);
// A failed or cancelled selection never replaces a successful profile/device record.
const previous=remembered,save=stored.get(REMEMBER_KEY),savedProfile=stored.get('afterglow.protocol');picked=fake('unknown','unknown','Govee_Unknown');await $('connect').onclick();assert.equal(live,null);assert.equal(selectedCandidate.status,'Not yet supported');assert.equal(picked.stats.writes.length,0);assert.equal(picked.stats.subscriptions,0);assert.equal(remembered,previous);assert.equal(stored.get(REMEMBER_KEY),save);assert.equal(stored.get('afterglow.protocol'),savedProfile);assert.equal(protocol,'daybetter');
navigator.bluetooth.requestDevice=async()=>{throw Object.assign(Error('Cancelled'),{name:'NotFoundError'});};await $('connect').onclick();assert.equal(remembered,previous);assert.equal(stored.get('afterglow.protocol'),savedProfile);
navigator.bluetooth.requestDevice=async()=>{pickers++;return picked;};
// A stale different built-in dropdown must not prevent exact saved-driver reconnect.
picked=dom;await $('connect').onclick();const domRecord=remembered;dropConnection();protocol='daybetter';render();granted=[dom];const beforePickers=pickers;startupAttempted=false;await reconnectAtStartup();assert.equal(pickers,beforePickers);assert.equal(protocol,'elk-bledom');assert.equal(remembered.id,'dom');assert.equal(dom.stats.writes.length,0);assert.equal(live.protocol,'elk-bledom');dropConnection();
// Same opaque device ID with a changed model cannot silently reassign a remembered driver.
const changed=fake('elk','dom','ELK-BLEDOB');granted=[changed];remembered=domRecord;const beforeMismatch=stored.get(REMEMBER_KEY);await connect({automatic:true});assert.equal(live,null);assert.equal(changed.stats.opens,0);assert.equal(changed.stats.writes.length,0);assert.equal(remembered,domRecord);assert.equal(stored.get(REMEMBER_KEY),beforeMismatch);
// Exact saved QC catalog mismatch rejects before notification subscription or CCHIP frames.
protocol='daybetter';remembered=previous;granted=[fake('qc','qc','Smart Light','P01B')];await connect({automatic:true});assert.equal(live,null);assert.equal(granted[0].stats.subscriptions,0);assert.equal(granted[0].stats.writes.length,0);assert.equal(remembered,previous);
// Legacy descriptors remain absent until the exact saved device is authenticated successfully.
remembered={version:1,id:'legacy',name:'Smart Light',protocol:'daybetter',custom:null,descriptor:null};granted=[fake('qc','legacy','Smart Light')];await connect({automatic:true});assert.equal(remembered.descriptor.catalog,'P001');assert.equal(granted[0].stats.writes.map(bytes=>bytes[1]).join(','),'0,1');dropConnection();
// Explicit custom service mappings are intentional and remain independent of advertised models.
protocol='custom';profile=validateProfile({service:'fff0',characteristic:'fff3',off:'00'});picked=bob;await $('connect').onclick();assert.equal(protocol,'custom');assert.equal(live.protocol,'custom');assert.equal(remembered.descriptor.driver,'custom-verified');assert.equal(bob.stats.writes.length,0);dropConnection();
if(toastTimer)clearTimeout(toastTimer);stopSpectrum();stopPresetPreview();
})()`,context);console.log('PASS auto profiles '+(variant?'standalone':'local')+': ELK/QC transitions, atomic ready identity, strict saved reconnect and custom override');}})().catch(error=>{console.error(error);process.exitCode=1;});
