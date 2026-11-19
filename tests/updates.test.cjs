// Manual update metadata only: requests and timers are mocked; nothing downloads.
const assert=require('node:assert'),path=require('node:path'),vm=require('node:vm');
const {readVariants,createFixture}=require('./helpers/dom-fixture.cjs');
const {sources,scripts}=readVariants(process.argv[2]||path.join(__dirname,'..'));
const section=source=>source.slice(source.indexOf('function parseStableVersion('),source.indexOf("$('checkUpdates').onclick="));
assert.equal(section(scripts[0]),section(scripts[1]));
for(const source of sources){assert(source.includes('id="checkUpdates"'));assert(source.includes('target="_blank" rel="noopener noreferrer" hidden>Open release page'));}
const tests=String.raw`(async()=>{
assert.equal(APP_VERSION,'1.0.4');assert.equal($('appVersion').textContent,APP_VERSION);assert.equal(__updateProbe.length,0);assert(troubleshootingReport().includes('App version: '+APP_VERSION));
for(const [a,b,want]of [['v1.0.4','1.0.3',1],['v1.0.3','1.0.2',1],['v1.0.2','1.0.1',1],['v1.0.1','1.0.0',1],['1.10.0','1.9.9',1],['v1.0.0+build.2','1.0.0',0],['0.9.9','1.0.0',-1],['999999999999999999999.0.0','999999999999999999998.999.999',1]])assert.equal(compareStableVersions(a,b),want);
for(const value of ['1.0.0'+String.fromCharCode(10),'1.0.0'+String.fromCharCode(13),'1.0.0'+String.fromCharCode(9),'',null,'1.0','01.0.0','1.0.0-rc.1','1.0.0;alert(1)','javascript:1.0.0','v1.0.0+','1.0.0\n'])assert.equal(parseStableVersion(value),null);
const stable=(tag,extra={})=>({tag_name:tag,draft:false,prerelease:false,...extra});
const response=(status=200,body=stable('v1.0.4'))=>({status,ok:status>=200&&status<300,async json(){return body}});
let requests=[];function useFetch(fn){globalThis.fetch=async(url,options)=>{requests.push({url,options});return fn(url,options)};}
async function check(mock){requests=[];useFetch(mock);await $('checkUpdates').onclick();assert.equal(requests.length,1);assert.equal(requests[0].url,UPDATE_API);assert.equal(requests[0].options.method,'GET');assert.equal(requests[0].options.credentials,'omit');assert.equal(requests[0].options.cache,'no-store');assert.equal(requests[0].options.redirect,'error');assert(!Object.keys(requests[0].options.headers).some(key=>/authorization|token/i.test(key)));assert.equal(pendingUpdateCheck,null);assert.equal($('checkUpdates').disabled,false);}
await check(()=>response(200,stable('v1.2.0',{html_url:'javascript:alert(1)',assets:[{browser_download_url:'https://untrusted.invalid/install.exe'}],body:'<script>throw Error()</script>'})));assert($('updateStatus').textContent.includes('1.2.0 is available'));assert.equal($('updateReleaseLink').href,'https://github.com/Dynatomic/AfterGlow/releases/tag/v1.2.0');assert.equal($('updateReleaseLink').hidden,false);
await check(()=>response());assert($('updateStatus').textContent.includes('up to date'));
await check(()=>response(200,stable('v0.9.0')));assert($('updateStatus').textContent.includes('newer than the latest'));
await check(()=>response(404));assert($('updateStatus').textContent.includes('No public release'));assert.equal($('updateReleaseLink').hidden,true);
for(const status of [403,429]){await check(()=>response(status));assert($('updateStatus').textContent.includes('limiting'));}
await check(()=>response(503));assert($('updateStatus').textContent.includes('Could not check'));
for(const malformed of [null,[],{},stable('v2.0.0-rc.1'),stable('v2.0.0',{prerelease:true}),stable('v2.0.0',{draft:true}),stable('v2.0.0;alert(1)')]){await check(()=>response(200,malformed));assert($('updateStatus').textContent.includes('not a valid stable version'));assert.equal($('updateReleaseLink').hidden,true);}
await check(()=>({status:200,ok:true,async json(){throw SyntaxError('Invalid JSON')}}));assert($('updateStatus').textContent.includes('not valid metadata'));
await check(()=>{throw Error('Sensitive network detail must not be displayed')});assert($('updateStatus').textContent.includes('Could not reach GitHub'));assert(!$('updateStatus').textContent.includes('Sensitive'));
console.log('PASS update group 1: stable SemVer, trusted release URL, new/current/local-newer/no-release/rate-limit/network/malformed metadata states');
const realSetTimeout=setTimeout,realClearTimeout=clearTimeout;let timeout;
globalThis.setTimeout=(fn,ms,...args)=>{if(ms===UPDATE_TIMEOUT_MS){timeout={fn,cleared:false};return timeout;}return realSetTimeout(fn,ms,...args)};globalThis.clearTimeout=handle=>{if(handle&&handle===timeout)handle.cleared=true;else realClearTimeout(handle)};
requests=[];let resolvePending;useFetch(()=>new Promise(resolve=>resolvePending=resolve));const first=$('checkUpdates').onclick();await $('checkUpdates').onclick();assert.equal(requests.length,1);assert.equal($('checkUpdates').disabled,true);resolvePending(response());await first;assert.equal(pendingUpdateCheck,null);assert(timeout.cleared);
requests=[];useFetch(()=>new Promise(()=>{}));const hung=$('checkUpdates').onclick();const hungSignal=requests[0].options.signal;timeout.fn();await hung;assert(hungSignal.aborted);assert($('updateStatus').textContent.includes('timed out'));assert.equal(pendingUpdateCheck,null);
requests=[];let resolveBody;useFetch(()=>({status:200,ok:true,json(){return new Promise(resolve=>resolveBody=resolve)}}));const slowBody=$('checkUpdates').onclick();await Promise.resolve();await Promise.resolve();timeout.fn();await slowBody;assert($('updateStatus').textContent.includes('timed out'));assert.equal(pendingUpdateCheck,null);
requests=[];let late;globalThis.fetch=async(url,options)=>{if(url===UPDATE_API){requests.push({url,options});return new Promise(resolve=>late=resolve)}return {ok:true,json:async()=>url==='/api/status'?{app:'afterglow-local-controller-v1',api:1,token:'test'}:{app:'afterglow-local-controller-v1',stopping:true}};};const checking=$('checkUpdates').onclick();await $('quitApp').onclick();await checking;assert.equal(appStopped,true);assert.equal(pendingUpdateCheck,null);assert(requests[0].options.signal.aborted);late(response(200,stable('v9.0.0')));await Promise.resolve();assert.equal($('updateReleaseLink').hidden,true);
globalThis.setTimeout=realSetTimeout;globalThis.clearTimeout=realClearTimeout;
console.log('PASS update group 2: no startup request, one pending check, whole-response timeout, Quit abort and no download/install or late UI revival');
})()`;
(async()=>{for(let i=0;i<sources.length;i++){console.log(i?'dist/index.html':'index.html');const code=scripts[i].replace("'use strict';","'use strict';globalThis.__updateProbe=[];globalThis.fetch=(...args)=>{__updateProbe.push(args);return Promise.reject(Error('Unexpected startup fetch'));};");const context=createFixture(sources[i],code);await vm.runInContext(tests,context);}console.log('PASS update group 3: local/hosted updater parity, version/DOM bindings and no startup metadata request');})().catch(error=>{console.error(error);process.exitCode=1});
