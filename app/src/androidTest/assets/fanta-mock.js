/* Android offline integration fixture only. Never included in the production game. */
window.fixture={textCalls:0,imageCalls:0};
window.root={generateText:async()=>{
  fixture.textCalls++;
  return JSON.stringify({title:'Печать на столе',narrative:'Адель кладёт медную печать на стол у окна. Снаружи слышны осторожные шаги, а на обороте конверта проступает след незнакомого знака. Ей хватает времени выбрать следующий шаг, но дверь не останется закрытой надолго.',choices:['Осмотреть печать','Подойти к двери','Спрятать письмо'],milestone:false,threat:false,location:'Нульн, комната',scene:'Медная печать на столе',wardrobe:'зелёный плащ',fact:'На письме есть знак',ending:'',frames:[{anchor:'Адель кладёт медную печать на стол у окна',description:'A clothed adult woman places a copper seal on a wooden table by a window, green cloak, warm lamplight.'}]});
},generateImage:async()=>{fixture.imageCalls++;return {dataUrl:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='};}};
function fixtureRect(selector){const e=document.querySelector(selector);if(!e)return null;const r=e.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height,b:r.bottom};}
window.addEventListener('message',e=>{
  if(e.origin!=='https://perchance.org')return;
  if(e.data?.type==='fixture-locate-button'){
    const button=[...document.querySelectorAll('button')].find(b=>!b.disabled&&b.textContent.trim()===e.data.label);
    if(button){button.scrollIntoView({block:'center',behavior:'instant'});requestAnimationFrame(()=>{const r=button.getBoundingClientRect();parent.postMessage({type:'fixture-button',token:e.data.token,x:r.x,y:r.y,w:r.width,h:r.height,height:innerHeight},e.origin);});}return;
  }
  if(e.data==='scroll-start-button')document.querySelector('#setup-hero button[type=submit]')?.scrollIntoView({block:'center'});
  if(e.data==='scroll-gallery')document.querySelector('[data-action="view"]')?.scrollIntoView({block:'center',behavior:'instant'});
  if(e.data==='bad-export'&&window.GameBrothFiles)GameBrothFiles.postMessage(JSON.stringify({name:'game-broth-day-0.json',text:JSON.stringify({format:'game-broth-export/2'})}));
  if(e.data==='measure')parent.postMessage({type:'fixtureMetrics',width:document.documentElement.clientWidth,scrollWidth:document.documentElement.scrollWidth,height:innerHeight,main:fixtureRect('main'),footer:fixtureRect('.foot'),input:fixtureRect('#action'),version:document.querySelector('#gb-apk-fanta-version')?.textContent||'',turnLabel:document.querySelector('.head-right span')?.textContent||'',dialog:!!document.querySelector('dialog[open]'),textCalls:fixture.textCalls,imageCalls:fixture.imageCalls},'https://perchance.org');
});
