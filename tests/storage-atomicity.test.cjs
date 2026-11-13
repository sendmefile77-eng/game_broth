const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),http=require('node:http'),path=require('node:path');
const {chromium}=require(process.env.FANTA_PLAYWRIGHT||'playwright');
for(const game of ['fanta','nursery'])test(game+': failed current-pointer write rolls back the preceding save record',{timeout:15000},async()=>{
 const html=fs.readFileSync(path.join(__dirname,'..',game==='fanta'?'warhammer/index.html':'workshops/nursery.html'),'utf8');const server=http.createServer((req,res)=>{res.setHeader('Content-Type','text/html; charset=utf-8');res.end(html);});await new Promise(ok=>server.listen(0,'127.0.0.1',ok));let browser;
 try{browser=await chromium.launch({headless:true,args:['--no-sandbox']});const page=await browser.newPage();await page.goto('http://127.0.0.1:'+server.address().port);await page.locator(game==='fanta'?'#dice':'#materials').waitFor();
  await page.evaluate(()=>{const put=IDBObjectStore.prototype.put;IDBObjectStore.prototype.put=function(value,key){if(this.name==='meta'&&key==='current')throw new DOMException('fixture storage failure','QuotaExceededError');return put.call(this,value,key);};});
  if(game==='fanta')await page.getByRole('button',{name:'Выбрать завязку →'}).click();else{await page.locator('#materials').fill('Жук, янтарь');await page.getByRole('button',{name:'Собрать существо',exact:true}).click();}
  await page.getByText('fixture storage failure',{exact:true}).waitFor();await page.reload();await page.locator(game==='fanta'?'#dice':'#materials').waitFor();
  if(game==='fanta'){assert.equal(await page.locator('#setup-style').count(),1);assert.equal(await page.locator('[data-action=scenario]').count(),0);}else{assert.equal(await page.locator('.pending').count(),0);assert.equal(await page.locator('article.scene').count(),0);assert.equal(await page.getByRole('button',{name:'Собрать существо',exact:true}).isEnabled(),true);}
 }finally{await browser?.close();await new Promise(ok=>server.close(ok));}
});
