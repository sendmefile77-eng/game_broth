const {test}=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const C=require('./src/core.js'),M=require('./src/media.js');
function harness(){
 const state=M.migrate(C.apply(C.newGame('queue-test'),{type:'visit',place:'market'},'visit').state),timers=new Map(),root={},saved=[],calls=[],briefs=[];let timerId=0,clock=1000;
 const window={RFMedia:M},document={querySelectorAll:()=>[],querySelector:()=>null,addEventListener:()=>{},hidden:false};
 const ctx={window,document,root,Date:{now:()=>clock},Map,Set,URL,queueMicrotask,setTimeout:(fn,ms)=>{const id=++timerId;timers.set(id,{fn,ms});return id;},clearTimeout:id=>timers.delete(id),fetch:async()=>({blob:async()=>({type:'image/png'})})};
 vm.runInNewContext(fs.readFileSync(__dirname+'/src/gallery.js','utf8'),ctx);
 const gallery=new window.RFGallery({state:()=>state,route:()=> 'hire',get:async()=>null,put:async(store,key)=>saved.push(key),save:async()=>{}});
 return {state,root,gallery,timers,saved,calls,briefs,advance(ms){clock+=ms;},tick(){const item=[...timers].find(([,v])=>v.ms===1000);assert.ok(item,'readiness timer exists');timers.delete(item[0]);item[1].fn();},ready(){root.generateText=async input=>{briefs.push(input);const text=input.instruction.split('FINAL NARRATIVE: ')[1].split('\nKNOWN CAST')[0];return JSON.stringify({prompt:'A precise single fantasy illustration of the confirmed visible action, with the specified setting and objects.',anchor:text.slice(0,300),characterIds:[]});};root.generateImage=async input=>{calls.push(input);return {dataUrl:'data:image/png;base64,AQ=='};};}};
}
async function flush(){for(let i=0;i<20;i++)await new Promise(ok=>setImmediate(ok));}
test('slow plugin startup does not mark queued images as failed',async()=>{const h=harness();await h.gallery.pump();await flush();assert.equal(h.calls.length,0);assert.equal(h.gallery.failed.size,0);assert.equal(M.plan(h.state,'hire').length,5);assert.equal(h.timers.size,1);h.gallery.schedule();await flush();assert.equal(h.timers.size,1,'only one readiness timer');});
test('queue resumes when the plugin becomes ready without user retries',async()=>{const h=harness();await h.gallery.pump();await flush();h.ready();h.tick();await flush();assert.equal(h.calls.length,5);assert.equal(h.saved.length,5);assert.equal(h.gallery.failed.size,0);assert.equal(M.plan(h.state,'hire').length,0);assert.equal(h.state.treasury,24);assert.equal(h.state.rev,1);});
test('unavailable plugin shows recoverable state and retry all resumes work',async()=>{const h=harness();await h.gallery.pump();h.advance(46000);h.tick();await flush();assert.equal(h.gallery.paused,true);assert.equal(h.gallery.failed.size,1);h.ready();h.gallery.retry('all');await flush();assert.equal(h.gallery.paused,false);assert.equal(h.calls.length,5);assert.equal(M.plan(h.state,'hire').length,0);});

function isolateScene(h){for(const e of h.state.journal){M.beginScene(h.state,e,false);e.mediaId='existing-'+e.id;e.mediaSource=M.sourceKey(e);}for(const c of h.state.batch.candidates)h.state.portraits[c.id]='existing-'+c.id;const e=h.state.journal.at(-1);delete e.mediaId;return e;}
test('every scene brief receives final narrative, not the player command',async()=>{
 const h=harness();h.ready();const e=isolateScene(h);e.aiText='Мира Медная ставит медный чайник на стол у окна.';
 await h.gallery.pump();await flush();assert.equal(h.briefs.length,1);assert.ok(h.briefs[0].instruction.includes('FINAL NARRATIVE: '+e.aiText));
 assert.ok(!h.briefs[0].instruction.includes('FINAL NARRATIVE: '+e.text));assert.equal(M.sceneRef(e),e.mediaId);assert.ok(e.mediaId);
});
test('invalid storyboard stops the image instead of drawing a generic replacement',async()=>{
 const h=harness();h.ready();const e=isolateScene(h);h.root.generateText=async()=> 'invalid JSON';
 await h.gallery.pump();await flush();assert.equal(h.calls.length,0);assert.equal(h.saved.length,0);assert.ok(h.gallery.failed.has('scene:'+e.id));assert.equal(e.mediaId,undefined);
});
test('changing narrative during an image request discards the stale image',async()=>{
 const h=harness();h.ready();const e=isolateScene(h);e.aiText='Ты поднимаешь медный ключ с пола приёмной.';let finish;
 const draw=h.root.generateImage;h.root.generateImage=input=>{h.calls.push(input);return new Promise(ok=>{finish=ok;});};
 const pumping=h.gallery.pump();await flush();assert.ok(finish);e.aiText='Ты ставишь синий чайник на стол у окна.';const expected=M.sourceKey(e);
 h.root.generateImage=draw;finish({dataUrl:'data:image/png;base64,AQ=='});await pumping;await flush();
 assert.equal(h.saved.length,1,'only current image stored');assert.equal(e.mediaSource,expected);assert.equal(h.calls.length,2);assert.equal(h.briefs.length,2);
});
test('pending text allows portraits but never starts its scene image',async()=>{
 const h=harness();h.ready();for(const e of h.state.journal)M.beginScene(h.state,e,true);
 await h.gallery.pump();await flush();assert.equal(h.calls.length,3);assert.equal(h.briefs.length,0);assert.ok(h.state.journal.every(e=>!e.mediaId));
 h.state.journal.at(-1).aiText='Ты открываешь тяжёлую дверь в зал со свечами.';h.state.journal.at(-1).narrationStatus='ready';h.gallery.schedule();await flush();assert.equal(h.calls.length,4);assert.equal(h.briefs.length,1);
});
