(function(){
  'use strict';
  if(location.origin!=='https://perchance.org'||window.top!==window)return;
  let last='',queued=false;
  function report(){
    queued=false;
    // Perchance's duplicate command leaves the editor on the original page.
    // Its success result contains the actual new URL in this specific element.
    const link=document.querySelector('[data-ref="duplicateGeneratorStatus"] a[href]');
    if(!link)return;
    try{
      const url=new URL(link.getAttribute('href'),location.href);
      if(url.origin!=='https://perchance.org'||url.href===last)return;
      if(window.GameBrothCreation){window.GameBrothCreation.postMessage(JSON.stringify({url:url.href}));last=url.href;}
    }catch(_){}
  }
  const observer=new MutationObserver(()=>{if(!queued){queued=true;requestAnimationFrame(report);}});
  observer.observe(document,{childList:true,subtree:true,attributes:true,attributeFilter:['href']});
  document.addEventListener('DOMContentLoaded',report,{once:true});report();
})();
