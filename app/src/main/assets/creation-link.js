(function(){
  'use strict';
  if(location.origin!=='https://perchance.org'||window.top!==window)return;
  let last='',queued=false;
  let job=null,timer=0;
  function send(message){try{window.GameBrothCreation?.postMessage(JSON.stringify(message));}catch(_){}}
  function visible(e){return !!e&&e.getClientRects().length>0&&getComputedStyle(e).display!=='none'&&getComputedStyle(e).visibility!=='hidden';}
  function resultUrl(){
    const link=document.querySelector('#settingsModalEl [data-ref="duplicateGeneratorStatus"] a[href], [data-ref="duplicateGeneratorStatus"] a[href]');
    if(link){try{const u=new URL(link.getAttribute('href'),location.href);if(u.origin==='https://perchance.org')return u.href;}catch(_){}}
    return '';
  }
  function report(){
    queued=false;
    // Perchance's duplicate command leaves the editor on the original page.
    // Its success result contains the actual new URL in this specific element.
    const candidate=resultUrl();if(!candidate)return;
    try{
      const url=new URL(candidate);
      if(url.origin!=='https://perchance.org'||url.href===last)return;
      if(window.GameBrothCreation){window.GameBrothCreation.postMessage(JSON.stringify({url:url.href}));last=url.href;}
    }catch(_){}
  }
  const observer=new MutationObserver(()=>{if(!queued){queued=true;requestAnimationFrame(report);}});
  observer.observe(document,{childList:true,subtree:true,attributes:true,attributeFilter:['href']});
  document.addEventListener('DOMContentLoaded',report,{once:true});report();
  function status(phase,detail){if(job&&job.phase!==phase){job.phase=phase;send({type:'state',token:job.token,phase,detail:detail||''});}}
  function stop(){clearInterval(timer);timer=0;}
  function gameUrl(value){
    try{const u=new URL(value,location.href),slug=u.pathname.replace(/^\//,'').replace(/\/$/,'');
      if(u.origin!=='https://perchance.org'||!/^[a-z0-9][a-z0-9-]{0,99}$/.test(slug)||['blank','minimal','basic-template','welcome','generators','login','signup','tutorial','examples','privacy-policy','terms-of-service'].includes(slug))return '';
      return u.origin+'/'+slug;
    }catch(_){return '';}
  }
  function tick(){
    if(!job)return;
    const found=resultUrl();
    const page=location.origin+location.pathname.replace(/\/$/,'');
    const changed=page!==job.source&&gameUrl(page);
    const newResult=found&&found!==job.initialResult&&gameUrl(found)!==job.source&&gameUrl(found);
    if(job.clicked&&(newResult||changed)){stop();const done=job;job=null;send({type:'created',token:done.token,url:newResult||changed});return;}
    if(job.clicked&&page!==job.source&&!changed){stop();status('paused','Perchance открыл служебную страницу. Проверьте результат перед повтором.');return;}
    if(Date.now()>job.deadline){stop();status(job.clicked?'paused':'login',job.clicked?'Perchance не вернул ссылку. Проверьте, появилась ли копия в аккаунте.':'Страница ещё не готова. Завершите вход и нажмите «Продолжить».');return;}
    if(job.clicked)return;
    if(page!==job.source){stop();status('login','Открыта другая страница. Нажмите «Продолжить», чтобы вернуться к исходной игре.');return;}
    if(visible(document.querySelector('#contentWarningPanelEl'))){stop();status('login','Сначала подтвердите предупреждение Perchance, затем нажмите «Продолжить».');return;}
    const login=document.querySelector('#menuBarEl [data-ref="loginButton"]');
    if(visible(login)){
      status('login','Войдите в Perchance. После входа создание продолжится.');
      if(!job.loginOpened){job.loginOpened=true;login.click();}return;
    }
    const edit=document.querySelector('#menuBarEl [data-ref="editButton"]');
    if(visible(edit)){if(!job.editorOpened){job.editorOpened=true;edit.click();}return;}
    const settings=document.querySelector('#menuBarEl [data-ref="settingsButton"]');
    if(visible(settings)){
      if(!job.settingsOpened){job.settingsOpened=true;settings.click();return;}
      const duplicate=document.querySelector('#settingsModalEl [onclick*="duplicateGenerator"]');
      if(visible(duplicate)){job.clicked=true;status('running','Создаём отдельную копию в Perchance…');duplicate.click();}return;
    }
    // A missing settings button alone is not proof of non-ownership: wait for
    // Perchance's ownership determination before allowing its Save-as-copy flow.
    const save=document.querySelector('#menuBarEl [data-ref="saveButton"]');
    if(window.userOwnsThisGenerator===false&&visible(save)){
      // On an existing generator, Save can use an edit key or collaboration
      // access and overwrite that generator. Only the blank template is safe.
      if(job.source!=='https://perchance.org/blank'){stop();status('paused','Штатное копирование не найдено. Создайте копию в Perchance и вставьте её ссылку через «Как?».');return;}
      job.clicked=true;status('running','Сохраняем новую страницу в Perchance…');save.click();
    }
  }
  window.GameBrothCreationDriver={result(token){
    if(!job||job.token!==token||!job.clicked)return '';
    const found=resultUrl(),url=gameUrl(found);
    return found!==job.initialResult&&url&&url!==job.source?url:'';
  },cancel(token){
    if(!job||job.token!==token)return;stop();job=null;
  },start(config){
    if(job?.token===config.token&&job.clicked){tick();return;}
    stop();job={token:config.token,source:config.source,initialResult:resultUrl(),clicked:false,phase:'',deadline:Date.now()+90000};
    status('running','Подготавливаем редактор Perchance…');timer=setInterval(tick,200);tick();
  }};
})();
