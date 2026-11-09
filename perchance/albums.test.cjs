const {test}=require('node:test'),assert=require('node:assert/strict');
const C=require('./src/core.js'),M=require('./src/media.js'),A=require('./src/albums.js');
let seq=0;const go=(s,cmd)=>C.apply(s,cmd,'album-'+(++seq)).state;
function ready(){const s=M.migrate(go(C.newGame('albums-qa'),{type:'visit',place:'market'}));s.portraits.C0001='portrait-mira';s.portraits.C0002='portrait-tilda';return s;}
function addScene(s,text,ids,ref){const next=go(s,{type:'talk',text}),e=next.journal.at(-1);e.visualCast=ids;e.mediaId=ref;e.mediaSource=M.sourceKey(e);e.visualAnchor=text;return next;}
test('each character gets only its portrait and confirmed participating scenes',()=>{
 let s=ready();s=addScene(s,'Мира Медная и Тильда Искра у окна.',['C0001'],'mira-scene');A.sync(s,M.sceneRef);
 assert.deepEqual(A.entries(s,s.batch.candidates[0]).map(x=>x.ref),['portrait-mira','mira-scene']);
 assert.deepEqual(A.entries(s,s.batch.candidates[1]).map(x=>x.ref),['portrait-tilda']);
 assert.deepEqual(A.entries(s,s.batch.candidates[2]),[]);
});
test('candidate album survives hire and replacement of the candidate batch',()=>{
 let s=ready();s=addScene(s,'Мира Медная ставит чайник.',['C0001'],'tea-scene');A.sync(s,M.sceneRef);
 s=go(s,{type:'hire',id:'C0001'});s=go(s,{type:'search',place:'baths'});A.sync(s,M.sceneRef);
 assert.equal(s.workers[0].candidateId,'C0001');assert.deepEqual(A.entries(s,s.workers[0]).map(x=>x.ref),['portrait-mira','tea-scene']);
 assert.deepEqual(A.entries(s,s.batch.candidates[0]),[]);
});
test('archived images survive journal trimming and JSON save transfer without changing economy',()=>{
 let s=ready();s=addScene(s,'Мира Медная у окна.',['C0001'],'old-scene');const money=s.treasury,rev=s.rev;A.sync(s,M.sceneRef);const id=s.journal.at(-1).id;
 s.journal=s.journal.filter(e=>e.id!==id);const copy=JSON.parse(JSON.stringify(s));A.sync(copy,M.sceneRef);C.validate(copy);
 assert.ok(A.refs(copy).includes('old-scene'));assert.ok(!M.refs(copy).includes('old-scene'));assert.equal(A.lookup(copy,'album:C0001:old-scene').ref,'old-scene');assert.equal(copy.treasury,money);assert.equal(copy.rev,rev);
});
test('legacy full-name association, deduplication and shared scenes are stable',()=>{
 let s=ready();s=addScene(s,'Мира Медная у окна.',[], 'legacy-scene');s=addScene(s,'Общая сцена.',['C0001','C0002'],'shared-scene');
 A.sync(s,M.sceneRef);A.sync(s,M.sceneRef);assert.equal(A.entries(s,s.batch.candidates[0]).length,3);assert.equal(A.entries(s,s.batch.candidates[1]).length,2);
 assert.deepEqual(A.refs(s).sort(),['legacy-scene','portrait-mira','portrait-tilda','shared-scene']);
 const e=s.journal.at(-1);e.mediaSource='outdated';s.characterGalleries={};A.sync(s,M.sceneRef);assert.ok(!A.refs(s).includes('shared-scene'));
});
test('corrupt album references and metadata are rejected before import',()=>{
 const s=ready();A.sync(s,M.sceneRef);s.characterGalleries.C0001[0].ref='<script>';assert.throws(()=>C.validate(s));
 s.characterGalleries.C0001[0].ref='valid-ref';s.characterGalleries.C0001[0].title='x'.repeat(161);assert.throws(()=>C.validate(s));
});
