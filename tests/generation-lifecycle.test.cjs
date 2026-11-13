/* Public UI flows with deliberately late plugins/responses; never contacts AI. */
const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),http=require('node:http'),path=require('node:path');
const {chromium}=require(process.env.FANTA_PLAYWRIGHT||'playwright');
const root=path.join(__dirname,'..');
async function setupFanta(page){await page.getByRole('button',{name:'Выбрать завязку →'}).click();await page.getByRole('button',{name:'Эта история →'}).first().click();await page.locator('#hero-age').fill('27');await page.getByRole('button',{name:'Начать приключение →'}).click();}
async function setupWorkshop(page){await page.locator('#materials').fill('Жук, янтарь');await page.getByRole('button',{name:'Собрать существо',exact:true}).click();}
for(const game of ['fanta','nursery'])for(const missing of ['generateText','generateImage'])test(game+': cancellation while waiting for '+missing+' does not submit stale requests or unlock a new operation',{timeout:30000},async()=>{
 const html=fs.readFileSync(path.join(root,game==='fanta'?'warhammer/index.html':'workshops/nursery.html'),'utf8');
 const server=http.createServer((req,res)=>{res.setHeader('Content-Type','text/html; charset=utf-8');res.end(html);});await new Promise(ok=>server.listen(0,'127.0.0.1',ok));let browser;
 try{
  browser=await chromium.launch({headless:true,args:['--no-sandbox']});const page=await browser.newPage();
  if(game==='nursery')await page.addInitScript(fs.readFileSync(path.join(root,'workshops/mock.js'),'utf8'));
  else await page.addInitScript(()=>{window.root={generateText:async()=>JSON.stringify({title:'Печать на столе',narrative:'Адель кладёт медную печать на стол у окна. Снаружи слышны осторожные шаги; ей хватает времени выбрать следующий шаг, но дверь не останется закрытой надолго.',choices:['Осмотреть печать','Закрыть дверь','Выйти'],milestone:false,threat:false,location:'Нульн',scene:'Печать на столе',wardrobe:'плащ',fact:'На столе печать',ending:'',frames:[{anchor:'Адель кладёт медную печать на стол у окна',description:'A clothed adult woman puts a copper seal on a wooden table by a window.'}]}),generateImage:async()=>({dataUrl:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='})};});
  await page.goto('http://127.0.0.1:'+server.address().port);await page.evaluate(missing=>{window.savedProvider=root[missing];delete root[missing];window.providerCalls=0;window.release=[];},missing);const setup=game==='fanta'?setupFanta:setupWorkshop;await setup(page);
  if(missing==='generateImage')await page.locator(game==='fanta'?'.image-empty':'.pending').first().waitFor();
  await page.waitForTimeout(300);await page.getByRole('button',{name:game==='fanta'&&missing==='generateImage'?'Продолжить без кадра':'Отменить ожидание',exact:true}).click();
  await page.getByRole('button',{name:'Сейвы',exact:true}).click();await page.getByRole('button',{name:game==='fanta'?'Новая история':'Новая мастерская',exact:true}).click();
  await setup(page);if(missing==='generateImage')await page.locator(game==='fanta'?'.image-empty':'.pending').first().waitFor();await page.waitForTimeout(300);
  await page.evaluate(missing=>{root[missing]=(...args)=>{providerCalls++;return new Promise(ok=>release.push(async()=>ok(await savedProvider(...args))));};},missing);
  await page.waitForFunction(()=>providerCalls>=1);await page.waitForTimeout(600);
  assert.equal(await page.evaluate(()=>providerCalls),1,'cancelled operation invoked a plugin after it became available');
  assert.equal(await page.getByRole('button',{name:'Повторить',exact:true}).count(),0,'old finally handler unlocked the newer operation with the same local ID');
  await page.evaluate(missing=>{root[missing]=savedProvider;release[0]();},missing);
  await page.waitForFunction(game=>game==='fanta'?document.querySelectorAll('article.scene img').length===1&&!document.querySelector('#action').disabled:document.querySelectorAll('article.scene img').length===1&&!document.querySelector('.pending'),game);
  assert.equal(await page.locator('article.scene').count(),1);
 }finally{await browser?.close();await new Promise(ok=>server.close(ok));}
});
