const fs=require('node:fs'),path=require('node:path');
const root=__dirname,css=fs.readFileSync(path.join(root,'src/styles.css'),'utf8'),scripts=['core.js','prompts.js','app.js'].map(x=>fs.readFileSync(path.join(root,'src',x),'utf8')).join('\n');
for(const [mode,title]of [['nursery','Питомник'],['alchemy','Алхимия образов']]){
 const html=`<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover,interactive-widget=resizes-content"><style>${css}</style><div id="workshop"><p>Открываем мастерскую…</p></div><script>window.WORKSHOP_MODE=${JSON.stringify(mode)};document.documentElement.dataset.workshop=${JSON.stringify(mode)};\n${scripts.replace(/<\/script/gi,'<\\/script')}</script>`;
 fs.writeFileSync(path.join(root,mode+'.html'),html);fs.writeFileSync(path.join(root,mode+'.pjs'),`title\n  ${title} — Старый Свет\n\ngenerateText = {import:ai-text-plugin}\ngenerateImage = {import:text-to-image-plugin}\n\noutput\n  ${title}: иллюстрированная мастерская Старого Света.\n`);
 if(process.argv.includes('--android-fixture')){
  const mock=fs.readFileSync(path.join(root,'mock.js'),'utf8')+'\n'+fs.readFileSync(path.join(root,'android-metrics.js'),'utf8');fs.writeFileSync(path.join(root,'../app/src/androidTest/assets',mode+'-game.html'),html.replace('<script>','<script>'+mock+'</script><script>'));
 }
 console.log(mode,Buffer.byteLength(html),'bytes');
}
