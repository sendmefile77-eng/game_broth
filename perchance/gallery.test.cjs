const {test}=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const C=require('./src/core.js'),M=require('./src/media.js');
function harness(){
 const state=M.migrate(C.apply(C.newGame('queue-test'),{type:'visit',place:'market'},'visit').state),timers=new Map(),root={},saved=[],calls=[];let timerId=0,clock=1000;
 const window={RFMedia:M},document={querySelectorAll:()=>[],querySelector:()=>null,addEventListener:()=>{},hidden:false};
 const ctx={window,document,root,Date:{now:()=>clock},Map,Set,URL,queueMicrotask,setTimeout:(fn,ms)=>{const id=++timerId;timers.set(id,{fn,ms});return id;},clearTimeout:id=>timers.delete(id),fetch:async()=>({blob:async()=>({type:'image/png'})})};
 vm.runInNewContext(fs.readFileSync(__dirname+'/src/gallery.js','utf8'),ctx);
 const gallery=new window.RFGallery({state:()=>state,route:()=> 'hire',get:async()=>null,put:async(store,key)=>saved.push(key),save:async()=>{}});
 return {state,root,gallery,timers,saved,calls,advance(ms){clock+=ms;},tick(){const item=[...timers].find(([,v])=>v.ms===1000);assert.ok(item,'readiness timer exists');timers.delete(item[0]);item[1].fn();},ready(){root.generateImage=async input=>{calls.push(input);return {dataUrl:'data:image/png;base64,AQ=='};};}};
}
async function flush(){for(let i=0;i<20;i++)await new Promise(ok=>setImmediate(ok));}
test('slow plugin startup does not mark queued images as failed',async()=>{const h=harness();await h.gallery.pump();await flush();assert.equal(h.calls.length,0);assert.equal(h.gallery.failed.size,0);assert.equal(M.plan(h.state,'hire').length,5);assert.equal(h.timers.size,1);h.gallery.schedule();await flush();assert.equal(h.timers.size,1,'only one readiness timer');});
test('queue resumes when the plugin becomes ready without user retries',async()=>{const h=harness();await h.gallery.pump();await flush();h.ready();h.tick();await flush();assert.equal(h.calls.length,5);assert.equal(h.saved.length,5);assert.equal(h.gallery.failed.size,0);assert.equal(M.plan(h.state,'hire').length,0);assert.equal(h.state.treasury,24);assert.equal(h.state.rev,1);});
test('unavailable plugin shows recoverable state and retry all resumes work',async()=>{const h=harness();await h.gallery.pump();h.advance(46000);h.tick();await flush();assert.equal(h.gallery.paused,true);assert.equal(h.gallery.failed.size,1);h.ready();h.gallery.retry('all');await flush();assert.equal(h.gallery.paused,false);assert.equal(h.calls.length,5);assert.equal(M.plan(h.state,'hire').length,0);});
