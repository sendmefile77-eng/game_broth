/* Offline UI integration fixture. This does NOT test live Perchance generation. */
const {test}=require('node:test'),assert=require('node:assert/strict'),http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const {chromium}=require(process.env.FANTA_PLAYWRIGHT||'playwright');
test('three-step setup, manual roll, failed image retry, save roundtrip and 320/390 layouts',{timeout:120000},async()=>{
  const source=fs.readFileSync(path.join(__dirname,'index.html'),'utf8');
  const server=http.createServer((req,res)=>{res.setHeader('Content-Type','text/html; charset=utf-8');res.end(source);});await new Promise(ok=>server.listen(0,'127.0.0.1',ok));
  const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
  try{
    const page=await browser.newPage({viewport:{width:390,height:780}});await page.addInitScript(()=>{
      window.fixture={textCalls:0,imageCalls:0,failImage:false};
      window.root={generateText:async({instruction})=>{
        fixture.textCalls++;
        if(instruction.includes('Classify the next intention'))return JSON.stringify({intent:'Осмотреть медную печать',risky:true,stakes:'Стража может войти прежде, чем будет прочитан знак.'});
        return JSON.stringify({title:'Печать на столе',narrative:'Адель кладёт медную печать на стол у окна. Снаружи слышны осторожные шаги, а на обороте конверта проступает след незнакомого знака. Ей хватает времени выбрать следующий шаг, но дверь не останется закрытой надолго.',choices:['Осмотреть медную печать','Поговорить через дверь','Спрятать письмо'],milestone:true,threat:false,location:'Нульн, комната у канала',scene:'Медная печать на столе',wardrobe:'зелёный плащ',fact:'На обороте письма есть знак',ending:'',frames:[{anchor:'Адель кладёт медную печать на стол у окна',description:'A clothed adult woman puts a copper seal on a wooden table by a window, green cloak, warm lamplight.'}]});
      },generateImage:async()=>{
        fixture.imageCalls++;if(fixture.failImage)throw Error('offline image test failure');
        return {dataUrl:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='};
      }};
    });
    await page.goto('http://127.0.0.1:'+server.address().port);
    await page.locator('#dice').selectOption('manual');await page.getByRole('button',{name:'Выбрать завязку →'}).click();
    await page.getByRole('button',{name:'Эта история →'}).first().waitFor();assert.equal(await page.getByRole('button',{name:'Эта история →'}).count(),4);
    await page.getByRole('button',{name:'Эта история →'}).first().click();
    await page.locator('#hero-age').fill('17');await page.getByRole('button',{name:'Начать приключение →'}).click();assert.equal(await page.locator('#setup-hero').count(),1);
    await page.locator('#hero-age').fill('27');await page.getByRole('button',{name:'Начать приключение →'}).click();
    await page.getByRole('button',{name:'Осмотреть медную печать',exact:true}).waitFor();
    assert.match(await page.locator('.head-right').innerText(),/Ход 0/);
    await page.evaluate(()=>fixture.failImage=true);
    await page.getByRole('button',{name:'Осмотреть медную печать',exact:true}).click();
    await page.locator('#manual-dice').waitFor();assert.match(await page.locator('.head-right').innerText(),/Ход 0/);
    await page.getByLabel('Первый кубик').fill('6');await page.getByLabel('Второй кубик').fill('6');await page.getByRole('button',{name:'Принять бросок'}).click();
    await page.getByRole('button',{name:'Повторить',exact:true}).waitFor();assert.match(await page.locator('.head-right').innerText(),/Ход 1/);
    await page.evaluate(()=>fixture.failImage=false);await page.getByRole('button',{name:'Повторить',exact:true}).click();
    await page.getByRole('button',{name:'Осмотреть медную печать',exact:true}).waitFor();assert.match(await page.locator('.head-right').innerText(),/Ход 1/);
    assert.equal(await page.locator('article.scene').count(),2);assert.match(await page.locator('.dice').innerText(),/6 \+ 6 = 12/);
    for(const width of [390,320]){
      await page.setViewportSize({width,height:780});
      const geometry=await page.evaluate(()=>{const m=document.querySelector('main').getBoundingClientRect(),f=document.querySelector('.foot').getBoundingClientRect();return {width:document.documentElement.clientWidth,scroll:document.documentElement.scrollWidth,main:m.bottom,footer:f.top,footerBottom:f.bottom,height:innerHeight};});
      assert.equal(geometry.width,geometry.scroll);assert.ok(Math.abs(geometry.main-geometry.footer)<=1);assert.ok(geometry.footerBottom<=geometry.height+1);
      fs.mkdirSync(path.join(__dirname,'test-results'),{recursive:true});await page.screenshot({path:path.join(__dirname,'test-results','fanta-'+width+'.png')});
    }
    await page.getByRole('button',{name:'Сейвы',exact:true}).click();
    const download=page.waitForEvent('download');await page.getByRole('button',{name:'Скачать сохранение'}).click();const file=await download;const saved=JSON.parse(fs.readFileSync(await file.path(),'utf8'));
    assert.equal(saved.state.t,1);assert.equal(saved.state.journal.length,2);assert.equal(saved.state.journal[1].roll.total,12);assert.equal(Object.keys(saved.media).length,2);
    await page.locator('#paste-save').fill(JSON.stringify(saved));await page.getByRole('button',{name:'Импортировать JSON'}).click();
    await page.getByText('История загружена. Незавершённые операции ждут твоего решения.',{exact:true}).waitFor();assert.match(await page.locator('.head-right').innerText(),/Ход 1/);
    await page.reload();await page.getByRole('button',{name:'Осмотреть медную печать',exact:true}).waitFor();assert.equal(await page.locator('article.scene').count(),2);
    await page.getByRole('button',{name:'Сейвы',exact:true}).click();await page.waitForFunction(()=>document.querySelectorAll('#campaigns button').length===2);assert.equal(await page.locator('#campaigns button').count(),2);
    // Hold a real FileReader while switching campaigns: the export must keep
    // the original state and its matching media, not mix two campaigns.
    await page.evaluate(()=>{const read=FileReader.prototype.readAsDataURL;let first=true;FileReader.prototype.readAsDataURL=function(blob){if(first){first=false;window.resumeExport=()=>read.call(this,blob);}else read.call(this,blob);};});
    const snapshotDownload=page.waitForEvent('download');await page.getByRole('button',{name:'Скачать сохранение'}).click();await page.waitForFunction(()=>typeof resumeExport==='function');
    await page.getByRole('button',{name:'Новая история',exact:true}).click();await page.locator('#dice').waitFor();await page.evaluate(()=>resumeExport());
    const snapshotFile=await snapshotDownload,snapshot=JSON.parse(fs.readFileSync(await snapshotFile.path(),'utf8'));assert.equal(snapshot.state.t,1);assert.equal(snapshot.state.journal.length,2);assert.equal(Object.keys(snapshot.media).length,2);assert.notEqual(snapshot.state.phase,'setup');
    fs.writeFileSync(path.join(__dirname,'test-results','summary.json'),JSON.stringify({type:'offline UI fixture, not live AI',widths:[390,320],turn:1,images:2,manualDice:[6,6],campaigns:2},null,2));
  }finally{await browser.close();await new Promise(ok=>server.close(ok));}
});
