/* Pure media planning: every event has its own image; hiring reuses a portrait. */
(function(scope){
  'use strict';
  const characterKey=c=>c.candidateId||c.id;
  function seed(text){let n=2166136261;for(const c of text)n=Math.imul(n^c.charCodeAt(0),16777619);return n>>>1;}
  function profile(c){
    const race=({'гоблин':'adult female goblin, green skin, pointed ears','нага':'adult female naga, human upper body, serpent lower body','дворф':'adult female dwarf, short and broad, strong features'}[c.race]||'adult fantasy person');
    const look=String(c.look||'');
    const hair=look.includes('серебристые')?'silver hair, green eyes, dark blue jacket':look.includes('рыжие')?'red hair, brown eyes, copper brooch':'dark hair, amber eyes, burgundy waistcoat';
    return race+', age '+Math.max(25,Number(c.age)||25)+', '+hair+', fully clothed in modest medieval clothing';
  }
  function migrate(s){
    if(!s.portraits)s.portraits={};
    if(s.mediaId&&!s.journal.some(e=>e.mediaId===s.mediaId)){
      const old=s.journal.findLast?s.journal.findLast(e=>e.day===Number((s.mediaLabel||'').replace(/\D/g,''))):s.journal.at(-1);
      if(old)old.mediaId=s.mediaId;
    }
    return s;
  }
  function plan(s,route){
    const chars=new Map();for(const c of [...s.workers,...(s.batch?.candidates||[])])chars.set(characterKey(c),c);
    const portraits=[...chars].filter(([key])=>!s.portraits?.[key]).map(([key,c])=>({key:'portrait:'+key,type:'portrait',id:key,character:c}));
    const scenes=s.journal.filter(e=>!e.mediaId).slice().reverse().map(e=>({key:'scene:'+e.id,type:'scene',id:e.id,event:e}));
    return ['hire','staff'].includes(route)?[...portraits,...scenes]:[...scenes,...portraits];
  }
  function refs(s){return [...new Set([...Object.values(s.portraits||{}),...s.journal.map(e=>e.mediaId),s.mediaId].filter(Boolean))];}
  function prompt(s,job){
    const style='Painterly dark fantasy concept art, warm copper lantern light, burgundy and charcoal palette, detailed, no text, no lettering. Only fully clothed adults over 25, ordinary nonsexual activities.';
    if(job.type==='portrait')return {prompt:'Character portrait, one person, head to waist, face clearly visible, neutral standing pose. '+profile(job.character)+'. Medieval inn reception background. '+style,resolution:'512x768',seed:seed(s.id+job.id)};
    const e=job.event,t=e.actionType||({report:'settle',dialogue:'talk'}[e.kind])||(/Утро/.test(e.title)?'morning':/договор/.test(e.title)?'hire':/встреча/i.test(e.title)?'visit':'setup');
    const place=e.placeId||s.batch?.placeId;
    const places={market:'a lively goblin market with spice stalls and copper bells',baths:'the public entrance hall of a naga bathhouse, turquoise tiles and steam, fully clothed visitors',halls:'a dwarven stone music hall with warm lights'};
    const scenes={setup:'A new owner holding an old brass key at the front door of a modest medieval inn',visit:'Three adult candidates waiting to meet an inn owner at '+(places[place]||places.market),search:'An inn owner meeting new adult job applicants at '+(places[place]||places.market),hire:'An inn owner and a new employee signing an ordinary employment contract at a wooden reception desk, a small pouch of coins',assign:'An adult employee discussing the work schedule beside a notice board in an inn',settle:'At closing time, an inn keeper counting coins beside a ledger, employees tidying tables',morning:'Morning sunlight entering a medieval inn, staff preparing the reception and opening the shutters',build:'Carpenters renovating a guest room, wood beams and ordinary tools',repay:'An inn keeper returning a pouch of coins to a merchant at the reception',talk:'An inn owner speaking with fully clothed adult staff in a cozy reception, expressive faces and natural gestures'};
    const person=s.workers.find(w=>w.id===e.characterId)||s.batch?.candidates.find(c=>c.id===e.characterId);
    return {prompt:(scenes[t]||scenes.talk)+'. '+(person?'Featured character: '+profile(person)+'. ':'')+(e.visualBrief?'Scene detail: '+e.visualBrief.slice(0,600)+'. ':'')+style,resolution:'768x512',seed:seed(s.id+e.id)};
  }
  const api={characterKey,seed,profile,migrate,plan,refs,prompt};if(typeof module!=='undefined'&&module.exports)module.exports=api;else scope.RFMedia=api;
})(typeof window!=='undefined'?window:this);
