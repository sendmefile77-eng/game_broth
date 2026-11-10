// Offline Android fixtures use the same neutral page template as the published stubs.
const fs=require('node:fs'),path=require('node:path');
const root=__dirname,assets=path.join(root,'../app/src/androidTest/assets');
const template=fs.readFileSync(path.join(root,'template.html'),'utf8');
const parent=fs.readFileSync(path.join(assets,'alchemy-parent.html'),'utf8');
for(const entry of require('./deployment.json')){
  const name='stub-'+entry.number,slug=new URL(entry.url).pathname;
  const top=parent.replaceAll('https://5df1b204e9f4b8a13dc8abcbc1fed0e6.perchance.org',entry.origin).replaceAll('/8kwa4xkn39',slug);
  const metrics=`<script>window.addEventListener('message',e=>{if(e.origin==='https://perchance.org'&&e.data==='measure')parent.postMessage({type:'fixtureMetrics',stubName:${JSON.stringify(entry.title)},width:innerWidth,height:innerHeight,scrollWidth:document.documentElement.scrollWidth,exportBridge:typeof window.GameBrothFiles!=='undefined'},e.origin);});</script>`;
  fs.writeFileSync(path.join(assets,name+'-parent.html'),top);
  fs.writeFileSync(path.join(assets,name+'-game.html'),'<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">'+template.replaceAll('[title]',entry.title)+metrics);
}
console.log('Built two offline Perchance placeholder fixtures');
