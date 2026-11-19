// Native dialog integration is exercised with fake DOM primitives; live browser visuals remain separate.
const vm=require('node:vm');
const {readVariants,createFixture}=require('./helpers/dom-fixture.cjs');
(async()=>{const {sources,scripts}=readVariants();for(let variant=0;variant<sources.length;variant++){
const context=createFixture(sources[variant],scripts[variant]);await vm.runInContext(String.raw`(async()=>{
const opener=$('settingsToggle');document.body.style.overflow='scroll';opener.focus();assert.equal($('settings').open,false);opener.onclick();assert.equal($('settings').open,true);assert.equal(opener.getAttribute('aria-expanded'),'true');assert.equal(document.body.style.overflow,'hidden');assert.equal($('expertSettings').open,false);
$('closeSettings').onclick();assert.equal($('settings').open,false);assert.equal(opener.getAttribute('aria-expanded'),'false');assert.equal(document.body.style.overflow,'scroll');assert.equal(document.activeElement,opener);
opener.onclick();let prevented=false;$('settings').oncancel({preventDefault(){prevented=true;}});assert(prevented);assert.equal($('settings').open,false);assert.equal(document.body.style.overflow,'scroll');assert.equal(document.activeElement,opener);
protocol='custom';revealSettings();assert.equal($('expertSettings').open,true);assert.equal($('settings').open,true);closeSettings();
// Check for updates opens the same dialog without opening Expert settings.
$('expertSettings').open=false;protocol='daybetter';$('checkUpdates').focus();globalThis.fetch=async()=>({ok:true,status:200,async json(){return {tag_name:'v1.0.3',html_url:'https://github.com/dynatomic/AfterGlow/releases/tag/v1.0.3'};}});await $('checkUpdates').onclick();assert.equal($('settings').open,true);assert.equal($('expertSettings').open,false);closeSettings();assert.equal(document.activeElement,$('checkUpdates'));assert.equal(document.body.style.overflow,'scroll');
// Stopping the app closes the top-layer dialog and restores scroll state.
revealSettings(false);showAppStopped('Dialog lifecycle check');assert.equal($('settings').open,false);assert.equal(document.body.style.overflow,'scroll');assert.equal(opener.getAttribute('aria-expanded'),'false');
if(toastTimer)clearTimeout(toastTimer);
})()`,context);console.log('PASS Advanced dialog '+(variant?'standalone':'local')+': open/Close/Escape, focus/scroll restoration, custom reveal, updates and Quit');}})().catch(error=>{console.error(error);process.exitCode=1;});
