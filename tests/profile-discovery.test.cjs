// Radio-free actual select-target mutation and browser chooser filter contracts.
const vm=require('node:vm');
const {readVariants,createFixture}=require('./helpers/dom-fixture.cjs');
(async()=>{const {sources,scripts}=readVariants();for(let variant=0;variant<sources.length;variant++){
const context=createFixture(sources[variant],scripts[variant]);await vm.runInContext(String.raw`(async()=>{
// Use the actual element: dropConnection renders and mutates this very target.
const stored=new Map();localStorage.setItem=(key,value)=>stored.set(key,value);
for(const value of ['elk-bledob','custom','daybetter','elk-bledom','elk-bledob']){
 const select=$('protocolMode');select.value=value;$('protocolMode').onchange({target:select});
 assert.equal(protocol,value);assert.equal(select.value,value);assert.equal(stored.get('afterglow.protocol'),value);assert.equal(live,null);assert.equal(queue.length,0);
}
const normal=Discovery.chooserOptions('daybetter',null);
assert.equal(normal.acceptAllDevices,true);assert.equal(normal.filters,undefined);
assert.equal(JSON.stringify(normal.optionalServices),JSON.stringify([...QC_PROFILES.map(row=>row[1]),uuid('fff0')]));
const elk=Discovery.chooserOptions('elk-bledob',null);assert.equal(JSON.stringify(elk),JSON.stringify(normal));
const custom=validateProfile({service:'abcd',characteristic:'abce',off:'00'});
assert.equal(Discovery.chooserOptions('custom',custom).acceptAllDevices,true);assert.equal(Discovery.chooserOptions('custom',custom).optionalServices.at(-1),uuid('abcd'));assert.equal(Discovery.chooserOptions('custom',emptyProfile()).acceptAllDevices,true);
// Main connection and Identify use the selected profile's constrained options.
let requests=[];navigator.bluetooth={async requestDevice(options){requests.push(options);throw Object.assign(Error('Test chooser cancelled'),{name:'NotFoundError'});}};
protocol='elk-bledob';await connect({chooseDifferent:true});assert.equal(JSON.stringify(requests[0]),JSON.stringify(elk));
protocol='custom';profile=custom;await $('connect').onclick();assert.equal(requests[1].acceptAllDevices,true);
profile=emptyProfile();await $('connect').onclick();assert.equal(requests.length,3);
// An explicitly verified custom protocol owns matching independently of the advertised name.
profile=custom;let customConnections=0,customWrites=0;
const customChar={uuid:custom.characteristic,properties:{write:true},async writeValueWithResponse(){customWrites++;}};
const customServer={connected:true,async getPrimaryService(id){assert.equal(id,custom.service);return {async getCharacteristics(){return [customChar];}};}};
const customDevice={id:'custom-prefix-device',name:'ELK-BLEDOB-other',addEventListener(){},removeEventListener(){},gatt:{async connect(){customConnections++;return customServer;},disconnect(){customServer.connected=false;}}};
navigator.bluetooth={async requestDevice(options){assert.equal(options.acceptAllDevices,true);assert(options.optionalServices.includes(custom.service));return customDevice;}};
await connect({chooseDifferent:true});assert.equal(customConnections,1);assert.equal(live.protocol,'custom');assert.equal(live.write,customChar);assert.equal(customWrites,0);assert.equal(remembered.descriptor.driver,'custom-verified');dropConnection();
// Unknown nearby devices remain visible after selection without protocol traffic or memory replacement.
const previous=remembered,previousSave=stored.get(REMEMBER_KEY);let unknownConnections=0,unknownWrites=0,unknownSubscriptions=0;
const unknownServer={connected:true,async getPrimaryServices(){return [{uuid:uuid('ffff')}];}};
const unknown={id:'unsupported-nearby',name:'Govee_H613B',addEventListener(){},removeEventListener(){},gatt:{async connect(){unknownConnections++;unknownServer.connected=true;return unknownServer;},disconnect(){unknownServer.connected=false;}}};
navigator.bluetooth={async requestDevice(options){assert.equal(options.acceptAllDevices,true);return unknown;}};
protocol='daybetter';await connect({chooseDifferent:true});assert.equal(unknownConnections,1);assert.equal(live,null);assert.equal(queue.length,0);assert.equal(selectedCandidate.status,'Not yet supported');assert.match($('candidateSupport').textContent,/Govee_H613B.*Not yet supported/);assert.equal($('candidateSupport').hidden,false);assert.equal(remembered,previous);assert.equal(stored.get(REMEMBER_KEY),previousSave);render();assert.match($('candidateSupport').textContent,/Not yet supported/);
// A known QC service with incomplete channels must not subscribe or authenticate.
unknown.name='Unknown channel';unknownServer.getPrimaryServices=async()=>[{uuid:uuid('ff10'),async getCharacteristic(id){return {properties:{write:id===uuid('ff12')},async startNotifications(){unknownSubscriptions++;},async writeValueWithResponse(){unknownWrites++;}};}}];
await connect({chooseDifferent:true});assert.equal(live,null);assert.equal(selectedCandidate.status,'Not yet supported');assert.equal(unknownWrites,0);assert.equal(unknownSubscriptions,0);assert.equal(remembered,previous);
const beforeUnknown=unknownConnections;
// Unknown variants still cannot select an encoder by a guessed family prefix.
unknown.name='ELK-BLEDXYZ';protocol='daybetter';await connect({chooseDifferent:true});assert.equal(unknownConnections,beforeUnknown);assert.equal(selectedCandidate.status,'Not yet supported');assert.equal(remembered,previous);
if(toastTimer)clearTimeout(toastTimer);
})()`,context);console.log('PASS profile/discovery '+(variant?'standalone':'local')+': real target survives render, exact persisted choice, broad chooser, honest candidate labels, unsupported no-command guard, custom');}})().catch(error=>{console.error(error);process.exitCode=1;});
