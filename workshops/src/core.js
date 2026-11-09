(function(scope){
'use strict';
const copy=v=>JSON.parse(JSON.stringify(v)),need=(v,m)=>{if(!v)throw Error(m);},text=(v,max=2000)=>{need(typeof v==='string'&&v.length<=max,'Некорректный текст');return v.trim();};
const BASES=['двуногий','четвероногий','насекомообразный','рептилия','птицеподобный','многоногий','щупальцевый','безногий'];
const TRIALS=['Первый контакт','Поиск пищи','Полоса препятствий','Совместная задача','Возвращение в питомник'];
const MATERIALS=[['Соль','Пепел','Медь','Воск','Шёлк','Глина'],['Лунное стекло','Серебряный мох','Янтарная смола'],['Тень кристалла','Пыль кометы','Живое эхо']];
const MUTATIONS=['форма становится полупрозрачной','в образе проявляются два мотива','фактура меняется на соседний материал','голос приобретает неожиданное звучание','главная деталь становится едва заметной','роль смещается к соседней профессии'];
function uid(){return Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,10);}
function die(){const b=new Uint32Array(1);do{crypto.getRandomValues(b);}while(b[0]>=4294967292);return b[0]%6+1;}
function pick(a){const b=new Uint32Array(1),limit=Math.floor(4294967296/a.length)*a.length;do{crypto.getRandomValues(b);}while(b[0]>=limit);return a[b[0]%a.length];}
function create(mode){need(['nursery','alchemy'].includes(mode),'Неизвестная игра');return {schema:1,mode,id:uid(),n:0,cards:[],entries:[],current:null,pending:null,master:0,successes:0,temperBonus:0,finds:0,inventory:[{id:'salt',name:'Соль',rarity:0},{id:'ash',name:'Пепел',rarity:0},{id:'copper',name:'Медь',rarity:0}]};}
function materials(raw,min,max){const items=text(raw,500).split(/[,;+\n]/).map(x=>x.trim()).filter(Boolean);need(items.length>=min&&items.length<=max,`Нужно от ${min} до ${max} компонентов через запятую`);need(new Set(items.map(x=>x.toLowerCase())).size===items.length,'Компоненты не должны повторяться');return items;}
function current(s){return s.cards.find(c=>c.id===s.current);}
function begin(s,kind,input='',options={},rng=die,choose=pick){
 need(!s.pending,'Сначала заверши или отмени текущую операцию');const card=current(s);let op={id:'E'+(s.n+1),kind,input:text(input,1000),applied:false,status:'writing',cardId:card?.id||null,frameCount:1};
 if(s.mode==='nursery'){
  if(kind==='create'){op.materials=materials(input,2,4);const last=s.cards.at(-1)?.base;op.base=options.base&&BASES.includes(options.base)?options.base:choose(BASES.filter(x=>x!==last));}
  else {need(card,'Сначала собери существо');need(['revise','trial'].includes(kind),'Неизвестное действие');op.base=card.base;op.materials=card.materials;
   if(kind==='revise')need(!card.approved&&input.trim(),'Правка доступна до утверждения эталона');
   if(kind==='trial'){need(card.approved&&card.stage<5,'Испытания уже завершены или эталон не утверждён');op.stage=card.stage+1;op.input=TRIALS[card.stage];}
  }
 }else{
  need(['create','modify','trial','temper','combine'].includes(kind),'Неизвестное действие');
  if(kind!=='create')need(card,'Выбери карточку');
  if(kind==='combine'){need(options.other&&options.other!==card.id,'Выбери вторую карточку');need(s.cards.some(c=>c.id===options.other),'Вторая карточка не найдена');op.other=options.other;}
  op.materials=kind==='create'?materials(input,1,3):card.materials;
  op.frameCount=[1,2,3].includes(Number(options.frames))?Number(options.frames):2;
  op.consumed=s.inventory.filter(m=>m.rarity>0&&op.materials.some(x=>x.toLowerCase()===m.name.toLowerCase())).map(m=>m.id);
  const selected=s.inventory.filter(m=>op.consumed.includes(m.id)),bonus=selected.filter(m=>m.rarity===1).length,anomaly=selected.filter(m=>m.rarity===2).length;
  op.roll={die:rng(),master:s.master,bonus,anomaly,wear:card&&kind!=='create'?(card.wear||0):0,target:2+op.materials.length};
  need(Number.isInteger(op.roll.die)&&op.roll.die>=1&&op.roll.die<=6,'Неверный кубик');
  op.roll.total=op.roll.die+s.master+bonus-anomaly-op.roll.wear;op.roll.success=op.roll.total>=op.roll.target;
  if(!op.roll.success){const d=rng();need(Number.isInteger(d)&&d>=1&&d<=6,'Неверный кубик срыва');op.roll.mutationDie=d;op.roll.mutation=MUTATIONS[d-1];}
 }
 s.n++;s.pending=op;return op;
}
function cardData(raw){need(raw&&typeof raw==='object','Нет карточки');const c={};for(const k of ['name','concept','appearance','mark','voice','traits','location']){c[k]=text(raw[k],k==='appearance'?1800:500);need(c[k].length>=2,'Не заполнено поле '+k);}c.age=Number(raw.age);need(Number.isInteger(c.age)&&c.age>=21&&c.age<=90,'Персонаж должен быть взрослым, от 21 года');return c;}
function commit(s,id,raw){const op=s.pending;need(op&&op.id===id&&!op.applied,'Операция уже применена или отменена');need(raw&&typeof raw==='object','Ответ не является объектом');
 const narrative=text(raw.narrative,5000),title=text(raw.title,120);need(narrative.length>=40&&title,'Нужен законченный рассказ');
 need(Array.isArray(raw.frames)&&raw.frames.length===op.frameCount,'Неверное число кадров');
 const frames=raw.frames.map((f,i)=>{const anchor=text(f.anchor,600),description=text(f.description,2200);need(anchor.length>=8&&narrative.includes(anchor),'Кадр должен содержать точную цитату из рассказа');need(description.length>=20,'Нет описания кадра');return {id:'F'+(i+1),anchor,description,status:'pending',mediaId:null,error:'',attempts:0};});
 let card=current(s);const fresh=op.kind==='create'||op.kind==='combine';
 if(fresh||op.kind==='revise'||op.kind==='modify'){
  const value=cardData(raw.card);card={...value,id:fresh?'C'+op.id:card.id,materials:copy(op.materials),base:op.base||'',approved:false,stage:0,wear:0,tempers:0,...(!fresh?{approved:card.approved,stage:card.stage,wear:card.wear,tempers:card.tempers}:{})};
 }else need(card&&card.id===op.cardId,'Исходная карточка изменилась');
 const entry={id:op.id,cardId:card.id,kind:op.kind,title,narrative,frames,roll:copy(op.roll||null),stage:op.stage||0,canon:card.appearance};
 if(s.mode==='alchemy'){
  if(op.roll.success){s.successes++;if(op.kind==='temper'){s.temperBonus++;card={...card,wear:card.wear+1,tempers:card.tempers+1};}}
  s.master=Math.min(5,Math.floor(s.successes/3)+s.temperBonus);s.inventory=s.inventory.filter(m=>!op.consumed.includes(m.id));
 }
 if(s.mode==='nursery'&&op.kind==='trial')card={...card,stage:op.stage};
 const at=s.cards.findIndex(c=>c.id===card.id);if(at<0)s.cards.push(card);else s.cards[at]=card;
 s.current=card.id;s.entries.push(entry);op.applied=true;op.entryId=entry.id;op.status='images';return entry;
}
function approve(s){const c=current(s);need(s.mode==='nursery'&&c&&!c.approved&&!s.pending,'Эталон ещё не готов');need(s.entries.some(e=>e.cardId===c.id&&e.frames.some(f=>f.status==='ready')),'Сначала нужен готовый эталон');c.approved=true;}
function complete(s,id){const op=s.pending;need(op&&op.id===id&&op.applied,'Не найден опыт');const e=s.entries.find(e=>e.id===op.entryId);if(e.frames.every(f=>f.status==='ready'))s.pending=null;else op.status='paused';}
function cancel(s){if(s.pending){const e=s.entries.find(e=>e.id===s.pending.entryId);e?.frames.forEach(f=>{if(f.status!=='ready'){f.status='paused';f.error='Ожидание отменено';}});s.pending=null;}}
function find(s,rng=die,choose=pick){need(s.mode==='alchemy'&&!s.pending,'Сначала заверши опыт');need(s.inventory.length<5,'В инвентаре пять слотов. Освободи один.');const d=rng();need(Number.isInteger(d)&&d>=1&&d<=6,'Неверный кубик');const rarity=d===6?2:d===5?1:0;const pool=MATERIALS[rarity].filter(n=>!s.inventory.some(m=>m.name===n));need(pool.length,'Материалы этого типа уже найдены');const m={id:'M'+(++s.finds),name:choose(pool),rarity,die:d};s.inventory.push(m);return m;}
function seed(str){let h=2166136261;for(const c of str)h=Math.imul(h^c.charCodeAt(0),16777619);return h>>>1;}
function imageRequest(s,e,f){const c=s.cards.find(c=>c.id===e.cardId);return {prompt:[s.mode==='nursery'?'Original fantastic creature, field naturalist illustration':'Fantasy alchemical character, adult age '+c.age+', fully clothed',e.canon,'Distinctive mark: '+c.mark,'Single exact scene: '+f.description,'Setting: '+c.location,'Rich digital painting, realistic materials, readable anatomy, cinematic light, no lettering'].join('. '),negativePrompt:'text, watermark, logo, cropped head, blurry, nsfw, graphic injury',resolution:s.mode==='nursery'&&e.kind!=='trial'?'768x512':'512x768',seed:seed(s.id+c.id)};}
function restore(raw,mode){const s=copy(raw);need(s&&s.schema===1&&s.mode===mode&&typeof s.id==='string','Сохранение от другой игры');need(Number.isInteger(s.n)&&s.n>=0&&s.n<=10000,'Неверный счётчик');need(Array.isArray(s.cards)&&s.cards.length<=200&&Array.isArray(s.entries)&&s.entries.length<=2000,'Слишком большая коллекция');
 const ids=new Set();for(const c of s.cards){cardData(c);need(typeof c.id==='string'&&!ids.has(c.id),'Повтор карточки');ids.add(c.id);need(Array.isArray(c.materials)&&c.materials.length>=1&&c.materials.length<=4&&c.materials.every(m=>typeof m==='string'&&m.length<=500),'Неверные материалы');need(Number.isInteger(c.stage)&&c.stage>=0&&c.stage<=5&&Number.isInteger(c.wear)&&c.wear>=0&&Number.isInteger(c.tempers)&&c.tempers>=0,'Неверная стадия карточки');}
 need(s.current===null||ids.has(s.current),'Не найдена текущая карточка');const entries=new Set();for(const e of s.entries){need(typeof e.id==='string'&&!entries.has(e.id)&&ids.has(e.cardId),'Повтор или неизвестная карточка опыта');entries.add(e.id);text(e.title,120);text(e.narrative,5000);text(e.canon,1800);need(Array.isArray(e.frames)&&e.frames.length>=1&&e.frames.length<=3,'Неверные кадры');for(const f of e.frames){need(typeof f.id==='string'&&typeof f.anchor==='string'&&e.narrative.includes(f.anchor),'Неверная привязка кадра');text(f.description,2200);need(['pending','running','ready','failed','paused'].includes(f.status),'Неизвестный статус');need(f.status!=='ready'||typeof f.mediaId==='string','Нет картинки');if(f.status==='running'){f.status='failed';f.error='Запрос прерван. Повтор запускается вручную.';}}}
 need(Array.isArray(s.inventory)&&s.inventory.length<=5&&new Set(s.inventory.map(m=>m.id)).size===s.inventory.length,'Неверный инвентарь');for(const m of s.inventory){text(m.name,200);need([0,1,2].includes(m.rarity),'Неверная редкость');}
 for(const k of ['master','successes','temperBonus','finds'])need(Number.isInteger(s[k])&&s[k]>=0&&s[k]<=10000,'Неверное мастерство');need(s.master===Math.min(5,Math.floor(s.successes/3)+s.temperBonus),'Мастерство не соответствует журналу');
 if(s.pending){const op=s.pending;need(typeof op.id==='string'&&op.id==='E'+s.n,'Неверная операция');need(!op.applied||entries.has(op.entryId),'Не найден сохранённый исход');need(['create','revise','modify','trial','temper','combine'].includes(op.kind)&&[1,2,3].includes(op.frameCount),'Неизвестная операция');if(mode==='alchemy'){need(op.roll&&Number.isInteger(op.roll.die)&&op.roll.die>=1&&op.roll.die<=6,'Неверный сохранённый бросок');need(op.roll.total===op.roll.die+op.roll.master+op.roll.bonus-op.roll.anomaly-op.roll.wear&&op.roll.success===(op.roll.total>=op.roll.target),'Повреждён результат броска');need(Array.isArray(op.consumed),'Нет списка материалов');}s.pending.status='paused';}
 return s;
}
const api={BASES,TRIALS,MUTATIONS,copy,need,uid,die,create,current,begin,commit,approve,complete,cancel,find,imageRequest,restore};if(typeof module!=='undefined'&&module.exports)module.exports=api;else scope.WorkshopCore=api;
})(typeof window==='undefined'?globalThis:window);
