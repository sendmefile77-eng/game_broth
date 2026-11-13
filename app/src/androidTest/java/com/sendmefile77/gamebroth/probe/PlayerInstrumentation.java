package com.sendmefile77.gamebroth.probe;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real Android WebView + services. All generation in this suite uses local delayed fixtures. */
public class PlayerInstrumentation extends Instrumentation {
  static final String TOP="https://perchance.org/game-broth-veyre",FRAME="https://95f868a04fcef565bd634a26cec6e170.perchance.org/game-broth-veyre";
  static final String PNG="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
  private final JSONArray results=new JSONArray();private File out;
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  @Override public Activity newActivity(ClassLoader cl,String name,Intent intent)throws InstantiationException,IllegalAccessException,ClassNotFoundException{if(MainActivity.class.getName().equals(name))return new FixtureActivity();return super.newActivity(cl,name,intent);}
  public static class FixtureActivity extends MainActivity {
    @Override protected WebViewClient createWebViewClient(){WebViewClient base=super.createWebViewClient();return new WebViewClient(){
      @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){String url=request.getUrl().toString().split("[?#]",2)[0];if(url.equals(TOP))return html("<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>html,body,iframe{height:100%;width:100%;margin:0;border:0;background:#171b1f}</style><iframe id='outputIframeEl' src='"+FRAME+"'></iframe>");if(url.equals(FRAME))return html(fixture());return html("");}
      @Override public void onPageStarted(WebView view,String url,Bitmap icon){base.onPageStarted(view,url,icon);}
      @Override public void onPageFinished(WebView view,String url){base.onPageFinished(view,url);}
    };}
    private WebResourceResponse html(String body){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));}
    private String fixture(){return """
      <!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'>
      <style>body{background:#171b1f;color:#eee;font:17px sans-serif}button,input{font:inherit;min-height:48px}input{width:90%}img{width:180px;height:220px;image-rendering:pixelated}.scene{padding:12px}</style>
      <div id='fanta'><main id='story'></main><form id='actions'><input id='action' maxlength='1200' aria-label='Действие героини'><button>Отправить</button></form><button id='text'>Только текст</button><button id='image'>Текст и картинка</button></div>
      <script>
       window.fixture={textCalls:0,imageCalls:0,submits:0,loads:Number(sessionStorage.playerLoads||0)+1};sessionStorage.playerLoads=fixture.loads;
       const incoming=GameBrothPlayer.onmessage;GameBrothPlayer.onmessage=e=>{try{fixture.nativeToken=JSON.parse(e.data).token;}catch(_){}incoming(e);};
       window.root={generateText:()=>{fixture.textCalls++;return new Promise(ok=>setTimeout(()=>ok('Герой находит медный ключ у окна. История продолжается.'),1700));},generateImage:()=>{fixture.imageCalls++;return new Promise(ok=>setTimeout(()=>ok({dataUrl:'__PNG__'}),1700));}};
       async function generate(image){document.querySelector('#action').disabled=true;const text=await root.generateText({instruction:'fixture'});const article=document.createElement('article');article.className='scene';article.innerHTML='<h2>Сцена '+fixture.textCalls+'</h2><p>'+text+' Номер '+fixture.textCalls+'.</p>';document.querySelector('#story').prepend(article);if(image){const result=await root.generateImage({prompt:'fixture'});const img=document.createElement('img');img.src=result.dataUrl;article.append(img);}document.querySelector('#action').disabled=false;}
       document.querySelector('#text').onclick=()=>generate(false);document.querySelector('#image').onclick=()=>generate(true);document.querySelector('#actions').onsubmit=e=>{e.preventDefault();fixture.submits++;};
       addEventListener('message',async e=>{if(e.origin!=='https://perchance.org'||e.data?.type!=='player-test')return;let value;try{value=await eval(e.data.js);}catch(error){value={error:error.message};}parent.postMessage({type:'player-test-result',id:e.data.id,value},e.origin);});
      </script>
      """.replace("__PNG__",PNG);}
  }
  interface Task {void run()throws Exception;}
  private void check(String name,Task work){try{work.run();record(name,"PASS","Android fixture");}catch(Throwable e){record(name,"FAIL",e.toString());try{screenshot(name+"-failure");}catch(Exception ignored){}}}
  private void record(String name,String status,String detail){try{results.put(new JSONObject().put("name",name).put("status",status).put("detail",detail));}catch(Exception ignored){}}
  @Override public void onStart(){out=new File(getTargetContext().getExternalFilesDir(null),"player-ui");out.mkdirs();try{
    startActivitySync(new Intent(getTargetContext(),MainActivity.class).putExtra("page",TOP).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    check("player_output_handshake",()->{waitFor(()->MainActivity.current.player.connected(),15000);game("GameBrothPlayer.postMessage(JSON.stringify({type:'hello'}))");waitFor(()->Boolean.TRUE.equals(game("!!fixture.nativeToken")),5000);});
    check("player_commands_append_without_send",()->{game("document.querySelector('#action').value='Мой черновик'");runOnMainSync(()->MainActivity.current.player.insert("Осмотреть ключ"));waitFor(()->"Мой черновик Осмотреть ключ".equals(String.valueOf(game("document.querySelector('#action').value"))),5000);require(((Number)game("fixture.submits")).intValue()==0,"command submitted a turn");});
    check("player_templates_and_per_game_storage",()->{runOnMainSync(()->{try{MainActivity.current.player.commands(new JSONArray().put(new JSONObject().put("name","Осмотр").put("text","Осмотреть {предмет}")));}catch(Exception e){throw new RuntimeException(e);}});require("Осмотреть ключ".equals(PlayerPolicy.expand("Осмотреть {предмет}",Collections.singletonMap("предмет","ключ"))),"template expansion failed");});
    check("player_commands_native_ui",()->{runOnMainSync(()->MainActivity.current.player.panel.commands());waitLabel("Быстрые команды");clickContains("Осмотр");waitLabel("Осмотр");List<AccessibilityNodeInfo> nodes=new ArrayList<>();collect(getUiAutomation().getRootInActiveWindow(),nodes);AccessibilityNodeInfo input=null;for(AccessibilityNodeInfo n:nodes)if(n.isEditable()){input=n;break;}require(input!=null,"template field missing");Bundle value=new Bundle();value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"сундук");require(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,value),"cannot fill template");clickContains("Вставить");waitFor(()->String.valueOf(game("document.querySelector('#action').value")).contains("Осмотреть сундук"),5000);require(((Number)game("fixture.submits")).intValue()==0,"template submitted a turn");});
    check("player_text_survives_home",()->background(false));
    check("player_image_survives_home",()->background(true));
    check("player_archive_has_both_scenes",()->{require(scenes().size()==2,"archive did not record both scenes");});
    check("player_gallery_deduplicates_image",()->{require(pictures().size()==1,"gallery missing or duplicated image");});
    check("player_html_export_contains_images",()->{String html=io(()->{ByteArrayOutputStream out=new ByteArrayOutputStream();MainActivity.current.player.store.exportArchive(TOP,out);return out.toString("UTF-8");});require(html.contains("медный ключ")&&html.contains("data:image/png;base64,"),"HTML export lost text or images");});
    check("player_rejects_forged_epoch",()->{int before=scenes().size();game("GameBrothPlayer.postMessage(JSON.stringify({type:'scene',token:'old-token',title:'Wrong',text:'MALICIOUS SCENE DATA',images:[]}))");Thread.sleep(300);require(scenes().size()==before,"old epoch was archived");});
    check("player_foreground_service_stops_when_idle",()->{waitFor(()->!GenerationService.running,5000);});
    check("player_gallery_native_ui",()->{runOnMainSync(()->MainActivity.current.findViewById(R.id.game_menu).performClick());waitLabel("Панель APK");clickContains("Панель APK");waitLabel("Галерея APK");clickContains("Галерея APK");waitLabel("Галерея · 1");Thread.sleep(400);screenshot("gallery");closeTop();});
    check("player_archive_native_ui",()->{runOnMainSync(()->MainActivity.current.player.panel.archive());waitLabel("Архив сцен · 2");screenshot("archive");closeTop();});
    check("player_orientation_keeps_same_webview",()->{MainActivity before=MainActivity.current;WebView web=before.web;runOnMainSync(()->before.player.panel.show());clickContains("Настройки панели");clickContains("Ориентация экрана");clickContains("Горизонтально");clickContains("Готово");Thread.sleep(900);require(MainActivity.current==before&&MainActivity.current.web==web,"rotation recreated the game");require(((Number)game("fixture.loads")).intValue()==1,"rotation reloaded page");runOnMainSync(()->MainActivity.current.player.panel.archive());waitLabel("Архив сцен · 2");clickContains("Сцена 2");Thread.sleep(500);screenshot("reader-landscape");closeTop();closeTop();runOnMainSync(()->MainActivity.current.player.orientation(1));Thread.sleep(600);});
    check("player_restart_keeps_archive_and_commands",()->{restart();waitFor(()->MainActivity.current.player.connected(),15000);require(scenes().size()==2&&pictures().size()==1,"restart lost archive");AtomicReference<JSONArray> commands=new AtomicReference<>();runOnMainSync(()->commands.set(MainActivity.current.player.commands()));require("Осмотр".equals(commands.get().getJSONObject(0).getString("name")),"restart lost custom commands");});
    check("player_deleting_picture_updates_archive_only",()->{String id=pictures().get(0).id;io(()->{MainActivity.current.player.store.deleteImage(TOP,id);return null;});require(pictures().isEmpty(),"picture not removed");for(PlayerStore.Scene s:scenes())require(s.images.length()==0,"archive retained a broken image reference");require(((Number)game("fixture.textCalls")).intValue()==0,"local deletion invoked game generation");});
  }catch(Throwable e){record("harness","FAIL",e.toString());}
    try{File file=new File(out,"results.json");try(FileOutputStream stream=new FileOutputStream(file)){stream.write(new JSONObject().put("results",results).put("kind","offline Android fixture, not live Perchance").toString(2).getBytes(StandardCharsets.UTF_8));}}catch(Exception ignored){}Bundle b=new Bundle();b.putString("stream",results.toString()+"\n");finish(Activity.RESULT_OK,b);
  }
  private void background(boolean image)throws Exception{
    MainActivity owner=MainActivity.current;int complete=owner.player.completionCount;game("document.querySelector('#"+(image?"image":"text")+"').click()");waitFor(()->GenerationService.running,5000);if(image)waitFor(()->((Number)game("fixture.imageCalls")).intValue()==1,5000);
    shell("input keyevent 3");waitFor(()->owner.player.completionCount>complete,12000);
    require(MainActivity.current==owner,"Home destroyed the Activity");require(((Number)game("fixture.loads")).intValue()==1,"background reloaded page");
    if(image)require(pictures().size()==1,"image not saved while backgrounded");
    waitFor(()->{for(android.service.notification.StatusBarNotification n:owner.getSystemService(NotificationManager.class).getActiveNotifications())if(n.getId()==152)return true;return false;},3000);
    GenerationService.open(owner).send();Thread.sleep(600);require(MainActivity.current==owner,"notification opened another game instance");require(((Number)game("fixture.textCalls")).intValue()==(image?2:1),"background resubmitted text");require(((Number)game("fixture.imageCalls")).intValue()==(image?1:0),"background resubmitted image");
  }
  private <T>T io(Callable<T> work)throws Exception{return MainActivity.current.player.io.submit(work).get(8,TimeUnit.SECONDS);}
  private List<PlayerStore.Scene> scenes()throws Exception{return io(()->MainActivity.current.player.store.scenes(TOP));}
  private List<PlayerStore.Picture> pictures()throws Exception{return io(()->MainActivity.current.player.store.pictures(TOP));}
  interface Condition{boolean get()throws Exception;}
  private void waitFor(Condition condition,long ms)throws Exception{long end=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<end){if(condition.get())return;Thread.sleep(100);}throw new Exception("Timed out waiting for condition");}
  private Object eval(String code)throws Exception{CountDownLatch done=new CountDownLatch(1);AtomicReference<Object> value=new AtomicReference<>();runOnMainSync(()->MainActivity.current.web.evaluateJavascript(code,raw->{try{value.set(new JSONTokener(raw).nextValue());}catch(Exception e){value.set(raw);}done.countDown();}));require(done.await(5,TimeUnit.SECONDS),"JS callback timeout");return value.get();}
  private Object game(String code)throws Exception{long id=SystemClock.elapsedRealtime();eval("window.__playerTest=null;window.addEventListener('message',function listen(e){if(e.origin==='https://95f868a04fcef565bd634a26cec6e170.perchance.org'&&e.data?.type==='player-test-result'&&e.data.id==="+id+"){window.__playerTest=e.data;removeEventListener('message',listen);}});document.querySelector('#outputIframeEl').contentWindow.postMessage({type:'player-test',id:"+id+",js:"+JSONObject.quote(code)+"},'https://95f868a04fcef565bd634a26cec6e170.perchance.org');null");long end=SystemClock.elapsedRealtime()+5000;while(SystemClock.elapsedRealtime()<end){Object raw=eval("JSON.stringify(window.__playerTest)");if(raw instanceof String&&!"null".equals(raw))return new JSONObject((String)raw).opt("value");Thread.sleep(30);}throw new Exception("Game fixture did not reply");}
  private void restart(){runOnMainSync(()->{MainActivity old=MainActivity.current;Intent i=new Intent(old,MainActivity.class).putExtra("page",TOP).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);old.finish();old.startActivity(i);});SystemClock.sleep(800);}
  private void collect(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> nodes){if(n==null)return;nodes.add(n);for(int i=0;i<n.getChildCount();i++)collect(n.getChild(i),nodes);}
  private AccessibilityNodeInfo find(String label,boolean contains){List<AccessibilityNodeInfo> nodes=new ArrayList<>();collect(getUiAutomation().getRootInActiveWindow(),nodes);for(AccessibilityNodeInfo n:nodes)if(n.isVisibleToUser()&&n.getText()!=null&&(contains?n.getText().toString().contains(label):n.getText().toString().equals(label)))return n;return null;}
  private void waitLabel(String label)throws Exception{waitFor(()->find(label,false)!=null,8000);}
  private void clickContains(String label)throws Exception{waitFor(()->find(label,true)!=null,5000);AccessibilityNodeInfo n=find(label,true);while(n!=null&&!n.isClickable())n=n.getParent();require(n!=null&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK),"control not clickable");}
  private void closeTop()throws Exception{clickContains("Закрыть");Thread.sleep(250);}
  private void shell(String cmd)throws Exception{try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(cmd);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[1024];while(in.read(b)!=-1){}}}
  private void screenshot(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();if(b!=null)try(FileOutputStream f=new FileOutputStream(new File(out,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,f);}}
  private static void require(boolean condition,String detail)throws Exception{if(!condition)throw new Exception(detail);}
}
