/* Test-only UI metrics and deliberate bridge misuse; excluded from release assets. */
window.addEventListener('message',event=>{
 if(event.origin!=='https://perchance.org')return;
 if(event.data==='bad-export'){
  const nursery=window.WORKSHOP_MODE==='nursery';window.GameBrothFiles?.postMessage(JSON.stringify({name:nursery?'broth-alchemy-save.json':'broth-nursery-save.json',text:JSON.stringify({format:nursery?'broth-alchemy/1':'broth-nursery/1'})}));return;
 }
 if(event.data==='scroll-gallery')document.querySelector('button[data-act="view"]')?.scrollIntoView({block:'center'});
 if(event.data==='measure'){
  const box=selector=>{const r=document.querySelector(selector)?.getBoundingClientRect();return r?{x:r.x,y:r.y,w:r.width,h:r.height,b:r.bottom}:null;};
  parent.postMessage({type:'fixtureMetrics',width:innerWidth,height:innerHeight,scrollWidth:document.documentElement.scrollWidth,main:box('#workshop main'),footer:box('#workshop footer'),input:box('#materials'),version:document.getElementById('workshop-version')?.textContent||'',pending:!!document.querySelector('.pending'),scenes:document.querySelectorAll('article.scene').length,images:document.querySelectorAll('article.scene img[data-media]').length,imageCalls:fixture.images,textCalls:fixture.texts,dialog:!!document.querySelector('dialog[open]'),hud:document.querySelector('#workshop header>span')?.textContent||''},'https://perchance.org');
 }
});
