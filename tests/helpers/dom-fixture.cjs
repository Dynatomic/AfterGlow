// Shared radio-free DOM fixture for Advanced and sample-display regressions.
const assert=require('node:assert');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');

function readVariants(base=path.resolve(__dirname,'..','..')){
 const sources=['index.html','dist/index.html'].map(file=>fs.readFileSync(path.join(base,file),'utf8'));
 const scripts=sources.map(source=>{const match=source.match(/<script>([\s\S]*?)<\/script>/);assert(match,'Missing inline app script');return match[1];});
 return {sources,scripts};
}

function createFixture(source,code){
 const ids=new Set([...source.matchAll(/\bid="([^"]+)"/g)].map(match=>match[1]));
 for(const [,id]of code.matchAll(/\$\('([^']+)'\)/g))assert(ids.has(id),'Dangling ID '+id);
 new vm.Script(code);
 const elements=new Map();
 function style(){return {values:{},setProperty(key,value){this.values[key]=value}};}
 function element(){
  const classes=new Set(['hidden']);
  return {value:'',textContent:'',innerHTML:'',hidden:false,open:false,dataset:{},style:style(),attrs:{},setAttribute(key,value){this.attrs[key]=String(value)},getAttribute(key){return this.attrs[key]??null},showModal(){this.open=true},close(){this.open=false;this.onclose?.()},removeAttribute(key){delete this[key];},
   classList:{add(key){classes.add(key)},remove(key){classes.delete(key)},contains(key){return classes.has(key)},toggle(key){classes.has(key)?classes.delete(key):classes.add(key)}},
   getBoundingClientRect(){return {left:0,top:0,width:200,height:200}},scrollIntoView(){},focus(){this.focused=true;context.document.activeElement=this},select(){this.selected=true},
   append(){},click(){},setPointerCapture(){},releasePointerCapture(){},options:[],files:[]};
 }
 function getElement(id){if(!elements.has(id))elements.set(id,element());return elements.get(id);}
 const context=vm.createContext({assert,console,
  document:{hidden:false,body:{style:{overflow:''}},activeElement:null,getElementById:getElement,documentElement:{style:style()},querySelectorAll(){return []},createElement:element,addEventListener(){}},
  location:{origin:'http://localhost:8766',protocol:'http:'},window:{isSecureContext:true},navigator:{userAgent:'Mock Chrome'},
  localStorage:{getItem(){return null},setItem(){}},setTimeout,clearTimeout,queueMicrotask,AbortController,Uint8Array,DataView,Date,URL,Blob,
  BroadcastChannel:class{postMessage(){}}});
 vm.runInContext(code,context);
 return context;
}

module.exports={readVariants,createFixture};
