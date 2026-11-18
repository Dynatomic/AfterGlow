const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const source=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');const code=source.match(/<script>([\s\S]*?)<\/script>/)[1];
const literalIds=new Set([...source.matchAll(/\bid="([^"]+)"/g)].map(match=>match[1]));for(const [,id]of code.matchAll(/\$\('([^']+)'\)/g))assert(literalIds.has(id),'Missing literal DOM ID: '+id);assert(!/commandStatus|class="statusbar"|<footer>/.test(source));new vm.Script(code);assert(!/id="(?:previewMode|hardwareMode|modeHelp)"|class="page-heading"|Your space|Visual preview/.test(fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8')));console.log('PASS 1: syntax and simplified layout contract');
const els=new Map();function el(id){if(!els.has(id))els.set(id,{value:'',textContent:'',innerHTML:'',style:{setProperty(){}},getBoundingClientRect(){return {left:0,top:0,width:200,height:200}},setPointerCapture(){},releasePointerCapture(){},classList:{add(){},remove(){},contains(){return true},toggle(){}},setAttribute(){},scrollIntoView(){},append(){},click(){},options:[],files:[]});return els.get(id)}
const context=vm.createContext({assert,console,document:{getElementById:el,documentElement:{style:{setProperty(){}}},querySelectorAll(){return []},addEventListener(){},createElement(){return el(Math.random())}},window:{isSecureContext:true},navigator:{},localStorage:{getItem(){return null},setItem(){}},setTimeout,clearTimeout,queueMicrotask,AbortController,BroadcastChannel:class {postMessage(value){this.lastMessage=value}},URL,Blob,Uint8Array,DataView,Date});
vm.runInContext(code,context);
const tests=String.raw`(async()=>{
const hex=b=>Array.from(b).map(v=>v.toString(16).padStart(2,'0')).join('');
for(const [cmd,payload,expected]of [[17,[1],'a0110401b121'],[17,[0],'a011040070e1'],[18,[1,0],'a012050100b0f0'],[19,[80],'a0130450d11d'],[21,[255,0,0,0],'a01507ff0000003c1b'],[0,CHIP,'a000084343484950acca']])assert.equal(hex(qcFrame(cmd,payload)),expected);
console.log('PASS 2: six supplied CRC vectors');
function response(cmd,payload){const out=qcFrame(cmd,payload);out[0]=0xa1;const crc=crc16(out.slice(0,-2));out[out.length-2]=crc&255;out[out.length-1]=crc>>>8;return out;}
assert.equal(parseQcFrame(response(0,[...CHIP,1,2])).payload.length,7);
for(const bad of [new Uint8Array(),qcFrame(0,CHIP),response(0,CHIP).slice(1),(()=>{const a=response(0,CHIP);a[3]^=1;return a})()])assert.throws(()=>parseQcFrame(bad));
console.log('PASS 3: response framing and invalid frames');
profile=validateProfile({service:'fff0',color:'AA {r} {g} {b} FF'});desired.color='#ff8040';desired.brightness=50;assert.equal(hex(bytesFor('color')),'aa804020ff');
for(const value of ['abc','{r}{g}{x}','{r}{g}{b}zz'])assert.throws(()=>packet(value,true));assert.throws(()=>uuid('garbage'));assert.throws(()=>validateProfile({color:'{r}{g}{b}'}));
console.log('PASS 4: custom validation and scaling');
function setup(reply){mode='hardware';session++;server={connected:true};const c={token:session,protocol:'daybetter',authenticated:false,profile:'P001',write:{properties:{write:true},async writeValueWithResponse(data){c.calls.push(Array.from(data));if(reply){const answer=reply(data);if(answer){const value=new DataView(answer.buffer);queueMicrotask(()=>handleNotification(c,{target:{value}}));}}}},calls:[]};live=c;characteristic=c.write;return c;}
async function awaitMockWrites(c,count){const until=Date.now()+1500;while(c.calls.length<count||writing){assert(Date.now()<until,'Mock writer did not finish');await new Promise(resolve=>setTimeout(resolve,5));}}
let c=setup(data=>response(data[1],data[1]===0?[...CHIP,0x12,0x34]:[...CHIP,0]));await authenticate(c,300);assert(c.authenticated);assert.equal(c.calls.length,2);assert.equal(c.calls[1][8],0x12^36);assert.equal(c.calls[1][9],0x34^76);
console.log('PASS 5: notification-driven successful authentication');
c=setup(data=>response(data[1],data[1]===0?[...CHIP,1,2]:[...CHIP,1]));await assert.rejects(authenticate(c,300),/rejected/);assert(!c.authenticated);
c=setup(data=>response(0,[0,0,0,0,0,1,2]));await assert.rejects(authenticate(c,300),/prefix/);
c=setup(()=>null);await assert.rejects(authenticate(c,15),/timed out/);assert(!c.authenticated);assert.equal(c.pending,null);
console.log('PASS 6: rejected, bad-prefix and timed-out authentication');
c=setup(()=>null);desired.brightness=25;send('color');await new Promise(r=>setTimeout(r,10));assert.equal(c.calls.length,0);assert.equal(queue.length,0);dropConnection('Disconnected guard');send('color');assert.equal(queue.length,0);console.log('PASS 7: unauthenticated/disconnected changes never write or buffer');
c=setup(()=>null);c.authenticated=true;mode='hardware';desired={color:'#0000ff',brightness:25,on:true,name:'Blue'};send('color');desired.color='#ff0000';send('color');desired.color='#00ff00';send('color');assert.equal(queue.length,1);await awaitMockWrites(c,12);assert.equal(c.calls.length,12);assert.equal(c.calls.map(v=>v[1]).join(','),'33,26,17,18,19,21,33,26,17,18,19,21');assert.equal(c.calls[11].slice(3,7).join(','),'0,255,0,0');assert.equal(c.calls[4][3],25);
console.log('PASS 8: atomic serial sequences and latest-color coalescing');
desired.brightness=0;send('color');await awaitMockWrites(c,15);assert.equal(desired.on,false);assert.equal(c.calls.at(-1)[3],0);desired.brightness=20;send('color');await awaitMockWrites(c,21);assert.equal(desired.on,true);assert.equal(c.calls.at(-4)[3],1);
console.log('PASS 9: zero brightness off and positive restores on');
c=setup(()=>null);c.authenticated=true;mode='hardware';let release;c.write.writeValueWithResponse=data=>{c.calls.push(Array.from(data));return new Promise(r=>release=r)};send('color');assert.equal(c.calls.length,1);dropConnection('Test disconnect');release();await new Promise(r=>setTimeout(r,80));assert.equal(c.calls.length,1);assert.equal(queue.length,0);assert.equal(live,null);
console.log('PASS 10: disconnect cancels remaining packets');

dropConnection('Connect contract setup');send('color');assert.equal(queue.length,0);
let notifyHandler;const connectionCalls=[];const write={uuid:uuid('ff12'),properties:{write:true},async writeValueWithResponse(data){connectionCalls.push(Array.from(data));if(data[1]===0||data[1]===1){const answer=response(data[1],data[1]===0?[...CHIP,1,2]:[...CHIP,0]);queueMicrotask(()=>notifyHandler({target:{value:new DataView(answer.buffer)}}));}}};
const notify={uuid:uuid('ff11'),properties:{notify:true},addEventListener(_,fn){notifyHandler=fn},removeEventListener(){},async startNotifications(){},async stopNotifications(){}};
const fakeServer={connected:true,async getPrimaryServices(){return [{uuid:uuid('ff10'),async getCharacteristic(id){return id===write.uuid?write:notify}}]}};
const fakeDevice={name:'Smart Light',gatt:{connected:true,async connect(){return fakeServer},disconnect(){this.connected=false;fakeServer.connected=false}},addEventListener(){},removeEventListener(){}};
navigator.bluetooth={async requestDevice(options){assert.equal(options.optionalServices.length,5);return fakeDevice;}};await connect();assert(live.authenticated);assert.equal(connectionCalls.map(v=>v[1]).join(','),'0,1');assert.equal(queue.length,0);await new Promise(r=>setTimeout(r,70));assert.equal(connectionCalls.length,2);dropConnection('Connect contract cleanup');
console.log('PASS 11: connect/authenticate sends authentication only; disconnected changes never replay');
desired.color='#ff0000';render();

assert.equal(hueSatToHex(0,1),'#ff0000');assert.equal(hueSatToHex(120,1),'#00ff00');assert.equal(hueSatToHex(240,1),'#0000ff');assert.equal(hueSatToHex(85,0),'#ffffff');
const green=rgbToHueSat('#00ff00');assert.equal(green.hue,120);assert.equal(green.saturation,1);
wheel.onpointerdown({button:0,pointerId:1,clientX:100,clientY:0});assert.equal(desired.color,'#ff0000');wheel.onpointermove({pointerId:1,clientX:200,clientY:100});assert.equal(desired.color,'#80ff00');wheel.onpointerup({pointerId:1,clientX:100,clientY:200});assert.equal(desired.color,'#00ffff');
console.log('PASS 12: color conversions and pointer wheel cardinal selections');
c=setup(()=>null);c.authenticated=true;mode='hardware';desired.brightness=50;wheel.onpointerdown({button:0,pointerId:2,clientX:100,clientY:0});wheel.onpointermove({pointerId:2,clientX:200,clientY:100});assert.equal(c.calls.length,0);wheel.onpointerup({pointerId:2,clientX:100,clientY:200});await awaitMockWrites(c,6);assert.equal(c.calls.length,6);
let prevented=false;wheel.onkeydown({key:'ArrowRight',preventDefault(){prevented=true}});assert(prevented);assert.equal(c.calls.length,6);wheel.onkeyup({key:'ArrowRight'});await awaitMockWrites(c,12);assert.equal(c.calls.length,12);
dropConnection('Disconnected picker guard');wheel.onkeydown({key:'Home',preventDefault(){}});wheel.onkeyup({key:'Home'});assert.equal(desired.color,'#ffffff');assert.equal(c.calls.length,12);
console.log('PASS 13: pointer/keyboard send once on commit; disconnected controls never queue');

c=setup(()=>null);c.authenticated=true;mode='hardware';let bluetoothDisconnected=false;device={gatt:{connected:true,disconnect(){this.connected=false;bluetoothDisconnected=true}}};let endpoints=[];
globalThis.fetch=async(path,options)=>{assert(bluetoothDisconnected);assert.equal(live,null);endpoints.push(path);if(path==='/api/status')return {ok:true,json:async()=>({app:'afterglow-local-controller-v1',api:1,token:'test-capability'})};assert.equal(options.method,'POST');assert.equal(options.headers['X-Afterglow-Token'],'test-capability');return {ok:true,json:async()=>({app:'afterglow-local-controller-v1',stopping:true})};};
await $('quitApp').onclick();assert.equal(endpoints.join(','),'/api/status,/api/shutdown');assert.equal(appStopped,true);assert.equal($('appShell').hidden,true);assert.equal($('stoppedPanel').hidden,false);assert.equal(c.calls.length,0);assert.equal(quitChannel.lastMessage.type,'afterglow-quit');
console.log('PASS 14: Quit disconnects before local requests, stops UI and broadcasts without lighting commands');
appStopped=false;$('appShell').hidden=false;$('stoppedPanel').hidden=true;globalThis.fetch=async()=>({ok:false,status:403});await $('quitApp').onclick();assert.equal(appStopped,false);assert.equal(mode,'hardware');assert.equal($('quitApp').disabled,false);assert.equal($('stoppedPanel').hidden,true);quitChannel.onmessage({data:{type:'afterglow-quit'}});assert.equal(appStopped,true);assert.equal($('appShell').hidden,true);
console.log('PASS 15: failed shutdown remains honest; peer-tab quit stops controls');


appStopped=false;mode='hardware';protocol='daybetter';profile=emptyProfile();dropConnection('Remembered-device tests');
const stored=new Map();localStorage.getItem=k=>stored.get(k)||null;localStorage.setItem=(k,v)=>stored.set(k,v);
const record=(id='saved',selected='daybetter',custom=null)=>({version:1,id,name:'Smart Light',protocol:selected,custom});
function resetMemory(saved=null){appStopped=false;mode='hardware';dropConnection('Test reset');protocol='daybetter';profile=emptyProfile();startupAttempted=false;remembered=null;stored.clear();if(saved)stored.set(REMEMBER_KEY,JSON.stringify(saved));}
function grant(id,{auth=true,pending=null,custom=null,connectError=false,subscription=null}={}){
 let handler;const calls=[],stats={connects:0,disconnects:0,stops:0};const con={connected:false};
 const w={uuid:custom?custom.characteristic:uuid('ff12'),properties:{write:true},async writeValueWithResponse(data){calls.push(Array.from(data));if(data[1]===0||data[1]===1){const answer=response(data[1],data[1]===0?[...CHIP,1,2]:[...CHIP,auth?0:1]);queueMicrotask(()=>handler({target:{value:new DataView(answer.buffer)}}));}}};
 const n={properties:{notify:true},addEventListener(_,fn){handler=fn},removeEventListener(){},async startNotifications(){if(subscription)await subscription;},async stopNotifications(){stats.stops++;}};
 con.getPrimaryServices=async()=>[{uuid:uuid('ff10'),async getCharacteristic(id){return id===w.uuid?w:n}}];con.getPrimaryService=async id=>{assert.equal(id,custom.service);return {async getCharacteristics(){return [w]}}};
 const d={id,name:'Smart Light',calls,stats,con,notify:n,write:w,addEventListener(){},removeEventListener(){},gatt:{get connected(){return con.connected},async connect(){stats.connects++;if(connectError)throw Error('Out of range');if(pending)await pending;con.connected=true;return con},disconnect(){stats.disconnects++;con.connected=false}}};return d;
}
const waitFor=async check=>{const until=Date.now()+500;while(!check()){if(Date.now()>until)throw Error('Mock condition timed out');await new Promise(r=>setTimeout(r,1));}};
for(const bad of [{},record(''),{...record(),version:2},{...record(),protocol:'random'},record('saved','custom',{})])assert.throws(()=>validateRemembered(bad));
const customProfile=validateProfile({service:'fff0',characteristic:'fff1',color:'AA {r} {g} {b}'});assert.equal(validateRemembered(record('custom','custom',customProfile)).custom.characteristic,uuid('fff1'));
stored.set(REMEMBER_KEY,'{broken');assert.equal(readRemembered(),null);const getItem=localStorage.getItem;localStorage.getItem=()=>{throw Error('Storage blocked')};assert.equal(readRemembered(),null);localStorage.getItem=getItem;
console.log('PASS 16: remembered record version/identity/custom validation and corrupt/unavailable storage');
let pickerCalls=0,lookupCalls=0;resetMemory();navigator.bluetooth={async requestDevice(){pickerCalls++;throw Error('Unexpected picker')},async getDevices(){lookupCalls++;return []}};await reconnectAtStartup();assert.equal(pickerCalls,0);assert.equal(lookupCalls,0);
resetMemory(record());navigator.bluetooth={async requestDevice(){pickerCalls++;throw Error('Unexpected picker')}};await reconnectAtStartup();assert.equal(pickerCalls,0);
resetMemory(record());const wrong=grant('same-name-other-id');navigator.bluetooth={async getDevices(){return [wrong]},async requestDevice(){pickerCalls++;throw Error('Unexpected picker')}};await reconnectAtStartup();assert.equal(wrong.stats.connects,0);assert.equal(pickerCalls,0);assert.equal(stored.get(REMEMBER_KEY),JSON.stringify(record()));
console.log('PASS 17: no saved record/unsupported/revoked exact grant never invokes startup picker or same-name device');
resetMemory(record());const target=grant('saved'),other=grant('other');lookupCalls=0;navigator.bluetooth={async getDevices(){lookupCalls++;return [other,target]},async requestDevice(){pickerCalls++;throw Error('Unexpected picker')}};const originalColor=desired.color,originalBrightness=desired.brightness;await reconnectAtStartup();assert(live.authenticated);assert.equal(target.stats.connects,1);assert.equal(other.stats.connects,0);assert.equal(target.calls.map(v=>v[1]).join(','),'0,1');assert.equal(queue.length,0);assert.equal(desired.color,originalColor);assert.equal(desired.brightness,originalBrightness);const persisted=JSON.parse(stored.get(REMEMBER_KEY));assert.equal(persisted.id,'saved');assert(!('brightness'in persisted));assert(!('color'in persisted));await reconnectAtStartup();assert.equal(lookupCalls,1);
await connect();assert.equal(live,null);await new Promise(r=>setTimeout(r,20));assert.equal(target.stats.connects,1);await connect();assert.equal(target.stats.connects,2);assert.equal(pickerCalls,0);
console.log('PASS 18: one exact-ID startup authentication only, no lighting replay, manual reconnect reuses grant');
const replacement=grant('replacement');navigator.bluetooth={async getDevices(){throw Error('Must not enumerate on fresh choice')},async requestDevice(){pickerCalls++;return replacement}};await $('chooseLight').onclick();assert.equal(live.device.id,'replacement');assert.equal(JSON.parse(stored.get(REMEMBER_KEY)).id,'replacement');assert.equal(replacement.calls.map(v=>v[1]).join(','),'0,1');
console.log('PASS 19: Choose different light uses fresh user picker and remembers only ready replacement');
resetMemory(record());navigator.bluetooth={async getDevices(){throw Error('Permission lookup failed')},async requestDevice(){throw Error('Unexpected picker')}};await reconnectAtStartup();assert.equal(live,null);assert.equal(connecting,false);assert.equal(stored.get(REMEMBER_KEY),JSON.stringify(record()));
resetMemory(record());const unavailable=grant('saved',{connectError:true});navigator.bluetooth={async getDevices(){return [unavailable]}};await reconnectAtStartup();assert.equal(live,null);assert.equal(connecting,false);assert.equal(unavailable.calls.length,0);assert.equal($('connect').disabled,false);
const rejected=grant('new-rejected',{auth:false});navigator.bluetooth={async requestDevice(){return rejected}};await connect({chooseDifferent:true});assert.equal(live,null);assert.equal(stored.get(REMEMBER_KEY),JSON.stringify(record()));assert.equal(rejected.calls.map(v=>v[1]).join(','),'0,1');
console.log('PASS 20: lookup/out-of-range/auth failures leave honest disconnected state and retain last successful record');
resetMemory(record('custom','custom',customProfile));protocol='custom';profile=validateProfile(customProfile);const customDevice=grant('custom',{custom:customProfile});navigator.bluetooth={async getDevices(){return [customDevice]}};await reconnectAtStartup();assert.equal(live.device.id,'custom');assert.equal(customDevice.calls.length,0);assert.deepEqual(JSON.parse(stored.get(REMEMBER_KEY)).custom,customProfile);dropConnection('Custom ready cleanup');startupAttempted=false;profile=validateProfile({...customProfile,color:'BB {r} {g} {b}'});let touched=false;navigator.bluetooth.getDevices=async()=>{touched=true;return [customDevice]};await reconnectAtStartup();assert.equal(touched,false);assert.equal(live,null);
console.log('PASS 21: custom profile snapshot reconnects coherently; later profile changes prevent stale automatic reuse');
resetMemory(record());let releaseLookup;const delayed=grant('saved');navigator.bluetooth={getDevices(){return new Promise(r=>releaseLookup=r)}};const pendingLookup=reconnectAtStartup();await connect();releaseLookup([delayed]);await pendingLookup;assert.equal(delayed.stats.connects,0);assert.equal(live,null);assert.equal(connecting,false);
resetMemory(record());let releaseOld;const old=grant('saved'),newChoice=grant('new-choice');navigator.bluetooth={getDevices(){return new Promise(r=>releaseOld=r)},async requestDevice(){return newChoice}};const oldAttempt=reconnectAtStartup();await $('chooseLight').onclick();releaseOld([old]);await oldAttempt;assert.equal(old.stats.connects,0);assert.equal(live.device.id,'new-choice');assert.equal(JSON.parse(stored.get(REMEMBER_KEY)).id,'new-choice');
console.log('PASS 22: cancelling lookup and choosing a newer device reject stale lookup results/storage changes');
resetMemory(record());let releaseGatt;const gate=new Promise(r=>releaseGatt=r),late=grant('saved',{pending:gate});navigator.bluetooth={async getDevices(){return [late]}};const pendingConnect=reconnectAtStartup();await waitFor(()=>late.stats.connects===1);globalThis.fetch=async path=>({ok:true,json:async()=>path==='/api/status'?{app:'afterglow-local-controller-v1',api:1,token:'test'}:{app:'afterglow-local-controller-v1',stopping:true}});await $('quitApp').onclick();releaseGatt();await pendingConnect;await new Promise(r=>setTimeout(r,5));assert.equal(live,null);assert.equal(late.con.connected,false);assert.equal(late.calls.length,0);assert.equal(stored.get(REMEMBER_KEY),JSON.stringify(record()));
resetMemory();let releaseTimeout;const timeoutDevice=grant('timeout',{pending:new Promise(r=>releaseTimeout=r)}),timed={token:++session,device:timeoutDevice};live=timed;await assert.rejects(openGatt(timed,10),/timed out/);dropConnection('Timed connection cleanup');releaseTimeout();await new Promise(r=>setTimeout(r,5));assert.equal(timeoutDevice.con.connected,false);assert(timeoutDevice.stats.disconnects>=2);
console.log('PASS 23: Quit cancels pending GATT; timed-out late connection is closed without orphan state or writes');
resetMemory(record());let releaseProfile;const profileDevice=grant('saved');navigator.bluetooth={getDevices(){return new Promise(r=>releaseProfile=r)}};const staleProfile=reconnectAtStartup();$('protocolMode').onchange({target:{value:'custom'}});releaseProfile([profileDevice]);await staleProfile;assert.equal(live,null);assert.equal(profileDevice.stats.connects,0);assert.equal(protocol,'custom');
resetMemory();const storageDevice=grant('storage');navigator.bluetooth={async requestDevice(){return storageDevice}};const setItem=localStorage.setItem;localStorage.setItem=()=>{throw Error('Storage blocked')};await connect({chooseDifferent:true});assert(live.authenticated);assert.equal(remembered.id,'storage');localStorage.setItem=setItem;dropConnection('Memory test cleanup');
console.log('PASS 24: profile changes cancel lookup and storage write failure preserves ready connection');


// BEGIN notification-owner-check
async function notificationOwnerCheck(sameDevice){
 appStopped=false;mode='hardware';protocol='daybetter';let release;const pending=new Promise(resolve=>release=resolve);let starts=0,stops=0,authWrites=0;
 const notify={properties:{notify:true},addEventListener(){},removeEventListener(){},startNotifications(){starts++;return pending},async stopNotifications(){stops++;}};
 const write={properties:{write:true},async writeValueWithResponse(){authWrites++;}};
 const connection={connected:true,async getPrimaryServices(){return [{uuid:uuid('ff10'),async getCharacteristic(id){return id===uuid('ff12')?write:notify}}]}};
 const old={token:++session,protocol:'daybetter',device:{id:'same-physical-light'},authenticated:false};live=old;server=connection;
 const preparing=prepareChannel(old,connection);for(let n=0;!starts&&n<12;n++)await Promise.resolve();assert.equal(starts,1);
 live={token:++session,protocol:'daybetter',device:{id:sameDevice?'same-physical-light':'different-light'},authenticated:true};server=connection;
 release();await assert.rejects(preparing,/session ended/);assert.equal(authWrites,0);assert.equal(stops,sameDevice?0:1);
}
await notificationOwnerCheck(true);await notificationOwnerCheck(false);dropConnection('Notification owner test cleanup');
console.log('PASS 25: late notification cleanup preserves newer same-light subscription and stops abandoned different-light subscription');
// END notification-owner-check


// BEGIN reconnect-auth-quit-check
appStopped=false;mode='hardware';dropConnection('Auth cancellation test');protocol='daybetter';profile=emptyProfile();remembered=validateRemembered({version:1,id:'auth-pending',name:'Smart Light',protocol:'daybetter',custom:null});const beforeAuthRecord=JSON.stringify(remembered);let savedWrites=0;
localStorage.getItem=k=>k===REMEMBER_KEY?beforeAuthRecord:null;localStorage.setItem=()=>savedWrites++;let lateNotify;const authPackets=[];
const pendingWrite={properties:{write:true},async writeValueWithResponse(bytes){authPackets.push(Array.from(bytes));}};
const pendingNotify={properties:{notify:true},addEventListener(_,handler){lateNotify=handler},removeEventListener(){},async startNotifications(){},async stopNotifications(){}};
const authServer={connected:true,async getPrimaryServices(){return [{uuid:uuid('ff10'),async getCharacteristic(id){return id===uuid('ff12')?pendingWrite:pendingNotify}}]}};
const authDevice={id:'auth-pending',name:'Smart Light',gatt:{connected:true,async connect(){return authServer},disconnect(){this.connected=false;authServer.connected=false}},addEventListener(){},removeEventListener(){}};
navigator.bluetooth={async getDevices(){return [authDevice]},async requestDevice(){throw Error('Unexpected picker')}};const authConnecting=connect({automatic:true});const authUntil=Date.now()+500;while(!authPackets.length){assert(Date.now()<authUntil);await new Promise(resolve=>setTimeout(resolve,1));}
globalThis.fetch=async path=>({ok:true,json:async()=>path==='/api/status'?{app:'afterglow-local-controller-v1',api:1,token:'test'}:{app:'afterglow-local-controller-v1',stopping:true}});await $('quitApp').onclick();await authConnecting;
const staleAnswer=qcFrame(0,[...CHIP,1,2]);staleAnswer[0]=0xa1;const staleCrc=crc16(staleAnswer.slice(0,-2));staleAnswer[staleAnswer.length-2]=staleCrc&255;staleAnswer[staleAnswer.length-1]=staleCrc>>>8;lateNotify({target:{value:new DataView(staleAnswer.buffer)}});await Promise.resolve();assert.equal(live,null);assert.equal(connecting,false);assert.equal(appStopped,true);assert.equal(savedWrites,0);assert.equal(authPackets.map(v=>v[1]).join(','),'0');assert.equal(JSON.stringify(remembered),beforeAuthRecord);
console.log('PASS 26: Quit during authentication rejects stale notification, preserves saved device and never sends lighting');
// END reconnect-auth-quit-check

})()`;
vm.runInContext(tests,context).catch(error=>{console.error(error);process.exitCode=1});