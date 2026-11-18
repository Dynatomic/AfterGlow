// Radio-free contracts for the inline standalone architecture.
const {readVariants,createFixture}=require('./helpers/dom-fixture.cjs');
const vm=require('node:vm');
const assert=require('node:assert');
const {sources,scripts}=readVariants();
const shared=s=>s.slice(0,s.indexOf('let appStopped=false;')).split('\n').filter(line=>!line.startsWith('if(!window.isSecureContext)')).join('\n');
assert.equal(shared(scripts[0]),shared(scripts[1]),'Shared standalone controllers drifted');
console.log('PASS 1: local and standalone shared module parity');
for(let variant=0;variant<sources.length;variant++){
 const context=createFixture(sources[variant],scripts[variant]);
 vm.runInContext(String.raw`
 assert.equal(DeviceCatalog.entries.length,5);
 const expected=[['P001','ff10','ff12','ff11'],['P01B','e01b','a01b','f01b'],['P026','e026','a026','f026'],['P02C','e02c','a02c','f02c'],['P031','e031','a031','f031']];
 for(const [id,service,write,notify]of expected){const e=DeviceCatalog.match(uuid(service),uuid(write),uuid(notify));assert(e);assert.equal(e.id,id);assert.equal(e.driver,'daybetter-qc');assert.equal(e.transport,'bluetooth-le');assert.equal(e.capabilities.controllerMicrophone,true);assert.equal(e.capabilities.foregroundColorStream,true);assert(Object.isFrozen(e.capabilities));}
 assert.equal(DeviceCatalog.match(uuid('ff10'),uuid('ff11'),uuid('ff12')),null);assert.equal(DeviceCatalog.match(uuid('fff0'),uuid('ff12'),uuid('ff11')),null);
 assert.match(DeviceCatalog.find('P001').evidence.hardware,/individual effects not verified/);assert.equal(DeviceCatalog.find('P01B').evidence.hardware,'Not hardware verified');
 assert.throws(()=>Discovery.provider('wifi'),/Unsupported/);assert.throws(()=>TransportRegistry.get('wifi'),/Unsupported/);assert.throws(()=>DriverRegistry.byId('guessed-brand'),/Unsupported/);
 assert.equal(DriverRegistry.byId('daybetter-qc'),QcDriver);assert.equal(DriverRegistry.forProtocol('custom'),CustomProfileDriver);
 const legacy={version:1,id:'opaque-local-browser-identity',name:'Smart Light',protocol:'daybetter',custom:null};
 assert.equal(validateRemembered(legacy).descriptor,null);
 const descriptor={transport:'bluetooth-le',driver:'daybetter-qc',catalog:'P001'};
 assert.equal(validateRemembered({...legacy,descriptor}).descriptor.catalog,'P001');
 for(const d of [{...descriptor,transport:'wifi'},{...descriptor,driver:'guessed'},{...descriptor,catalog:'unknown'}])assert.throws(()=>validateRemembered({...legacy,descriptor:d}),/driver reference/);
 const custom=validateProfile({service:'fff0',characteristic:'fff1',color:'aa {r} {g} {b}',on:'01',off:'00'});
 assert.equal(CapabilityAdapter.forProfile('custom',custom).presets,false);assert.equal(CapabilityAdapter.forProfile('custom',custom).brightness,true);assert.equal(CapabilityAdapter.forProfile('custom',custom).controllerMicrophone,false);
 assert.equal(CapabilityAdapter.permits('daybetter',custom,'unknown'),false);assert.equal(CapabilityAdapter.permits('custom',{off:'00'},'off'),true);assert.equal(CapabilityAdapter.permits('custom',{off:'00'},'on'),false);assert.equal(CapabilityAdapter.permits('custom',{music:'01'},'music'),true);assert.equal(CapabilityAdapter.permits('custom',{music:'01'},'effect'),false);
 assert.throws(()=>validateRemembered({...legacy,protocol:'custom',custom,descriptor}),/driver reference/);
 const migrated=validateRemembered({...legacy,protocol:'custom',custom,descriptor:{transport:'bluetooth-le',driver:'custom-verified',catalog:null}});assert.equal(migrated.descriptor.driver,'custom-verified');
 // Names and chooser results never assign a protocol. Only ready QC sessions are persisted.
 let saves=0;localStorage.setItem=()=>saves++;session++;server={connected:true};const c={token:session,device:{id:'exact-ready-id',name:'Any advertised name'},protocol:'daybetter',custom:null,write:{},authenticated:false,descriptor};live=c;rememberReady(c);assert.equal(saves,0);c.authenticated=true;rememberReady(c);assert.equal(saves,1);assert.equal(remembered.descriptor.catalog,'P001');assert.equal(remembered.id,'exact-ready-id');live=null;
 // The transport forwards opaque bytes to the selected driver: custom traffic must not enter QC decoding.
 const customConnection={token:session,protocol:'custom'};live=customConnection;const before=events.length;BluetoothTransport.handleNotification(customConnection,{target:{value:new DataView(Uint8Array.from([1,2,3]).buffer)}});assert.equal(events.length,before);live=null;
 assert(!ConnectionSession.exchange);assert.equal(typeof QcDriver.exchange,'function');assert(!/parseQcFrame|qcFrame/.test(String(BluetoothTransport.handleNotification)));assert(!/parseQcFrame|qcFrame/.test(String(ConnectionSession.pump)));
 `,context);
 console.log('PASS '+(variant+2)+': '+(variant?'standalone':'local')+' exact catalog, unsupported boundary, capabilities/evidence, storage migration and authenticated persistence');
}
