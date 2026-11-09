package com.sendmefile77.gamebroth.probe;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Real Android UI and origin-scoped bridges; all page requests use local fixtures. */
public class CompactUiInstrumentation extends ProbeInstrumentation {
  private static CompactUiInstrumentation instance;
  private File out;
  private boolean live;
  private int launcherAnrs=0;
  private final JSONArray results=new JSONArray();
  @Override public void onCreate(Bundle arguments){instance=this;live=arguments!=null&&"true".equals(arguments.getString("networkProbe"));super.onCreate(arguments);}
  @Override public Activity newActivity(ClassLoader cl,String name,Intent intent)
      throws InstantiationException,IllegalAccessException,ClassNotFoundException {
    if(!live&&MainActivity.class.getName().equals(name))return new FixtureActivity();
    return super.newActivity(cl,name,intent);
  }
  public static class FixtureActivity extends MainActivity {
    public volatile Intent lastExport;
    @Override public void startActivityForResult(Intent intent,int requestCode){
      if(Intent.ACTION_CREATE_DOCUMENT.equals(intent.getAction())){lastExport=new Intent(intent);super.onActivityResult(requestCode,Activity.RESULT_CANCELED,null);return;}
      super.startActivityForResult(intent,requestCode);
    }
    @Override protected WebViewClient createWebViewClient(){
      WebViewClient base=super.createWebViewClient();
      return new WebViewClient(){
        @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
          String url=request.getUrl().toString();
          try{
            if(url.equals(BROTH))return page("compact-parent.html");
            if(url.equals(ART_ORIGIN+"/game-broth-veyre"))return page("compact-game.html");
            if(url.equals(FANTA))return page("fanta-parent.html");
            if(url.equals(FANTA_ORIGIN+"/n89crmz16e"))return page("fanta-game.html");
            if(url.equals(GAME))return response("<html><body><div id='menuBarEl'>Perchance menu</div><h1>MNO Engine fixture</h1></body></html>");
          }catch(Exception e){return response("Fixture failed: "+e);}
          return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
        }
        private WebResourceResponse page(String name)throws Exception{return new WebResourceResponse("text/html","UTF-8",instance.getContext().getAssets().open(name));}
        private WebResourceResponse response(String html){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));}
        @Override public void onPageStarted(WebView view,String url,Bitmap icon){base.onPageStarted(view,url,icon);}
        @Override public void onPageFinished(WebView view,String url){base.onPageFinished(view,url);}
      };
    }
  }
  @Override public void onStart(){
    if(live){super.onStart();return;}
    out=new File(getTargetContext().getExternalFilesDir(null),"compact-ui");out.mkdirs();
    try{
      launch();
      check("compact_layout",()->{
        JSONObject m=metrics();geometry(m);require("none".equals(m.getString("header")),"duplicate game header visible");
        require("none".equals(m.getString("tools")),"generation panel expanded by default");
        require(m.getJSONObject("footer").getDouble("h")<=135,"composer and version strip are too tall");
        require(Boolean.TRUE.equals(eval("getComputedStyle(document.getElementById('menuBarEl')).display!=='none'")),"Perchance login and navigation hidden");
        require(m.getString("version").contains("APK 0.6")&&m.getString("version").contains("Картинки 0.7"),"APK or generator version missing from footer");
        require(Boolean.TRUE.equals(eval("document.querySelector('.fixture-ad').getBoundingClientRect().height===50")),"banner region changed");
        AtomicReference<String> hud=new AtomicReference<>();runOnMainSync(()->hud.set(((TextView)MainActivity.current.findViewById(0x6005)).getText().toString()));
        require("День 1".equals(hud.get()),"day did not reach native toolbar");capture("compact");
      });
      check("tools_and_back",()->{
        choose("Панель ИИ и сохранения");require(!"none".equals(metrics().getString("tools")),"tools menu did not expand footer");capture("tools");
        runOnMainSync(()->MainActivity.current.onBackPressed());require("none".equals(metrics().getString("tools")),"Back did not collapse tools");
        eval("document.getElementById('outputIframeEl').contentWindow.postMessage('open-dialog','"+MainActivity.ART_ORIGIN+"')");
        require(metrics().getBoolean("dialog"),"dialog did not open");
        runOnMainSync(()->MainActivity.current.onBackPressed());require(!metrics().getBoolean("dialog"),"Back left viewer open");
        require(metrics().getInt("actions")==0,"presentation changed game actions");
      });
      check("ordinary_and_text_size",()->{
        choose("Обычный вид");require(!"none".equals(metrics().getString("header")),"ordinary view did not restore header");
        choose("Компактный вид");
        for(int n=0;n<5;n++)choose("Увеличить текст");geometry(metrics());capture("large-text");
        for(int n=0;n<5;n++)choose("Уменьшить текст");
      });
      check("keyboard_and_draft",()->{
        AccessibilityNodeInfo input=find("Твоё действие",true);Bundle value=new Bundle();value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"Тестовая заметка");
        require(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,value),"cannot type into composer");
        require(input.performAction(AccessibilityNodeInfo.ACTION_FOCUS),"cannot focus composer");input.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        shell("input text test");SystemClock.sleep(1000);
        AtomicReference<Boolean> keyboard=new AtomicReference<>(false);
        runOnMainSync(()->{android.view.WindowInsets insets=MainActivity.current.getWindow().getDecorView().getRootWindowInsets();keyboard.set(insets!=null&&insets.isVisible(android.view.WindowInsets.Type.ime()));});
        require(keyboard.get(),"keyboard did not open for inset test");
        JSONObject m=metrics();geometry(m);capture("keyboard");
        require(m.getJSONObject("input").getDouble("b")<=m.getDouble("height")+1,"keyboard covers input");
        shell("input keyevent 4");SystemClock.sleep(500);
        require(metrics().getString("command").contains("Тестовая заметка"),"draft lost after keyboard dismissal");
      });
      check("reload_and_game_selection",()->{
        String draft=metrics().getString("command");runOnMainSync(()->MainActivity.current.web.reload());waitFixture();
        require(draft.equals(metrics().getString("command")),"reload lost draft");
        choose("Выбрать игру");click("MNO Engine");waitText("MNO Engine fixture");
        require(Boolean.TRUE.equals(eval("!document.documentElement.classList.contains('gb-apk-game')")),"Broth layout applied to MNO");
        choose("Выбрать игру");click("Ржавый Фаллос");waitFixture();geometry(metrics());
        require(draft.equals(metrics().getString("command")),"switching games lost Broth draft");
        require(metrics().getInt("actions")==0,"UI test submitted a game action");
      });
      check("fanta_third_game_and_export",()->{
        choose("Выбрать игру");click("FANTA · Старый Свет");
        waitText("Выбрать завязку →");click("Выбрать завязку →");click("Эта история →");
        eval("document.getElementById('outputIframeEl').contentWindow.postMessage('scroll-start-button','"+MainActivity.FANTA_ORIGIN+"')");
        click("Начать приключение →");waitText("Осмотреть печать");
        JSONObject m=metrics();fantaGeometry(m);
        require(m.getString("version").contains("APK 0.6")&&m.getString("version").contains("FANTA 0.1"),"FANTA footer versions missing");
        require("Ход 0".equals(m.getString("turnLabel")),"opening consumed a turn");
        require(Boolean.TRUE.equals(eval("typeof window.GameBrothFiles==='undefined'")),"export bridge exposed in top-level shell");
        require(Boolean.TRUE.equals(eval("getComputedStyle(document.getElementById('menuBarEl')).display!=='none'")),"Perchance navigation hidden for FANTA");
        click("Галерея");click("Открыть крупно");require(metrics().getBoolean("dialog"),"FANTA viewer did not open");
        runOnMainSync(()->MainActivity.current.onBackPressed());require(!metrics().getBoolean("dialog"),"Back did not close FANTA viewer");
        runOnMainSync(()->((FixtureActivity)MainActivity.current).lastExport=null);
        eval("document.getElementById('outputIframeEl').contentWindow.postMessage('bad-export','"+MainActivity.FANTA_ORIGIN+"')");SystemClock.sleep(250);
        require(((FixtureActivity)MainActivity.current).lastExport==null,"FANTA origin could export Broth format");
        click("Сейвы");click("Скачать сохранение");
        long end=SystemClock.elapsedRealtime()+5000;while(((FixtureActivity)MainActivity.current).lastExport==null&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(50);
        Intent export=((FixtureActivity)MainActivity.current).lastExport;
        require(export!=null&&"application/json".equals(export.getType())&&"fanta-whfb-turn-0.json".equals(export.getStringExtra(Intent.EXTRA_TITLE)),"FANTA did not reach native JSON save picker");
        capture("fanta-saves");
        runOnMainSync(()->{MainActivity old=MainActivity.current;Intent intent=new Intent(old,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);old.finish();old.startActivity(intent);});
        SystemClock.sleep(1000);waitText("Осмотреть печать");
        AtomicReference<String> currentPage=new AtomicReference<>();runOnMainSync(()->currentPage.set(MainActivity.current.web.getUrl()));
        require(MainActivity.FANTA.equals(currentPage.get()),"restart did not retain third game selection");
        m=metrics();fantaGeometry(m);require(m.getInt("imageCalls")==0&&m.getInt("textCalls")==0,"restart regenerated restored FANTA scene");capture("fanta-restored");
      });
    }catch(Throwable e){recordUi("harness","FAIL",e.toString());}
    try{writeUi("results.json",new JSONObject().put("results",results).put("sdk",android.os.Build.VERSION.SDK_INT).put("dismissedPixelLauncherAnrs",launcherAnrs).toString(2));}catch(Exception ignored){}
    Bundle result=new Bundle();result.putString("stream",results.toString()+"\n");finish(Activity.RESULT_OK,result);
  }
  private void launch()throws Exception{
    Intent intent=new Intent(getTargetContext(),MainActivity.class).putExtra("page",MainActivity.BROTH).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivitySync(intent);waitFixture();
  }
  private void waitFixture()throws Exception{
    long end=SystemClock.elapsedRealtime()+30000;
    while(SystemClock.elapsedRealtime()<end){try{JSONObject m=metrics();if("none".equals(m.optString("header")))return;}catch(Exception ignored){}SystemClock.sleep(150);}
    throw new Exception("Compact fixture did not load");
  }
  interface Task{void run()throws Exception;}
  private void check(String name,Task task){try{task.run();recordUi(name,"PASS","Android WebView fixture");}catch(Throwable e){recordUi(name,"FAIL",e.toString());try{capture(name+"-failure");}catch(Exception ignored){}}}
  private void recordUi(String name,String status,String detail){try{results.put(new JSONObject().put("name",name).put("status",status).put("detail",detail));}catch(Exception ignored){}}
  private Object eval(String code)throws Exception{
    CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> raw=new AtomicReference<>();
    runOnMainSync(()->MainActivity.current.web.evaluateJavascript(code,value->{raw.set(value);latch.countDown();}));
    require(latch.await(5,TimeUnit.SECONDS),"JavaScript result timed out");return new JSONTokener(raw.get()).nextValue();
  }
  private JSONObject metrics()throws Exception{
    eval("document.getElementById('fixture-metrics').textContent='';window.measureFixture();null");
    long end=SystemClock.elapsedRealtime()+3000;
    while(SystemClock.elapsedRealtime()<end){Object raw=eval("document.getElementById('fixture-metrics').textContent");if(raw instanceof String&&!((String)raw).isEmpty())return new JSONObject((String)raw);SystemClock.sleep(50);}
    throw new Exception("No fresh frame metrics");
  }
  private void geometry(JSONObject m)throws Exception{
    require(m.getInt("width")==m.getInt("scrollWidth"),"horizontal overflow");
    JSONObject main=m.getJSONObject("main"),footer=m.getJSONObject("footer"),input=m.getJSONObject("input");
    require(Math.abs(main.getDouble("b")-footer.getDouble("y"))<=2,"footer overlaps reading area");
    require(main.getDouble("h")>=80,"reading area collapsed");
    require(input.getDouble("h")>=44&&m.getJSONObject("turn").getDouble("h")>=44,"small composer touch target");
    require(footer.getDouble("b")<=m.getDouble("height")+2,"footer outside viewport");
  }
  private void fantaGeometry(JSONObject m)throws Exception{
    require(m.getInt("width")==m.getInt("scrollWidth"),"FANTA horizontal overflow");
    require(Math.abs(m.getJSONObject("main").getDouble("b")-m.getJSONObject("footer").getDouble("y"))<=2,"FANTA footer overlaps story");
    require(m.getJSONObject("input").getDouble("h")>=44,"FANTA input is too small");
    require(m.getJSONObject("footer").getDouble("b")<=m.getDouble("height")+2,"FANTA version strip outside viewport");
  }
  private void choose(String label)throws Exception{
    runOnMainSync(()->{View focused=MainActivity.current.getCurrentFocus();if(focused!=null)((InputMethodManager)MainActivity.current.getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(),0);MainActivity.current.findViewById(0x6002).performClick();});
    click(label);SystemClock.sleep(200);
  }
  private void click(String label)throws Exception{AccessibilityNodeInfo n=find(label,false);n.performAction(AccessibilityNodeInfo.ACTION_SHOW_ON_SCREEN);SystemClock.sleep(120);n=find(label,false);while(n!=null&&!n.isClickable())n=n.getParent();require(n!=null&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK),"cannot click "+label);SystemClock.sleep(200);}
  private void waitText(String text)throws Exception{find(text,false);}
  private AccessibilityNodeInfo find(String text,boolean editable)throws Exception{
    long end=SystemClock.elapsedRealtime()+10000;
    while(SystemClock.elapsedRealtime()<end){List<AccessibilityNodeInfo> nodes=new ArrayList<>();collectUi(getUiAutomation().getRootInActiveWindow(),nodes);
      boolean launcherAnr=nodes.stream().anyMatch(n->{String label=String.valueOf(n.getText());return label.contains("Pixel Launcher")&&label.contains("responding");});
      if(launcherAnr){for(AccessibilityNodeInfo n:nodes)if("Close app".equals(String.valueOf(n.getText()))){AccessibilityNodeInfo close=n;while(close!=null&&!close.isClickable())close=close.getParent();if(close!=null&&close.performAction(AccessibilityNodeInfo.ACTION_CLICK)){launcherAnrs++;break;}}SystemClock.sleep(300);continue;}
      for(AccessibilityNodeInfo n:nodes){String label=String.valueOf(n.getText()),description=String.valueOf(n.getContentDescription());if(n.isVisibleToUser()&&(editable?n.isEditable():(text.equals(label)||text.equals(description))))return n;}SystemClock.sleep(100);}
    throw new Exception("Control unavailable: "+text);
  }
  private void collectUi(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> nodes){if(n==null)return;nodes.add(n);for(int i=0;i<n.getChildCount();i++)collectUi(n.getChild(i),nodes);}
  private void shell(String command)throws Exception{try(android.os.ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(command);java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[1024];while(in.read(b)!=-1){}}}
  private void capture(String name)throws Exception{CountDownLatch painted=new CountDownLatch(1);runOnMainSync(()->MainActivity.current.web.postVisualStateCallback(SystemClock.uptimeMillis(),new WebView.VisualStateCallback(){@Override public void onComplete(long id){painted.countDown();}}));painted.await(3,TimeUnit.SECONDS);Bitmap image=getUiAutomation().takeScreenshot();if(image!=null)try(FileOutputStream f=new FileOutputStream(new File(out,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,f);}try{writeUi(name+".json",metrics().toString(2));}catch(Exception ignored){}}
  private void writeUi(String name,String text)throws Exception{try(FileOutputStream f=new FileOutputStream(new File(out,name))){f.write(text.getBytes(StandardCharsets.UTF_8));}}
  private static void require(boolean condition,String message)throws Exception{if(!condition)throw new Exception(message);}
}
