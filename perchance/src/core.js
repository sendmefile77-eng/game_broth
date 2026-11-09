/* Original non-graphic fantasy management engine. No AI output mutates canon. */
(function (scope) {
  'use strict';
  const clamp = n => Math.max(0, Math.min(100, n));
  const clone = x => JSON.parse(JSON.stringify(x));
  const places = [
    {id:'market',name:'Гоблинский рынок',desc:'Медные фонари, пряности и звон торговых колокольчиков.',race:'гоблин',names:['Мира Медная','Тильда Искра','Лисса Шелест']},
    {id:'baths',name:'Купальни наг',desc:'Пар над бирюзовой водой; в галереях беседуют взрослые наги.',race:'нага',names:['Сайра Тихая Вода','Нимэ Лазурная','Эсса Янтарь']},
    {id:'halls',name:'Дворфийские залы',desc:'Тёплый свет, каменные своды и музыка медных струн.',race:'дворф',names:['Бринна Уголь','Хельга Медь','Дагна Кремень']}
  ];
  const looks = ['тёмные волосы, янтарные глаза, бордовый жилет','серебристые волосы, зелёные глаза, тёмно-синий камзол','рыжие волосы, карие глаза, медная брошь'];
  function note(s, title, text, kind='event') {
    const e={id:'S'+String(s.nextIds.scene++).padStart(4,'0'),day:s.day,title,text,kind};
    s.journal.push(e); s.journal=s.journal.slice(-100); return e;
  }
  function batch(s, placeId) {
    const p=places.find(p=>p.id===placeId); if(!p) throw Error('Выберите место встречи.');
    const serial=s.nextIds.batch++;
    s.batch={id:'B'+serial,placeId,candidates:p.names.map((name,i)=>({
      id:'C'+String(s.nextIds.candidate++).padStart(4,'0'),name:serial>1?name+' '+serial:name,
      age:24+i*7,race:p.race,look:looks[i],fee:8+i*3,communication:3-i,
      talent:['общение и приём гостей','порядок и организация','музыка и гостеприимство'][i],
      personality:['спокойная, наблюдательная, с сухим юмором','решительная, аккуратная, любит ясные договорённости','общительная, любопытная, ценит честность'][i],hired:false
    }))};
  }
  function newGame(id) {
    const s={schema:'broth/1',id:id||'game-'+Date.now(),rev:0,day:0,phase:'SETUP',treasury:24,debt:0,reputation:0,
      rooms:1,rent:3,closedDay:-1,workers:[],batch:null,projects:[],nextIds:{candidate:1,worker:1,batch:1,scene:1},
      actionIds:[],journal:[],lastReport:null,mediaSeq:0};
    note(s,'Ключ от старого дома','Ты получаешь ключ от небольшого дома развлечений в городе Вейр. В кассе 24 галлеона, наверху одна гостевая комната, внизу — пустая приёмная. Для начала выбери место, где встретишь первых кандидатов. Все персонажи — взрослые.');
    return s;
  }
  function demand(s){return 1+Math.floor(s.reputation/25);}
  function gain(w,key,n){
    const lv=key==='career'?'level':'communication',xp=key==='career'?'xp':'communicationXp';
    w[xp]+=n; while(w[lv]<15 && w[xp]>=(key==='career'?8+4*w[lv]:4+2*w[lv])){w[xp]-=key==='career'?8+4*w[lv]:4+2*w[lv];w[lv]++;}
    if(w[lv]===15)w[xp]=0;
  }
  function apply(state,cmd,actionId){
    if(typeof actionId!=='string'||!actionId)throw Error('Не задан номер действия.');
    if(state.actionIds.includes(actionId))return {state,event:null,changed:false};
    const s=clone(state);let e;
    const open=()=>{if(s.phase!=='ORDERS')throw Error('Это действие доступно в течение открытого дня.');};
    switch(cmd.type){
      case 'visit':
        if(s.phase!=='SETUP')throw Error('Для нового набора используй «Новый поиск».');
        batch(s,cmd.place);s.day=1;s.phase='ORDERS';
        e=note(s,'Первая встреча',places.find(p=>p.id===cmd.place).desc+' Тебя ждут три кандидата. Набор останется прежним, пока ты не начнёшь новый поиск.');break;
      case 'search':
        open();if(s.treasury<1)throw Error('На поиск нужен 1 галлеон.');
        batch(s,cmd.place);s.treasury-=1;e=note(s,'Новый набор','Поиск оплачен: 1 галлеон. В списке три новых кандидата.');break;
      case 'hire': {
        open();const c=s.batch?.candidates.find(c=>c.id===cmd.id);
        if(!c||c.hired)throw Error('Этот кандидат уже нанят или больше недоступен.');
        if(s.treasury<c.fee)throw Error('Недостаточно денег на договор.');
        s.treasury-=c.fee;c.hired=true;
        const w={...clone(c),id:'W'+String(s.nextIds.worker++).padStart(4,'0'),candidateId:c.id,health:100,morale:60,fatigue:0,trust:40,level:1,xp:0,communicationXp:0,assignment:'WORK',lastSettledDay:s.day-1};
        delete w.hired;s.workers.push(w);e=note(s,'Новый договор',c.name+' присоединяется к заведению. Выплачено '+c.fee+' галлеонов. На сегодня назначена работа.');break;
      }
      case 'assign': {
        open();const w=s.workers.find(w=>w.id===cmd.id);if(!w)throw Error('Сотрудник не найден.');
        if(!['WORK','REST','ABSENT'].includes(cmd.assignment))throw Error('Неизвестное назначение.');
        if(w.assignment===cmd.assignment)return {state,event:null,changed:false};
        w.assignment=cmd.assignment;e=note(s,'План смены',w.name+': '+({WORK:'работа',REST:'отдых',ABSENT:'отсутствует'}[w.assignment])+'.');break;
      }
      case 'build':
        open();if(s.rooms+s.projects.length>=3)throw Error('В доме помещается не больше трёх комнат.');
        if(s.treasury<30)throw Error('На новую комнату нужно 30 галлеонов.');
        s.treasury-=30;s.projects.push({due:s.day+2});e=note(s,'Строительство начато','Потрачено 30 галлеонов. Комната откроется утром дня '+(s.day+2)+'. Аренда вырастет на 2 галлеона.');break;
      case 'repay':{
        open();const n=Math.min(s.debt,s.treasury);if(n<1)throw Error('Нет долга или свободных денег.');s.treasury-=n;s.debt-=n;e=note(s,'Возврат долга','Погашено '+n+' галлеонов.');break;
      }
      case 'settle':{
        if(s.day<=s.closedDay||s.phase==='CLOSED')return {state,event:null,changed:false};
        open();const opening=s.treasury;
        const capacities=s.workers.map(w=>({w,rest:w.assignment==='REST'||(w.assignment==='WORK'&&w.fatigue>=80),cap:w.assignment==='WORK'&&w.health>=50&&w.fatigue<80?(w.fatigue<60?2:1):0,n:0}));
        capacities.sort((a,b)=>a.w.fatigue-b.w.fatigue||b.w.communication-a.w.communication||a.w.id.localeCompare(b.w.id));
        let left=Math.min(demand(s),s.rooms*2,capacities.reduce((n,x)=>n+x.cap,0));const served=left;
        for(const x of capacities){x.n=Math.min(x.cap,left);left-=x.n;}
        let shares=0;const rows=[];
        for(const x of capacities){
          const w=x.w;const gross=x.n*8,share=Math.floor(gross*25/100);shares+=share;
          if(x.rest){w.fatigue=clamp(w.fatigue-25);w.health=clamp(w.health+5);}else if(w.assignment==='WORK'){w.fatigue=clamp(w.fatigue+12*x.n);}
          gain(w,'career',x.n);gain(w,'communication',x.n);w.lastSettledDay=s.day;
          rows.push({id:w.id,name:w.name,served:x.n,gross,share,rest:x.rest});
        }
        const gross=served*8,food=s.workers.length,cost=s.rent+food,balance=opening+gross-shares-cost,newDebt=Math.max(0,-balance);
        s.treasury=Math.max(0,balance);s.debt+=newDebt;if(served)s.reputation=clamp(s.reputation+1);
        s.closedDay=s.day;s.phase='CLOSED';s.lastReport={day:s.day,opening,gross,shares,rent:s.rent,food,cost,newDebt,closing:s.treasury,served,rows};
        e=note(s,'День '+s.day+' закрыт','Принято гостей: '+served+'. Выручка '+gross+', выплаты '+shares+', аренда '+s.rent+', питание '+food+'. В кассе '+s.treasury+' галлеонов.'+(newDebt?' Новый долг: '+newDebt+'.':''),'report');break;
      }
      case 'morning':{
        if(s.phase!=='CLOSED')throw Error('Сначала закрой текущий день.');
        s.day++;s.phase='ORDERS';const ready=s.projects.filter(p=>p.due<=s.day).length;
        s.rooms+=ready;s.rent+=2*ready;s.projects=s.projects.filter(p=>p.due>s.day);
        e=note(s,'Утро дня '+s.day,'Новый день начинается. Назначения сотрудников сохранены.'+(ready?' Строительство завершено: новых комнат '+ready+'.':''));break;
      }
      case 'talk':
        if(s.phase==='SETUP')throw Error('Сначала выбери место первой встречи.');
        e=note(s,'Твоё действие',String(cmd.text||'').trim().slice(0,700),'dialogue');if(!e.text)throw Error('Напиши действие.');break;
      default:throw Error('Неизвестное действие.');
    }
    if(e){e.actionType=cmd.type;if(cmd.place)e.placeId=cmd.place;if(cmd.id)e.characterId=cmd.id;}
    s.rev++;s.actionIds.push(actionId);s.actionIds=s.actionIds.slice(-200);validate(s);return {state:s,event:e,changed:true};
  }
  function validate(s){
    const num=(n,min=0,max=1e9)=>Number.isSafeInteger(n)&&n>=min&&n<=max;
    if(!s||s.schema!=='broth/1'||typeof s.id!=='string'||s.id.length>100)throw Error('Это не сохранение игры broth/1.');
    for(const k of ['rev','day','treasury','debt','mediaSeq'])if(!num(s[k]))throw Error('Повреждено поле '+k+'.');
    if(!num(s.reputation,0,100)||!num(s.rooms,1,3)||s.rent!==3+(s.rooms-1)*2||!num(s.closedDay,-1,s.day)||!['SETUP','ORDERS','CLOSED'].includes(s.phase))throw Error('Повреждён календарь или состояние дома.');
    if((s.phase==='SETUP')!==(s.day===0)||(s.phase==='CLOSED'&&s.closedDay!==s.day)||(s.phase==='ORDERS'&&s.closedDay>=s.day))throw Error('Несогласованный день.');
    if(!Array.isArray(s.workers)||s.workers.length>100||!Array.isArray(s.projects)||s.projects.length+s.rooms>3||!Array.isArray(s.journal)||s.journal.length>100||!Array.isArray(s.actionIds)||s.actionIds.length>200)throw Error('Повреждены списки сохранения.');
    if(!s.nextIds||!['worker','candidate','batch','scene'].every(k=>num(s.nextIds[k],1)))throw Error('Повреждены счётчики.');
    const ids=new Set();
    for(const w of s.workers){
      if(typeof w.id!=='string'||ids.has(w.id)||typeof w.name!=='string'||w.name.length>120||!num(w.age,21,500)||!['WORK','REST','ABSENT'].includes(w.assignment))throw Error('Повреждена карточка сотрудника.');
      ids.add(w.id);for(const k of ['health','morale','fatigue','trust'])if(!num(w[k],0,100))throw Error('Повреждены показатели сотрудника.');
      for(const k of ['level','communication'])if(!num(w[k],1,15))throw Error('Повреждён уровень.');
      for(const k of ['xp','communicationXp','lastSettledDay'])if(!num(w[k]))throw Error('Повреждён опыт сотрудника.');
    }
    for(const p of s.projects)if(!num(p.due,s.day+1))throw Error('Повреждён проект.');
    const mediaRef=id=>typeof id==='string'&&id.length<200&&/^[A-Za-z0-9_:.-]+$/.test(id);
    for(const e of s.journal){if(typeof e.id!=='string'||typeof e.text!=='string'||e.text.length>20000||typeof e.title!=='string'||!num(e.day,0,s.day))throw Error('Повреждён журнал.');if(e.mediaId&&!mediaRef(e.mediaId))throw Error('Повреждена ссылка на иллюстрацию.');}
    if(s.portraits&&(typeof s.portraits!=='object'||Array.isArray(s.portraits)||Object.keys(s.portraits).length>400||!Object.entries(s.portraits).every(([k,v])=>/^[CW][0-9]+$/.test(k)&&mediaRef(v))))throw Error('Повреждены портреты.');
    if(s.batch&&(!places.some(p=>p.id===s.batch.placeId)||!Array.isArray(s.batch.candidates)||s.batch.candidates.length!==3||!s.batch.candidates.every(c=>typeof c.id==='string'&&typeof c.name==='string'&&num(c.fee,8,14)&&num(c.age,21,500)&&num(c.communication,1,3)&&typeof c.hired==='boolean')))throw Error('Повреждён набор кандидатов.');
    if(s.lastReport){const r=s.lastReport;for(const k of ['day','opening','gross','shares','rent','food','cost','newDebt','closing','served'])if(!num(r[k]))throw Error('Повреждён отчёт.');if(!Array.isArray(r.rows)||r.rows.length>100||!r.rows.every(w=>typeof w.name==='string'&&num(w.served)&&num(w.gross)&&num(w.share)))throw Error('Повреждены строки отчёта.');if(r.closing-r.newDebt!==r.opening+r.gross-r.shares-r.cost)throw Error('Не сходится баланс отчёта.');}
    if(!s.actionIds.every(x=>typeof x==='string'))throw Error('Повреждены номера действий.');return true;
  }
  const api={places,newGame,apply,validate,demand};if(typeof module!=='undefined'&&module.exports)module.exports=api;else scope.RFCore=api;
})(typeof window!=='undefined'?window:this);
