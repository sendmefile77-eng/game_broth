const {test}=require('node:test'),assert=require('node:assert/strict'),C=require('./src/core.js'),M=require('./src/media.js');
let i=0;const go=(s,c)=>C.apply(s,c,'m'+(++i)).state;
test('all events and three candidates receive separate persistent jobs',()=>{let s=M.migrate(go(C.newGame('qa'),{type:'visit',place:'market'}));assert.equal(M.plan(s,'hire').filter(j=>j.type==='portrait').length,3);assert.equal(M.plan(s,'story').filter(j=>j.type==='scene').length,2);assert.equal(M.plan(s,'hire')[0].type,'portrait');});
test('hiring reuses portrait key and seed, changes no economy when media is attached',()=>{let s=M.migrate(go(C.newGame('qa'),{type:'visit',place:'market'}));const c=s.batch.candidates[0],before=M.prompt(s,{type:'portrait',id:c.id,character:c});s.portraits[c.id]='qa-portrait-C0001';s=go(s,{type:'hire',id:c.id});const w=s.workers[0];assert.equal(M.characterKey(w),c.id);assert.deepEqual(M.prompt(s,{type:'portrait',id:M.characterKey(w),character:w}),before);assert.ok(!M.plan(s,'staff').some(j=>j.key==='portrait:'+c.id));const rev=s.rev,money=s.treasury;s.journal[0].mediaId='qa-scene-S0001';s.mediaSeq++;C.validate(s);assert.equal(s.rev,rev);assert.equal(s.treasury,money);});
test('scene prompt uses the narrated moment without an action-type template',()=>{
 const s=M.migrate(go(C.newGame('qa'),{type:'visit',place:'halls'})),e=s.journal[1];M.beginScene(s,e,false);
 e.aiText='Ты находишь медный ключ под синим креслом в пустой приёмной.';
 Object.assign(e,M.parseBrief(JSON.stringify({prompt:'An owner kneels beside a blue armchair, reaching for a copper key underneath it in an empty reception room.',anchor:e.aiText,characterIds:[]}),s,e));
 const p=M.prompt(s,{type:'scene',id:e.id,event:e}).prompt;
 assert.match(p,/blue armchair/);assert.match(p,/copper key/);assert.doesNotMatch(p,/dwarven|candidates|market|contract/);
 s.portraits.C0001='p1';e.mediaId='s2';e.mediaSource=M.sourceKey(e);s.journal[0].mediaId='s1';assert.deepEqual(M.refs(s).sort(),['p1','s1','s2']);
});
test('pending narration blocks only its illustration and reload releases abandoned narration',()=>{
 const s=M.migrate(go(C.newGame('qa'),{type:'visit',place:'market'})),e=s.journal[1];M.beginScene(s,e,true);
 assert.ok(!M.plan(s,'hire').some(j=>j.id===e.id));assert.equal(M.plan(s,'hire').filter(j=>j.type==='portrait').length,3);
 M.recoverNarration(s);assert.ok(M.plan(s,'story').some(j=>j.id===e.id));
});
test('only a quote from final narrative and known cast IDs are accepted',()=>{
 const s=M.migrate(go(C.newGame('qa'),{type:'visit',place:'market'})),e=s.journal[1];M.beginScene(s,e,false);
 e.aiText='Мира Медная ставит медный чайник на стол у окна.';
 const valid={prompt:'Mira places a copper teapot on a wooden table beside a window.',anchor:e.aiText,characterIds:['C0001']};
 assert.throws(()=>M.parseBrief(JSON.stringify({...valid,anchor:e.text}),s,e));
 assert.throws(()=>M.parseBrief(JSON.stringify({...valid,characterIds:['invented']}),s,e));
 Object.assign(e,M.parseBrief(JSON.stringify(valid),s,e));
 assert.match(M.prompt(s,{type:'scene',id:e.id,event:e}).prompt,/green skin/);
 e.mediaId='image1';e.mediaSource=M.sourceKey(e);assert.equal(M.sceneRef(e),'image1');
 e.aiText='Тильда Искра открывает ставни у входа.';assert.equal(M.sceneRef(e),null);assert.throws(()=>M.prompt(s,{type:'scene',id:e.id,event:e}));
});
test('event keeps the original cast after a new search replaces candidates',()=>{
 let s=M.migrate(go(C.newGame('qa'),{type:'visit',place:'market'}));const e=s.journal[1];M.beginScene(s,e,false);
 s=go(s,{type:'search',place:'baths'});const saved=s.journal[1];assert.equal(saved.sceneContext.cast[0].race,'гоблин');
 assert.equal(saved.sceneContext.placeId,'market');assert.notEqual(s.batch.candidates[0].race,saved.sceneContext.cast[0].race);
});
test('v1 image migration keeps the existing image and corrupt portrait refs are rejected',()=>{let s=go(C.newGame('qa'),{type:'visit',place:'market'});s.mediaId='qa-media-1';s.mediaLabel='День 1';M.migrate(s);assert.equal(s.journal[1].mediaId,s.mediaId);C.validate(s);s.portraits.C0001='<script>';assert.throws(()=>C.validate(s));});
