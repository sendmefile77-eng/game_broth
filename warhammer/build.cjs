const fs=require('node:fs'),path=require('node:path');
const root=__dirname,css=fs.readFileSync(path.join(root,'src/styles.css'),'utf8'),scripts=['core.js','prompts.js','app.js'].map(x=>fs.readFileSync(path.join(root,'src',x),'utf8')).join('\n');
const html='<!-- FANTA Old World 0.1; source files are in warhammer/src -->\n<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover,interactive-widget=resizes-content">\n<style>'+css+'</style>\n<div id="fanta"><p style="padding:20px">Открываем Старый Свет…</p></div>\n<script>'+scripts.replace(/<\/script/gi,'<\\/script')+'</script>\n';
fs.writeFileSync(path.join(root,'index.html'),html);console.log('Built warhammer/index.html:',Buffer.byteLength(html),'bytes');
