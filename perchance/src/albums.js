/* Character image archives. No prompts or game actions are generated here. */
(function(scope){
  'use strict';
  const key=c=>c.candidateId||c.id;
  const normal=text=>String(text||'').toLocaleLowerCase('ru').replace(/ё/g,'е').replace(/[^\p{L}\p{N}]+/gu,' ').trim();
  function includesCharacter(e,c){
    const ids=new Set([c.id,c.candidateId].filter(Boolean));
    if(Array.isArray(e.visualCast)&&e.visualCast.length)return e.visualCast.some(id=>ids.has(id));
    if(ids.has(e.characterId))return true;
    const text=' '+normal(e.aiText||e.text)+' '+normal(e.text)+' ',name=normal(c.name);
    return !!name&&text.includes(' '+name+' ');
  }
  function sync(s,sceneRef=e=>e.mediaId){
    if(!s.characterGalleries)s.characterGalleries={};
    const chars=new Map();for(const c of [...s.workers,...(s.batch?.candidates||[])])chars.set(key(c),c);
    for(const [id,c] of chars){
      const items=s.characterGalleries[id]||(s.characterGalleries[id]=[]),seen=new Set(items.map(x=>x.ref));
      const add=item=>{if(item.ref&&!seen.has(item.ref)){items.push(item);seen.add(item.ref);}};
      if(s.portraits?.[id])add({ref:s.portraits[id],type:'portrait',title:'Портрет: '+c.name,caption:'',day:s.day});
      for(const e of s.journal){const ref=sceneRef(e);if(ref&&includesCharacter(e,c))add({ref,type:'scene',eventId:e.id,title:e.title.slice(0,160),caption:String(e.visualAnchor||'').slice(0,800),day:e.day});}
    }
    return s;
  }
  function entries(s,c){const items=s.characterGalleries?.[key(c)]||[];return [...items.filter(x=>x.type==='portrait'),...items.filter(x=>x.type==='scene').reverse()];}
  function lookup(s,artKey){const parts=artKey.split(':'),id=parts[1],ref=parts.slice(2).join(':');return parts[0]==='album'?s.characterGalleries?.[id]?.find(x=>x.ref===ref):null;}
  function refs(s){return [...new Set(Object.values(s.characterGalleries||{}).flatMap(items=>items.map(x=>x.ref)))];}
  const api={key,includesCharacter,sync,entries,lookup,refs};if(typeof module!=='undefined'&&module.exports)module.exports=api;else scope.RFAlbums=api;
})(typeof window!=='undefined'?window:this);
