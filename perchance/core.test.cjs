const {test}=require('node:test');
const assert=require('node:assert/strict');
const C=require('./src/core.js');
let seq=0;const go=(s,c,id)=>C.apply(s,c,id||'test-'+(++seq)).state;
const start=()=>go(C.newGame('test'),{type:'visit',place:'market'});
const hired=()=>{let s=start();return go(s,{type:'hire',id:s.batch.candidates[0].id});};
test('new game starts with 24 coins, day zero and three fixed candidates after visit',()=>{
 const initial=C.newGame('test');assert.equal(initial.treasury,24);assert.equal(initial.day,0);C.validate(initial);
 const s=start(),batch=JSON.stringify(s.batch);assert.equal(s.day,1);assert.equal(s.batch.candidates.length,3);
 assert.equal(JSON.stringify(JSON.parse(JSON.stringify(s)).batch),batch);assert.equal(s.treasury,24);
});
test('action IDs and candidate status prevent duplicate hire charges',()=>{
 let s=start();const cmd={type:'hire',id:s.batch.candidates[0].id};s=go(s,cmd,'hire-once');assert.equal(s.treasury,16);
 assert.equal(C.apply(s,cmd,'hire-once').changed,false);assert.throws(()=>go(s,cmd));assert.equal(s.workers.length,1);
});
test('overspending does not mutate the state or replace the candidate batch',()=>{
 let s=start();s.treasury=0;const before=JSON.stringify(s);assert.throws(()=>go(s,{type:'search',place:'halls'}));assert.equal(JSON.stringify(s),before);
 assert.throws(()=>go(s,{type:'hire',id:s.batch.candidates[0].id}));assert.equal(JSON.stringify(s),before);
});
test('daily accounting: 16 + 8 - 2 - 3 - 1 = 18, settlement applies once',()=>{
 let s=go(hired(),{type:'settle'});assert.equal(s.treasury,18);assert.equal(s.lastReport.served,1);assert.equal(s.workers[0].fatigue,12);assert.equal(s.workers[0].xp,1);assert.equal(s.reputation,1);
 const before=JSON.stringify(s);s=go(s,{type:'settle'});assert.equal(JSON.stringify(s),before);
 s=go(s,{type:'morning'});assert.equal(s.day,2);assert.equal(s.workers[0].assignment,'WORK');
});
test('rest recovers fatigue, no service, new shortfall becomes debt once',()=>{
 let s=hired();s.treasury=1;s.workers[0].fatigue=90;s.workers[0].health=90;
 s=go(s,{type:'settle'});assert.equal(s.treasury,0);assert.equal(s.debt,3);assert.equal(s.workers[0].fatigue,65);assert.equal(s.workers[0].health,95);assert.equal(s.workers[0].assignment,'WORK');
 s=go(s,{type:'morning'});s=go(s,{type:'assign',id:s.workers[0].id,assignment:'REST'});s=go(s,{type:'settle'});assert.equal(s.debt,7);assert.equal(s.workers[0].fatigue,40);
});
test('new search costs one and creates non-reused candidate identifiers',()=>{
 let s=start(),old=s.batch.candidates.map(c=>c.id);s=go(s,{type:'search',place:'halls'});assert.equal(s.treasury,23);assert.equal(s.day,1);assert.ok(s.batch.candidates.every(c=>!old.includes(c.id)));
});
test('room opens only on due morning, rent changes exactly once, max three rooms',()=>{
 let s=hired();s.treasury=100;s=go(s,{type:'build'});assert.equal(s.treasury,70);assert.equal(s.rooms,1);
 s=go(go(s,{type:'settle'}),{type:'morning'});assert.equal(s.rooms,1);assert.equal(s.rent,3);
 s=go(go(s,{type:'settle'}),{type:'morning'});assert.equal(s.rooms,2);assert.equal(s.rent,5);
 s=go(s,{type:'build'});assert.throws(()=>go(s,{type:'build'}));s=go(go(s,{type:'settle'}),{type:'morning'});s=go(go(s,{type:'settle'}),{type:'morning'});assert.equal(s.rooms,3);assert.equal(s.rent,7);
});
test('room and staff capacities bound served guests, deterministic allocation',()=>{
 let s=start();s.treasury=100;s=go(s,{type:'hire',id:s.batch.candidates[0].id});s=go(s,{type:'hire',id:s.batch.candidates[1].id});s.reputation=100;s=go(s,{type:'settle'});assert.equal(s.lastReport.served,2);assert.equal(s.lastReport.rows[0].served,2);assert.equal(s.lastReport.rows[1].served,0);
});
test('save validation rejects corrupt balances, counters and injected report values',()=>{
 let s=go(hired(),{type:'settle'});C.validate(JSON.parse(JSON.stringify(s)));
 for(const mutate of [x=>x.treasury=-1,x=>x.nextIds.worker=0,x=>x.lastReport.gross='<img>',x=>x.phase='ORDERS']){const x=JSON.parse(JSON.stringify(s));mutate(x);assert.throws(()=>C.validate(x));}
});
test('dialogue does not advance time or alter finances',()=>{
 let s=hired(),day=s.day,money=s.treasury;s=go(s,{type:'talk',text:'Спросить, как идут дела.'});assert.equal(s.day,day);assert.equal(s.treasury,money);assert.equal(s.rev,3);
});
