(function(scope){
  'use strict';
  const VERSION='fanta-whfb/1',copy=x=>JSON.parse(JSON.stringify(x));
  const STYLES={painting:'Painterly dark fantasy illustration, textured brushwork, muted ochre and forest green',cinema:'Cinematic fantasy illustration, natural light, detailed period costumes',comic:'European fantasy graphic novel illustration, clear ink lines, rich shadows',realism:'Realistic fantasy concept art, natural proportions, atmospheric light'};
  const TONES={adventure:'Приключение',grimdark:'Мрачная история',comedy:'Чёрная комедия'};
  const SCENARIOS=[
    {id:'ash',title:'Печать в пепле',role:'Курьер в Нульне',place:'Нульн, типография у канала',goal:'Доставить подлинный оттиск печати свидетелю до того, как его найдут люди заговорщика.',obstacle:'Типография сгорела; единственный свидетель боится охотника на ведьм.',hook:'Старый знакомый предлагает помощь, но скрывает, почему узнаёт печать.'},
    {id:'salt',title:'Соль и тени',role:'Сыщица в порту Мариенбурга',place:'Мариенбург, склад у приливного канала',goal:'Найти пропавшую судовую книгу и вывести живым человека, который знает её шифр.',obstacle:'Склад делят контрабандисты и люди купеческого дома; прилив отрезает выход.',hook:'Очаровательная посредница предлагает рискованную совместную сделку.'},
    {id:'stone',title:'Долг в камне',role:'Проводница дворфийской экспедиции',place:'Рейкланд, старая горная дорога',goal:'Вернуть именной жетон пропавшего мастера и установить, почему замолчала сигнальная станция.',obstacle:'Мост разрушен, а под станцией слышен работающий механизм.',hook:'Напарница верит героине больше, чем нанявшему их караванщику.'},
    {id:'mask',title:'Маска под дождём',role:'Посланница при бретонском дворе',place:'Бретония, загородная усадьба во время маскарада',goal:'Вывести свидетельницу с приёма и передать доказательство подмены письма.',obstacle:'Ворота заперты; один из гостей выдаёт себя за отсутствующего человека.',hook:'Танец с союзником даёт возможность поговорить без свидетелей.'}
  ];
  const uid=()=>{if(!scope.crypto?.randomUUID)throw Error('Браузеру нужен crypto.randomUUID');return scope.crypto.randomUUID();};
  const text=(v,max=1600)=>typeof v==='string'?v.trim().slice(0,max):'';
  function need(ok,message){if(!ok)throw Error(message);}
  function create(id=uid()){
    return {schema:VERSION,id,session:uid(),seq:0,rev:0,t:0,phase:'setup',step:1,settings:{style:'painting',tone:'adventure',dice:'auto',frames:1,romance:true},scenario:null,heroine:null,progress:0,pressure:0,location:'',scene:'',wardrobe:'',facts:[],journal:[],pending:null,check:null,queue:null,preEnd:null};
  }
  function configure(s,input){
    need(!s.pending,'Сначала заверши или отмени текущее действие');
    for(const [key,value] of Object.entries(input)){
      if(key==='style'&&STYLES[value])s.settings.style=value;
      if(key==='tone'&&TONES[value])s.settings.tone=value;
      if(key==='dice'&&['auto','manual'].includes(value))s.settings.dice=value;
      if(key==='frames'&&Number.isInteger(value)&&value>=1&&value<=3)s.settings.frames=value;
      if(key==='romance'&&typeof value==='boolean')s.settings.romance=value;
    }
    s.rev++;if(s.phase==='setup'&&s.step===1)s.step=2;return s;
  }
  function chooseScenario(s,id,custom){
    need(s.phase==='setup'&&s.step===2,'Сценарий выбирается на втором шаге');
    if(id==='custom'){
      need(text(custom,900).length>=20,'Опиши свою завязку хотя бы одним предложением');
      s.scenario={id:'custom',title:'Своя история',role:'Искательница приключений',place:'Старый Свет',goal:text(custom,900),obstacle:'Препятствие должно следовать из завязки.',hook:''};
    }else{const scenario=SCENARIOS.find(x=>x.id===id);need(scenario,'Неизвестный сценарий');s.scenario=copy(scenario);}
    s.location=s.scenario.place;s.step=3;s.rev++;return s;
  }
  function setHeroine(s,input){
    need(s.phase==='setup'&&s.step===3,'Героиня создаётся на третьем шаге');
    const age=Number(input.age),name=text(input.name,70),look=text(input.look,1000),race=text(input.race,80);
    need(Number.isInteger(age)&&age>=21&&age<=3000,'Укажи взрослый возраст от 21 года');
    need(name.length>=2&&look.length>=12&&race,'Заполни имя, происхождение и внешность');
    s.heroine={name,age,race,look,lock:`Original adult fictional woman, ${age} years old; ancestry: ${race}; identity: ${name}; stable appearance: ${look}`};
    s.wardrobe=text(input.wardrobe,500)||'дорожный плащ, практичная одежда, поясная сумка, сапоги';
    s.phase='play';s.step=3;s.rev++;return s;
  }
  function dice(values){
    need(Array.isArray(values)&&values.length===2&&values.every(x=>Number.isInteger(x)&&x>=1&&x<=6),'Нужны два целых числа от 1 до 6');
    const total=values[0]+values[1],kind=total===2?'critical-failure':total<=6?'failure':total<=9?'mixed':total<=11?'success':'critical-success';
    const label={'critical-failure':'Критическая неудача',failure:'Неудача с продолжением',mixed:'Успех с осложнением',success:'Полный успех','critical-success':'Критический успех'}[kind];
    return {values:[...values],total,kind,label};
  }
  function roll(randomBytes){
    const source=randomBytes||(()=>{need(scope.crypto?.getRandomValues,'Нет системного генератора случайных чисел');return scope.crypto.getRandomValues(new Uint8Array(1))[0];});
    const one=()=>{for(let i=0;i<100;i++){const n=source();need(Number.isInteger(n)&&n>=0&&n<=255,'Некорректный источник случайных чисел');if(n<252)return n%6+1;}throw Error('Источник случайных чисел не ответил');};
    return {...dice([one(),one()]),source:'crypto.getRandomValues'};
  }
  function begin(s,action,kind='turn',guaranteed=false){
    need(!s.pending&&!s.check,'Предыдущее действие ещё не завершено');
    need(['play','ended'].includes(s.phase),'Продолжение остановлено');
    need(kind!=='turn'||s.phase==='play','История завершена: выбери эпилог или свободную игру');
    need(kind!=='opening'||s.journal.length===0,'Начало уже сыграно');
    need(['turn','opening','ending','epilogue'].includes(kind),'Неизвестное действие');
    action=text(action,1200);need(action,'Опиши действие');
    s.seq++;s.pending={id:s.session+':'+s.seq,kind,action,guaranteed:!!guaranteed,sourceRev:s.rev,status:'planning',applied:false,roll:null,plan:null,entryId:null,error:'',startedSession:s.session};
    return s.pending;
  }
  function plan(s,opId,value,randomBytes){
    const op=s.pending;need(op&&op.id===opId&&!op.applied&&op.sourceRev===s.rev,'Устаревшее действие');
    need(op.status==='planning','План этого действия уже принят');
    need(value&&typeof value.risky==='boolean'&&text(value.intent,500),'Модель не вернула проверяемый план');
    op.plan={intent:text(value.intent,500),stakes:text(value.stakes,700),risky:value.risky&&!op.guaranteed&&op.kind==='turn'};
    if(op.plan.risky&&s.settings.dice==='manual'){
      op.status='check';s.check={opId,intent:op.plan.intent,stakes:op.plan.stakes,sourceRev:s.rev};return op;
    }
    if(op.plan.risky)op.roll=s.replayRoll?copy(s.replayRoll):roll(randomBytes);
    delete s.replayRoll;op.status='writing';return op;
  }
  function manualRoll(s,values){
    const check=s.check,op=s.pending;need(check&&op&&check.opId===op.id&&check.sourceRev===s.rev&&op.status==='check','Сейчас нет ожидающего броска');
    op.roll={...dice(values),source:'player'};s.check=null;op.status='writing';return op;
  }
  function normalize(v){return text(v,12000).replace(/\s+/g,' ').toLowerCase();}
  function outcome(value,maxFrames=1){
    need(value&&typeof value==='object','Пустой ответ модели');
    const narrative=text(value.narrative,6000),choices=Array.isArray(value.choices)?value.choices.map(x=>text(x,220)).filter(Boolean):[];
    need(narrative.length>=70&&choices.length>=2&&choices.length<=4,'Нужны сцена и от двух до четырёх действий');
    need(typeof value.milestone==='boolean'&&typeof value.threat==='boolean','Нет признаков прогресса и угрозы');
    need(Array.isArray(value.frames)&&value.frames.length>=1&&value.frames.length<=maxFrames,'Нет согласованного набора кадров');
    const frames=value.frames.map((f,i)=>{const anchor=text(f.anchor,500),description=text(f.description,1800);need(anchor.length>=8&&normalize(narrative).includes(normalize(anchor)),'Кадр не привязан цитатой к рассказу');need(description.length>=20,'Нет описания кадра');return {id:'F'+(i+1),anchor,description,status:'not_requested',attempts:0,mediaId:null,error:''};});
    return {narrative,choices,frames,milestone:value.milestone,threat:value.threat,title:text(value.title,100)||'Новая сцена',location:text(value.location,200),scene:text(value.scene,800),wardrobe:text(value.wardrobe,500),fact:text(value.fact,500),ending:text(value.ending,500)};
  }
  function commit(s,opId,raw){
    const op=s.pending;need(op&&op.id===opId&&!op.applied&&op.status==='writing'&&op.sourceRev===s.rev,'Этот исход уже применён или устарел');
    const value=outcome(raw,s.settings.frames),before={t:s.t,progress:s.progress,pressure:s.pressure,location:s.location,scene:s.scene,wardrobe:s.wardrobe,facts:copy(s.facts),journalLength:s.journal.length,phase:s.phase};
    const advances=['turn','ending'].includes(op.kind);if(advances)s.t++;
    const canProgress=!op.roll||!['failure','critical-failure'].includes(op.roll.kind);
    if(advances&&value.milestone&&canProgress)s.progress=Math.min(5,s.progress+1);
    if(advances&&(value.threat||op.roll&&['failure','critical-failure','mixed'].includes(op.roll.kind)))s.pressure=Math.min(5,s.pressure+1);
    if(value.location)s.location=value.location;if(value.scene)s.scene=value.scene;if(value.wardrobe)s.wardrobe=value.wardrobe;
    if(value.fact&&!s.facts.includes(value.fact))s.facts=[...s.facts,value.fact].slice(-40);
    const ended=op.kind!=='epilogue'&&(op.kind==='ending'||s.progress===5||s.t>=20&&s.mode!=='freeplay');
    if(ended){s.preEnd={before,roll:copy(op.roll)};s.phase='ended';}
    const entry={id:op.id,kind:op.kind,t:s.t,action:op.action,roll:copy(op.roll),title:value.title,narrative:value.narrative,choices:value.choices,frames:value.frames,ending:ended?(value.ending||'Развязка достигнута. Последствия описаны в сцене.'):'',scene:s.scene,location:s.location,wardrobe:s.wardrobe};
    s.journal.push(entry);s.rev++;op.applied=true;op.entryId=entry.id;op.status='images';return entry;
  }
  function activeEntry(s){return s.journal.find(x=>x.id===s.pending?.entryId);}
  function frameRequest(s,entry,frame){
    return {prompt:[s.heroine.lock,'Scene moment: '+frame.description,'Location: '+entry.location,'Current costume: '+entry.wardrobe,STYLES[s.settings.style],'Warhammer Fantasy Battles, the Old World; late medieval European material culture, black powder and subtle magic; ordinary clothed adults; readable scene, no lettering'].join('. '),negativePrompt:'child, teen, nudity, explicit sexual content, gore, science fiction, modern clothes, text, watermark, extra limbs',resolution:'768x512',seed:seed(s.id+entry.id+frame.id)};
  }
  function seed(v){let h=2166136261;for(const c of v)h=Math.imul(h^c.charCodeAt(0),16777619);return h>>>1;}
  function startFrame(s,entryId,frameId){
    const entry=s.journal.find(x=>x.id===entryId),frame=entry?.frames.find(x=>x.id===frameId);need(frame,'Кадр не найден');need(frame.status!=='running','Этот кадр уже создаётся');
    frame.status='running';frame.attempts++;frame.error='';return frameRequest(s,entry,frame);
  }
  function finishFrame(s,entryId,frameId,mediaId,error,token){
    if(token!==s.session||s.phase==='stopped')return false;
    const entry=s.journal.find(x=>x.id===entryId),f=entry?.frames.find(x=>x.id===frameId);if(!f||f.status!=='running')return false;
    f.mediaId=mediaId||null;f.status=mediaId?'succeeded':/timeout|не пришла/i.test(error||'')?'unknown':'failed';f.error=text(error,500);return true;
  }
  function finishImages(s,opId){
    const op=s.pending;if(!op||op.id!==opId||!op.applied)return;
    const entry=activeEntry(s);if(entry.frames.some(f=>f.status==='succeeded'))s.pending=null;else{op.status='paused';op.error='Кадр не получен. Исход сохранён; продолжение приостановлено.';}
  }
  function fail(s,opId,error){if(s.pending?.id===opId){s.pending.status='paused';s.pending.error=text(error,700);}}
  function cancel(s){
    if(s.pending?.applied){const entry=activeEntry(s);for(const f of entry?.frames||[])if(!f.mediaId)f.status='cancelled';}
    s.pending=null;s.check=null;s.queue=null;s.session=uid();s.rev++;return s;
  }
  function stop(s){cancel(s);s.resumePhase=s.phase;s.phase='stopped';return s;}
  function resume(s){need(s.phase==='stopped','Игра не остановлена');s.phase=s.resumePhase==='ended'?'ended':'play';delete s.resumePhase;s.rev++;}
  function fact(s,value){need(!s.pending,'Сначала заверши текущее действие');value=text(value,500);need(value,'Пустой факт');s.facts=[...s.facts,value].slice(-40);s.rev++;}
  function replay(s){
    need(s.phase==='ended'&&!s.pending&&s.preEnd,'Нет сохранённой развязки');
    const {before,roll}=copy(s.preEnd),seq=s.seq,rev=s.rev;Object.assign(s,before);s.journal=s.journal.slice(0,before.journalLength);delete s.journalLength;s.phase='play';s.seq=seq;s.rev=rev+1;s.session=uid();s.replayRoll=roll;s.preEnd=null;
  }
  function validate(raw){
    need(raw&&raw.schema===VERSION,'Несовместимое сохранение');
    const s=copy(raw);for(const k of ['id','session'])need(typeof s[k]==='string'&&s[k].length<160,'Повреждён идентификатор');
    for(const k of ['seq','rev','t','step','progress','pressure'])need(Number.isSafeInteger(s[k])&&s[k]>=0,'Повреждён счётчик '+k);
    need(s.t<=10000&&s.progress<=5&&s.pressure<=5&&s.step>=1&&s.step<=3,'Счётчики вне диапазона');
    need(['setup','play','ended','stopped'].includes(s.phase),'Неизвестная фаза');
    need(s.settings&&STYLES[s.settings.style]&&TONES[s.settings.tone]&&['auto','manual'].includes(s.settings.dice)&&[1,2,3].includes(s.settings.frames)&&typeof s.settings.romance==='boolean','Повреждены настройки');
    need(Array.isArray(s.journal)&&s.journal.length<=1200&&Array.isArray(s.facts)&&s.facts.length<=40&&s.facts.every(x=>typeof x==='string'&&x.length<=500),'Повреждён журнал');
    if(s.phase!=='setup')need(s.scenario&&s.heroine&&Number.isInteger(s.heroine.age)&&s.heroine.age>=21&&s.heroine.age<=3000&&typeof s.heroine.lock==='string'&&typeof s.heroine.name==='string','Нет взрослой героини или завязки');
    const ids=new Set();for(const e of s.journal){need(typeof e.id==='string'&&!ids.has(e.id)&&Number.isSafeInteger(e.t)&&e.t>=0&&e.t<=s.t&&typeof e.narrative==='string'&&e.narrative.length<=6000,'Повреждено событие');ids.add(e.id);need(Array.isArray(e.frames)&&e.frames.length>=1&&e.frames.length<=3&&Array.isArray(e.choices)&&e.choices.length>=2&&e.choices.length<=4,'Повреждены кадры или варианты');for(const f of e.frames){need(['not_requested','running','succeeded','failed','unknown','cancelled'].includes(f.status)&&typeof f.id==='string'&&typeof f.description==='string'&&typeof f.anchor==='string'&&normalize(e.narrative).includes(normalize(f.anchor))&&Number.isInteger(f.attempts)&&f.attempts>=0,'Повреждены метаданные кадра');need(f.mediaId===null||typeof f.mediaId==='string'&&f.mediaId.startsWith(s.id+'-'),'Неверная ссылка на картинку');if(f.status==='succeeded')need(f.mediaId,'Нет сохранённой картинки');}}
    if(s.pending){need(typeof s.pending.id==='string'&&typeof s.pending.applied==='boolean'&&['planning','writing','images','check','paused'].includes(s.pending.status),'Повреждена операция');need(!s.pending.applied||ids.has(s.pending.entryId),'Нет применённого исхода');}
    if(s.check)need(s.pending&&!s.pending.applied&&s.pending.status==='check'&&s.check.opId===s.pending.id&&s.check.sourceRev===s.rev,'Бросок не соответствует действию');
    return s;
  }
  function restore(raw){const s=validate(raw);s.session=uid();for(const e of s.journal)for(const f of e.frames)if(f.status==='running'){f.status='unknown';f.error='Перезагрузка во время генерации: результат неизвестен';}if(s.pending&&s.pending.status!=='check'){s.pending.status='paused';s.pending.error=s.pending.error||'Загрузка остановила незавершённую операцию. Автоматического повтора нет.';}return s;}
  function mediaRefs(s){return [...new Set(s.journal.flatMap(e=>e.frames.map(f=>f.mediaId)).filter(Boolean))];}
  const api={VERSION,STYLES,TONES,SCENARIOS,create,configure,chooseScenario,setHeroine,dice,roll,begin,plan,manualRoll,outcome,commit,activeEntry,frameRequest,startFrame,finishFrame,finishImages,fail,cancel,stop,resume,fact,replay,validate,restore,mediaRefs,copy};
  if(typeof module!=='undefined'&&module.exports)module.exports=api;else scope.FantaCore=api;
})(typeof window!=='undefined'?window:globalThis);
