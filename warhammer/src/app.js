(function(){
  'use strict';
  const C=window.FantaCore,P=window.FantaPrompts,$=s=>document.querySelector(s),esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  let state,db,busy=false,route='story',message='',draft='',urls=new Map(),oldFrames=new Map();
  const button=(label,action,value='',cls='')=>`<button type="button" class="${cls}" data-action="${action}" data-value="${esc(value)}">${esc(label)}</button>`;
  function openDb(){return new Promise((ok,no)=>{const request=indexedDB.open('fanta-whfb',1);request.onupgradeneeded=()=>{for(const n of ['saves','media','meta'])request.result.createObjectStore(n);};request.onsuccess=()=>ok(request.result);request.onerror=()=>no(request.error);});}
  function transact(store,mode,action){return new Promise((ok,no)=>{const tx=db.transaction(store,mode),r=action(tx.objectStore(store));let value;r.onsuccess=()=>value=r.result;tx.oncomplete=()=>ok(value);tx.onerror=()=>no(tx.error);tx.onabort=()=>no(tx.error);});}
  const get=(store,key)=>transact(store,'readonly',s=>s.get(key)),put=(store,key,value)=>transact(store,'readwrite',s=>s.put(value,key));
  async function save(){const snapshot=C.copy(state);await new Promise((ok,no)=>{const tx=db.transaction(['saves','meta'],'readwrite');tx.oncomplete=ok;tx.onerror=()=>no(tx.error);tx.onabort=()=>no(tx.error||Error('Сохранение отменено'));try{tx.objectStore('saves').put(snapshot,snapshot.id);tx.objectStore('meta').put(snapshot.id,'current');}catch(e){tx.abort();no(e);}});}
  async function newGame(){if(busy)C.cancel(state);busy=false;state=C.create();draft='';route='story';message='';await save();render();}
  const same=(id,token)=>state?.pending?.id===id&&state.session===token;
  async function tool(name){const end=Date.now()+30000;while(Date.now()<end){const fn=typeof root!=='undefined'?root[name]:null;if(typeof fn==='function')return fn;await new Promise(r=>setTimeout(r,250));}throw Error('Плагин Perchance ещё не подключился. Подожди и нажми «Повторить».');}
  async function timed(job,ms){let timer;try{return await Promise.race([Promise.resolve().then(job),new Promise((_,no)=>timer=setTimeout(()=>no(Error('Ответ не пришёл вовремя (timeout). Запрос мог остаться на сервере.')),ms))]);}finally{clearTimeout(timer);}}
  async function generateText(instruction,id,token){const fn=await tool('generateText');if(!same(id,token))throw Error('Ожидание отменено');return P.json(await timed(()=>{if(!same(id,token))throw Error('Ожидание отменено');return fn({instruction});},120000));}
  async function execute(op){
    if(busy)return;const token=state.session,id=op.id;busy=op;message='';render();
    try{
      if(!op.applied){
        if(!op.plan){let plan;if(op.kind!=='turn')plan={intent:op.kind==='opening'?'Начало истории':'Завершение главы',risky:false,stakes:''};else plan=await generateText(P.planning(state,op),id,token);if(!same(id,token))return;C.plan(state,id,plan);await save();render();}
        if(op.status==='check')return;
        if(op.status==='paused')op.status='writing';
        if(!same(id,token))return;const result=await generateText(P.writing(state,op),id,token);if(!same(id,token))return;C.commit(state,id,result);await save();render();
      }
      await images(id,token);
    }catch(e){if(same(id,token)){C.fail(state,id,e.message);message=e.message;await save();}}
    finally{if(busy===op)busy=false;render();}
  }
  async function images(id,token){
    const entry=C.activeEntry(state);if(!entry)throw Error('Не найден применённый исход');
    for(const f of entry.frames){
      if(f.status==='succeeded')continue;if(!same(id,token))return;
      const request=C.startFrame(state,entry.id,f.id);await save();render();
      try{
        const fn=await tool('generateImage');if(!same(id,token))return;const result=await timed(()=>{if(!same(id,token))throw Error('Ожидание отменено');return fn(request);},120000);if(!same(id,token))return;
        const data=result?.dataUrl||(typeof result==='string'?result:'');if(!/^data:image\/(png|jpeg|webp);base64,/.test(data)||data.length>20000000)throw Error('Perchance не вернул поддерживаемое изображение');
        const blob=await(await fetch(data)).blob(),ref=state.id+'-'+entry.id+'-'+f.id+'-'+f.attempts;
        await put('media',ref,blob);if(!same(id,token))return;C.finishFrame(state,entry.id,f.id,ref,'',token);
      }catch(e){if(same(id,token))C.finishFrame(state,entry.id,f.id,null,e.message,token);}
      if(!same(id,token))return;await save();render();
    }
    if(same(id,token)){C.finishImages(state,id);await save();}
  }
  async function play(action,kind='turn',guaranteed=false){
    if(busy)return;if(state.pending){message='Сначала заверши кадр, бросок или отмени ожидание.';render();return;}
    const op=C.begin(state,action,kind,guaranteed);draft='';if($('#action'))$('#action').value='';localStorage.removeItem('fanta-draft:'+state.id);await save();await execute(op);
  }
  function imageMarkup(e,f){return `<figure class="scene-image">${f.mediaId?`<img data-media="${esc(f.mediaId)}" alt="${esc(e.title)}">`:`<div class="image-empty">${esc(f.status==='running'?'Perchance рисует…':f.status==='failed'||f.status==='unknown'?f.error||'Кадр не получен':'Кадр ещё не создан')}</div>`}<figcaption>${esc(f.anchor)}${e.frames.length>1?' · '+esc(f.id):''}</figcaption>${f.mediaId?`<div class="row">${button('Открыть крупно','view',e.id+'|'+f.id)}</div>`:''}</figure>`;}
  function setup(){
    if(state.step===1)return `<div class="setup"><div class="step">НОВАЯ ИГРА · ШАГ 1 ИЗ 3</div><h1>Старый Свет<br>ждёт свою героиню</h1><p class="goal">Короткое иллюстрированное приключение Warhammer Fantasy Battles. Твои решения, явные броски 2d6 и последствия, которые сохраняются.</p><form id="setup-style"><div class="form-grid"><div><label for="style">Изображения</label><select id="style" name="style">${Object.entries(C.STYLES).map(([k])=>`<option value="${k}">${{painting:'Живописная иллюстрация',cinema:'Кинематографично',comic:'Графический роман',realism:'Реалистичный рисунок'}[k]}</option>`).join('')}</select></div><div><label for="tone">Тон истории</label><select id="tone" name="tone">${Object.entries(C.TONES).map(([k,v])=>`<option value="${k}">${v}</option>`).join('')}</select></div><div><label for="dice">Броски</label><select id="dice" name="dice"><option value="auto">Авто · системная случайность</option><option value="manual">Вручную · я бросаю кубики</option></select></div><div><label for="frames">Кадров на сцену, максимум</label><select id="frames" name="frames"><option>1</option><option>2</option><option>3</option></select></div></div><label class="setting-line"><input type="checkbox" name="romance" checked> Допускать романтику и флирт</label><p class="service">Мир закреплён: Старый Свет до Конца времён. Местные события и герои — авторская история.</p><button class="primary buttons" type="submit">Выбрать завязку →</button></form></div>`;
    if(state.step===2)return `<div class="setup"><div class="step">НОВАЯ ИГРА · ШАГ 2 ИЗ 3</div><h1>Какая история зовёт?</h1><div class="cards">${C.SCENARIOS.map(x=>`<article class="card"><div class="eyebrow">${esc(x.role)}</div><h2>${esc(x.title)}</h2><p>${esc(x.goal)}</p><p class="muted">${esc(x.obstacle)}</p><small>${esc(x.hook)}</small><p></p>${button('Эта история →','scenario',x.id,'primary')}</article>`).join('')}</div><form id="setup-custom"><label for="custom">Или своя завязка в Старом Свете</label><textarea id="custom" maxlength="900" placeholder="Кто она, чего хочет и что мешает? Все персонажи взрослые."></textarea><button type="submit">Своя история →</button></form></div>`;
    return `<div class="setup"><div class="step">НОВАЯ ИГРА · ШАГ 3 ИЗ 3</div><h1>Твоя героиня</h1><p class="goal">${esc(state.scenario.title)} · ${esc(state.scenario.place)}</p><form id="setup-hero"><div class="form-grid"><div><label for="hero-name">Имя</label><input id="hero-name" name="name" maxlength="70" value="Адель Розен" required></div><div><label for="hero-age">Возраст · 21+</label><input id="hero-age" name="age" type="number" min="21" max="3000" value="27" required></div><div class="full"><label for="hero-race">Происхождение</label><select id="hero-race" name="race"><option>человек Империи</option><option>бретонка</option><option>высшая эльфийка</option><option>лесная эльфийка</option><option>тёмная эльфийка</option><option>дворфийка</option><option>полурослица</option></select></div><div class="full"><label for="hero-look">Постоянная внешность</label><textarea id="hero-look" name="look" maxlength="1000" required>Взрослое лицо, короткие тёмно-каштановые волосы, серые глаза, светлая кожа, небольшой шрам через левую бровь, крепкое телосложение.</textarea></div><div class="full"><label for="hero-wardrobe">Начальная одежда и вещи</label><textarea id="hero-wardrobe" name="wardrobe" maxlength="500">Тёмно-зелёный дорожный плащ, кожаный камзол, штаны и сапоги, меч в ножнах, поясная сумка и маленький латунный фонарь.</textarea></div></div><p class="service">Броски: ${state.settings.dice==='auto'?'crypto.getRandomValues, без подбора моделью':'два твоих кубика перед рискованным исходом'}. Рисунки: подключённый text-to-image-plugin Perchance; точное совпадение лиц между кадрами не гарантируется.</p><button type="submit" class="primary buttons">Начать приключение →</button></form></div>`;
  }
  function pending(){
    const op=state.pending;if(!op)return '';
    if(state.check)return `<section class="notice"><strong>Проверка 2d6</strong><p>${esc(state.check.intent)}</p><p>${esc(state.check.stakes)}</p><form id="manual-dice"><div class="row"><input name="d1" type="number" min="1" max="6" value="1" aria-label="Первый кубик" required><input name="d2" type="number" min="1" max="6" value="1" aria-label="Второй кубик" required><button class="primary" type="submit">Принять бросок</button></div></form></section>`;
    const status=op.status==='planning'?'Разбираем намерение…':op.status==='writing'?'Пишем сцену…':op.status==='images'?'Создаём иллюстрацию…':op.error||'Ожидание остановлено';
    return `<section class="notice ${op.status==='paused'?'error':''}"><strong>${esc(status)}</strong>${op.applied?'<p>Исход и номер хода уже сохранены. Повтор картинки не сыграет ход заново.</p>':'<p>Исход ещё не применён; ход не потрачен.</p>'}<div class="row">${!busy?button('Повторить','retry','','primary'):''}${button(op.applied?'Продолжить без кадра':'Отменить ожидание','cancel')}</div></section>`;
  }
  function story(){
    const latest=state.journal.at(-1);return `<p class="goal">${esc(state.scenario.goal)}</p>${pending()}${state.phase==='stopped'?`<div class="notice">Игра остановлена. Новые действия не выполняются. ${button('Продолжить','resume')}</div>`:''}${!latest?`<div class="empty">${busy?'Готовим начало истории…':'Начальная сцена ещё не создана.'}</div>`:''}${state.journal.slice().reverse().map(e=>`<article class="scene"><small>ХОД ${e.t} · ${esc(e.kind==='opening'?'НАЧАЛО':e.kind==='epilogue'?'ЭПИЛОГ':'ИСТОРИЯ')}</small><h2>${esc(e.title)}</h2>${e.roll?`<div class="dice">🎲 2d6: ${e.roll.values[0]} + ${e.roll.values[1]} = ${e.roll.total} · ${esc(e.roll.label)}<br><small>${e.roll.source==='player'?'Бросок игрока':'Системный генератор браузера'}</small></div>`:''}<p>${esc(e.narrative)}</p>${e.frames.map(f=>imageMarkup(e,f)).join('')}${e.ending?`<div class="notice">${esc(e.ending)}</div>`:''}${e===latest&&!state.pending&&!busy&&state.phase!=='stopped'?state.phase==='ended'?`<div class="choices">${button('Короткий эпилог','epilogue')}${button('Продолжить свободно','freeplay')}${button('Переиграть развязку','replay')}${button('Новая история','new')}</div>`:`<div class="choices">${e.choices.map(x=>button(x,'choice',x)).join('')}</div>`:''}</article>`).join('')}`;
  }
  function heroine(){return `<section class="card"><div class="eyebrow">ТВОЯ ГЕРОИНЯ</div><h1>${esc(state.heroine.name)}</h1><p>${esc(state.heroine.age)} лет · ${esc(state.heroine.race)}</p><p>${esc(state.heroine.look)}</p><h3>Сейчас</h3><p>${esc(state.location)}</p><p>${esc(state.wardrobe)}</p></section><section class="card"><h3>Цель и давление</h3><p>${esc(state.scenario.goal)}</p><p>Продвижение ${state.progress}/5</p><div class="progress"><span style="width:${state.progress*20}%"></span></div><p>Угроза ${state.pressure}/5</p><div class="progress"><span style="width:${state.pressure*20}%"></span></div><h3>Установленные факты</h3>${state.facts.map(x=>`<p>${esc(x)}</p>`).join('')||'<p class="muted">Пока нет отдельных записей.</p>'}<form id="world-fact"><label for="fact">Добавить авторский факт без хода</label><input id="fact" maxlength="500" placeholder="Например: в кармане письма лежит медный жетон."><button type="submit">Сохранить факт</button></form></section>`;}
  function gallery(){const all=state.journal.flatMap(e=>e.frames.filter(f=>f.mediaId).map(f=>({e,f})));return `<h1>Иллюстрации</h1><p class="muted">Сохранённые кадры этой истории. Просмотр не тратит ход.</p><div class="album">${all.map(({e,f})=>imageMarkup(e,f)).join('')||'<p class="empty">Первые кадры появятся после начала истории.</p>'}</div>`;}
  function saves(){return `<h1>Истории и настройки</h1><section class="card"><h3>Эта история</h3><p>${esc(state.heroine?.name||'Новая героиня')} · ход ${state.t} · ${esc(state.location)}</p><div class="row">${button('Скачать сохранение','export','','primary')}${button('Новая история','new')}</div><label for="import">Загрузить JSON с другого устройства</label><input id="import" type="file" accept=".json,application/json"><p class="service">Экспорт содержит состояние и сами изображения. Сохранения Broth и MNO имеют другие форматы.</p><label for="paste-save">Или вставить JSON</label><textarea id="paste-save" placeholder="Содержимое fanta-whfb-save/1"></textarea>${button('Импортировать JSON','import-text')}<h3>На этом устройстве</h3><div id="campaigns" class="save-list"></div></section><section class="card"><h3>Управление</h3><p>/stop · /cancel · /retry · /end · /save · /status<br>/fact текст · /must действие · /roll 3 4 · /dice auto · /dice manual</p><p class="service">Обычный ввод — действие героини. /must гарантирует заявленный исход без броска. /fact добавляет установленный факт без хода. Романтика — по согласию; интимные сцены остаются за кадром.</p><div class="row">${button('Остановить историю','stop','','danger')}${button('Завершить приключение','end')}</div><details><summary>Текущее состояние</summary><pre>${esc(JSON.stringify({turn:state.t,phase:state.phase,progress:state.progress,pressure:state.pressure,pending:state.pending?{kind:state.pending.kind,status:state.pending.status,applied:state.pending.applied}:null,dice:state.settings.dice,frames:state.settings.frames},null,2))}</pre></details></section>`;}
  function render(){
    if(!state)return;const input=$('#action');if(input)draft=input.value;
    const setupMode=state.phase==='setup';$('#fanta').innerHTML=`<div class="page"><header class="head"><div><div class="eyebrow">WARHAMMER FANTASY BATTLES</div><div class="brand">FANTA · Старый Свет</div></div><div class="head-right"><span>${setupMode?'Новая история':'Ход '+state.t}</span>${setupMode?'3 шага до начала':state.progress+'/5 цель · '+state.pressure+'/5 угроза'}</div></header><nav class="tabs" aria-label="Разделы игры">${[['story','История'],['hero','Героиня'],['gallery','Галерея'],['saves','Сейвы']].map(([k,v])=>`<button data-action="route" data-value="${k}" class="${route===k?'active':''}" ${setupMode&&k!=='story'&&k!=='saves'?'disabled':''}>${v}</button>`).join('')}</nav><main><div class="content">${message?`<div class="notice error" role="alert">${esc(message)}</div>`:''}${route==='saves'?saves():setupMode?setup():({story,hero:heroine,gallery}[route]||story)()}</div></main>${!setupMode?`<footer class="foot"><form id="action-form" class="composer"><input id="action" aria-label="Действие героини" maxlength="1200" placeholder="Что делает ${esc(state.heroine.name)}?" ${busy||state.pending||state.phase!=='play'?'disabled':''}><button type="submit" class="primary" aria-label="Отправить действие" ${busy||state.pending||state.phase!=='play'?'disabled':''}>↗</button></form></footer>`:'<footer class="foot"><small>Отдельная история · автоматическое сохранение на устройстве</small></footer>'}</div><dialog id="viewer"><div class="viewer-bar"><strong id="viewer-title"></strong>${button('Увеличить','zoom')}${button('Закрыть','close-view')}</div><div class="viewer-scroll"><img id="viewer-image" alt="Иллюстрация сцены"></div><p class="viewer-caption" id="viewer-caption"></p></dialog>`;
    if($('#action'))$('#action').value=draft;hydrate();if(route==='saves')listCampaigns();
  }
  async function mediaUrl(ref){let url=urls.get(ref);if(!url){const blob=await get('media',ref);if(!(blob instanceof Blob))throw Error('Изображение отсутствует в сохранении');url=URL.createObjectURL(blob);urls.set(ref,url);}return url;}
  async function hydrate(){for(const el of document.querySelectorAll('[data-media]'))try{const url=await mediaUrl(el.dataset.media);if(el.isConnected)el.src=url;}catch(e){el.alt=e.message;}}
  async function listCampaigns(){const list=await transact('saves','readonly',s=>s.getAll()),el=$('#campaigns');if(el)el.innerHTML=list.reverse().map(x=>button((x.heroine?.name||'Настройка новой игры')+' · ход '+x.t+' · '+(x.scenario?.title||'Выбор истории'),'campaign',x.id)).join('');}
  async function exportSave(){
    const snapshot=C.copy(state),media={};for(const ref of C.mediaRefs(snapshot)){const b=await get('media',ref);if(!b)throw Error('Нет изображения '+ref);media[ref]=await new Promise((ok,no)=>{const r=new FileReader();r.onload=()=>ok(r.result);r.onerror=()=>no(r.error);r.readAsDataURL(b);});}
    const text=JSON.stringify({format:'fanta-whfb-save/1',state:snapshot,media},null,2),name='fanta-whfb-turn-'+snapshot.t+'.json';
    if(window.GameBrothFiles){window.GameBrothFiles.postMessage(JSON.stringify({name,text}));message='Открывается системный выбор места сохранения.';}else{const url=URL.createObjectURL(new Blob([text],{type:'application/json'})),a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),30000);message='Файл сохранения подготовлен.';}render();
  }
  async function importSave(text){
    if(busy)throw Error('Сначала отмени текущее ожидание');if(text.length>100000000)throw Error('Файл слишком большой');
    const payload=JSON.parse(text);if(payload.format!=='fanta-whfb-save/1')throw Error('Это не сохранение FANTA');const candidate=C.restore(payload.state),prepared=[];
    for(const ref of C.mediaRefs(candidate)){const data=payload.media?.[ref];if(typeof data!=='string'||!/^data:image\/(png|jpeg|webp);base64,/.test(data)||data.length>20000000)throw Error('Повреждено изображение '+ref);const blob=await(await fetch(data)).blob();prepared.push([ref,blob]);}
    const existing=await get('saves',candidate.id);if(existing){candidate.id=crypto.randomUUID();const mapping=new Map();for(const [ref] of prepared)mapping.set(ref,candidate.id+'-'+ref);for(const e of candidate.journal)for(const f of e.frames)if(f.mediaId)f.mediaId=mapping.get(f.mediaId);for(const item of prepared)item[0]=mapping.get(item[0]);}
    await new Promise((ok,no)=>{const tx=db.transaction(['saves','media','meta'],'readwrite');tx.oncomplete=ok;tx.onerror=()=>no(tx.error);tx.onabort=()=>no(tx.error||Error('Импорт отменён'));try{for(const [ref,blob] of prepared)tx.objectStore('media').put(blob,ref);tx.objectStore('saves').put(candidate,candidate.id);tx.objectStore('meta').put(candidate.id,'current');}catch(e){tx.abort();no(e);}});state=candidate;route='story';draft='';message='История загружена. Незавершённые операции ждут твоего решения.';render();
  }
  async function command(value){
    if(value==='/save')return exportSave();if(value==='/status'){route='hero';render();return;}if(value==='/stop'){C.stop(state);busy=false;await save();render();return;}if(value==='/cancel'){C.cancel(state);busy=false;await save();render();return;}
    if(value==='/retry')return action('retry');if(value==='/new')return newGame();if(value==='/end')return play('Завершить текущую цель честной развязкой, сохранив установленные последствия.','ending');
    if(value.startsWith('/fact ')){C.fact(state,value.slice(6));await save();render();return;}if(value.startsWith('/must '))return play(value.slice(6),'turn',true);
    if(/^\/dice (auto|manual)$/.test(value)){C.configure(state,{dice:value.split(' ')[1]});await save();render();return;}
    if(value.startsWith('/roll ')){const values=value.split(/\s+/).slice(1).map(Number);const op=C.manualRoll(state,values);await save();return execute(op);}
    if(value.startsWith('/'))throw Error('Неизвестная команда. Список — в «Сейвах».');return play(value);
  }
  async function action(name,value){
    if(name==='route'){route=value;render();return;}if(name==='new')return newGame();if(name==='export')return exportSave();
    if(name==='scenario'){C.chooseScenario(state,value);await save();render();return;}
    if(name==='choice')return play(value);if(name==='end')return command('/end');if(name==='stop')return command('/stop');if(name==='cancel')return command('/cancel');
    if(name==='resume'){C.resume(state);await save();render();return;}if(name==='freeplay'){state.mode='freeplay';state.phase='play';state.rev++;await save();render();return;}
    if(name==='replay'){C.replay(state);await save();render();return;}if(name==='epilogue')return play('Короткий эпилог: покажи последствия развязки без нового обязательного конфликта.','epilogue');
    if(name==='retry'){
      if(busy||!state.pending)return;if(state.check)return;
      const op=state.pending;if(op.applied&&C.activeEntry(state)?.frames.some(f=>f.status==='unknown')&&!confirm('Прошлый запрос мог завершиться на сервере. Повтор может создать ещё одну картинку. Повторить?'))return;
      op.status=op.applied?'images':op.plan?'writing':'planning';await save();return execute(op);
    }
    if(name==='campaign'){if(busy)throw Error('Сначала отмени ожидание');state=C.restore(await get('saves',value));draft=localStorage.getItem('fanta-draft:'+state.id)||'';route='story';message='';await save();render();return;}
    if(name==='import-text')return importSave($('#paste-save').value);
    if(name==='view'){const [id,frame]=value.split('|'),entry=state.journal.find(e=>e.id===id),f=entry?.frames.find(x=>x.id===frame);if(!f?.mediaId)throw Error('Кадр не найден');$('#viewer-image').src=await mediaUrl(f.mediaId);$('#viewer-title').textContent=entry.title;$('#viewer-caption').textContent=f.anchor;$('#viewer').showModal();return;}
    if(name==='zoom'){$('.viewer-scroll').classList.toggle('zoom');return;}if(name==='close-view'){$('#viewer').close();return;}
  }
  async function attempt(work){try{await work();}catch(e){message=e.message;render();}}
  document.addEventListener('click',event=>{const b=event.target.closest('[data-action]');if(b&&!b.disabled)attempt(()=>action(b.dataset.action,b.dataset.value));});
  document.addEventListener('input',event=>{if(event.target.id==='action'){draft=event.target.value;localStorage.setItem('fanta-draft:'+state.id,draft);}});
  document.addEventListener('change',event=>{if(event.target.id==='import'&&event.target.files[0])attempt(async()=>importSave(await event.target.files[0].text()));});
  document.addEventListener('submit',event=>{
    const form=event.target;event.preventDefault();attempt(async()=>{
      const values=Object.fromEntries(new FormData(form));
      if(form.id==='setup-style'){C.configure(state,{...values,frames:Number(values.frames),romance:!!values.romance});await save();render();}
      if(form.id==='setup-custom'){C.chooseScenario(state,'custom',$('#custom').value);await save();render();}
      if(form.id==='setup-hero'){C.setHeroine(state,values);await save();await play('Открой приключение: героиня прибывает в исходное место с ясной ближайшей задачей.','opening');}
      if(form.id==='manual-dice'){const op=C.manualRoll(state,[Number(values.d1),Number(values.d2)]);await save();await execute(op);}
      if(form.id==='world-fact'){C.fact(state,$('#fact').value);await save();render();}
      if(form.id==='action-form'){const value=$('#action').value.trim();draft='';$('#action').value='';await command(value);}
    });
  });
  function viewport(){document.documentElement.style.setProperty('--fanta-height',(window.visualViewport?.height||innerHeight)+'px');}
  window.addEventListener('resize',viewport);window.visualViewport?.addEventListener('resize',viewport);viewport();
  (async()=>{try{db=await openDb();const key=await get('meta','current'),stored=key&&await get('saves',key);state=stored?C.restore(stored):C.create();draft=localStorage.getItem('fanta-draft:'+state.id)||'';await save();render();}catch(e){$('#fanta').innerHTML='<main><h1>Не удалось открыть сохранения</h1><p>'+esc(e.message)+'</p><p>Проверь, разрешено ли хранение данных в этом браузере.</p></main>';}})();
})();
