package com.sendmefile77.gamebroth.probe;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.InputDevice;
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
  private int launcherAnrs=0,touchFallbacks=0;
  private final JSONArray results=new JSONArray();
  @Override public void onCreate(Bundle arguments){instance=this;live=arguments!=null&&"true".equals(arguments.getString("networkProbe"));super.onCreate(arguments);}
  @Override public Activity newActivity(ClassLoader cl,String name,Intent intent)
      throws InstantiationException,IllegalAccessException,ClassNotFoundException {
    if(!live&&MainActivity.class.getName().equals(name))return new FixtureActivity();
    return super.newActivity(cl,name,intent);
  }
  public static class FixtureActivity extends MainActivity {
    public boolean failResultWrite,failLibraryWrite;
    @Override protected boolean writeCreationRecord(String raw){if(failResultWrite&&raw.contains("\"url\":")){failResultWrite=false;return false;}return super.writeCreationRecord(raw);}
    @Override protected boolean writeLibraryRecord(String raw){if(failLibraryWrite){failLibraryWrite=false;return false;}return super.writeLibraryRecord(raw);}
    public volatile Intent lastExport;
    public volatile Intent lastExternal;
    @Override public void startActivity(Intent intent){
      if(Intent.ACTION_VIEW.equals(intent.getAction())){lastExternal=new Intent(intent);return;}
      super.startActivity(intent);
    }
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
            if(url.equals(NURSERY))return page("nursery-parent.html");
            if(url.equals(NURSERY_ORIGIN+"/0oobcp6o2a"))return page("nursery-game.html");
            if(url.equals(ALCHEMY))return page("alchemy-parent.html");
            if(url.equals(ALCHEMY_ORIGIN+"/8kwa4xkn39"))return page("alchemy-game.html");
            if(url.equals(FANTA))return page("fanta-parent.html");
            if(url.equals(FANTA_ORIGIN+"/n89crmz16e"))return page("fanta-game.html");
            if(url.equals(COMING_SOON))return page("stub-1-parent.html");
            if(url.equals(SECOND_PLACEHOLDER))return page("stub-2-parent.html");
            if(url.equals("https://f66f1b0e712d0549df1a0fb16d5a591d.perchance.org/j4o3qot7f6"))return page("stub-1-game.html");
            if(url.equals("https://380649050583c0acb5916e7fc43a4709.perchance.org/1x315uqdpf"))return page("stub-2-game.html");
            if(url.equals(GAME))return response("<html><body><div id='menuBarEl'>Perchance menu</div><h1>MNO Engine fixture</h1></body></html>");
            String baseUrl=url.split("[?#]",2)[0];
            if(baseUrl.equals("https://perchance.org/blank"))return libraryPage("Пустой шаблон теста","library-empty-fixture");
            if(baseUrl.equals("https://perchance.org/library-user-fixture"))return libraryPage("Добавленная тестовая игра","library-copy-fixture");
            if(baseUrl.equals("https://perchance.org/library-empty-fixture"))return libraryPage("Новая тестовая игра","library-copy-fixture");
            if(baseUrl.equals("https://perchance.org/library-copy-fixture"))return libraryPage("Отдельная тестовая копия","library-copy-fixture");
            if(baseUrl.equals("https://perchance.org/library-login-copy-fixture"))return libraryPage("Копия после входа: страница","library-login-copy-fixture");
            if(baseUrl.equals("https://perchance.org/library-held-copy-fixture"))return libraryPage("Восстановленная копия: страница","library-held-copy-fixture");
            if(baseUrl.equals("https://perchance.org/library-recovered-fixture"))return libraryPage("Копия после ошибки записи","library-recovered-fixture");
          }catch(Exception e){return response("Fixture failed: "+e);}
          WebResourceResponse local=base.shouldInterceptRequest(view,request);if(local!=null)return local;
          return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
        }
        private WebResourceResponse page(String name)throws Exception{return new WebResourceResponse("text/html","UTF-8",instance.getContext().getAssets().open(name));}
        private WebResourceResponse response(String html){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));}
        private WebResourceResponse libraryPage(String label,String saved){return response("""
          <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
          <style>body{color:#eee;background:#181a1d;font:16px sans-serif}button{min-height:48px}#settingsModalEl{display:none}#fixture-login{display:none}</style>
          <div id="menuBarEl">
            <button data-ref="loginButton" onclick="document.getElementById('fixture-login').style.display='block'">login</button>
            <button data-ref="editButton" style="display:none">edit</button>
            <button data-ref="settingsButton" onclick="document.getElementById('settingsModalEl').style.display='block'">settings</button>
            <button data-ref="saveButton" onclick="fixtureSave()">save</button>
            <span data-ref="accountButton">account</span>
          </div>
          <h1>__TITLE__</h1>
          <div id="fixture-login"><button onclick="localStorage.removeItem('fixtureLoggedOut');fixtureAuth();document.getElementById('fixture-login').style.display='none'">Войти в Perchance</button></div>
          <div id="settingsModalEl"><button onclick="settingsModal.duplicateGenerator()">duplicate it</button><span data-ref="duplicateGeneratorStatus"></span></div>
          <script>
            function fixtureAuth(){
              const loggedIn=!localStorage.getItem('fixtureLoggedOut');
              window.userOwnsThisGenerator=loggedIn&&location.pathname!=='/blank';
              document.querySelector('[data-ref=loginButton]').style.display=loggedIn?'none':'inline-block';
              document.querySelector('[data-ref=settingsButton]').style.display=window.userOwnsThisGenerator?'inline-block':'none';
              document.querySelector('[data-ref=accountButton]').style.display=loggedIn?'inline-block':'none';
            }
            function fixtureDuplicate(){
              localStorage.fixtureCopyCalls=String(Number(localStorage.fixtureCopyCalls||0)+1);
              localStorage.fixtureSourceAtResult=location.pathname;
              if(localStorage.fixtureHoldCopy)return;
              setTimeout(()=>{const a=document.createElement('a');a.href='/'+(localStorage.fixtureCopyTarget||'library-copy-fixture')+'#edit';a.target='_blank';a.textContent='копия';const status=document.querySelector('[data-ref=duplicateGeneratorStatus]');status.textContent='Success! → ';status.append(a);},Number(localStorage.fixtureCopyDelay||90));
            }
            function fixtureSave(){
              if(window.userOwnsThisGenerator){localStorage.fixtureWrongSave='1';return;}
              localStorage.fixtureSaveCalls=String(Number(localStorage.fixtureSaveCalls||0)+1);history.replaceState({},'', '/__SAVED__#edit');
            }
            window.settingsModal={duplicateGenerator:fixtureDuplicate};fixtureAuth();
          </script>
          """.replace("__TITLE__",label).replace("__SAVED__",saved));}

        @Override public void onPageStarted(WebView view,String url,Bitmap icon){base.onPageStarted(view,url,icon);}
        @Override public void onPageFinished(WebView view,String url){base.onPageFinished(view,url);}
        @Override public void doUpdateVisitedHistory(WebView view,String url,boolean reload){base.doUpdateVisitedHistory(view,url,reload);}
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
        require(m.getString("version").contains("APK "+BuildConfig.VERSION_NAME)&&m.getString("version").contains("Картинки 0.7"),"APK or generator version missing from footer");
        shellGeometry(true);
        AtomicReference<String> hud=new AtomicReference<>();runOnMainSync(()->hud.set(((TextView)MainActivity.current.findViewById(R.id.game_day)).getText().toString()));
        require("День 1".equals(hud.get()),"day did not reach native toolbar");capture("compact");
      });
      check("empty_bottom_slot",()->{
        try{
          shellGeometry(true);capture("empty-bottom-slot");
          eval("document.getElementById('adCtn').textContent='Populated slot';null");
          shellGeometry(false);geometry(metrics());
          eval("document.getElementById('adCtn').replaceChildren();null");
          shellGeometry(true);geometry(metrics());
          eval("document.getElementById('adCtn').appendChild(document.createElement('iframe'));null");
          shellGeometry(false); // Pending frames must never be treated as empty.
          eval("document.getElementById('adCtn').replaceChildren(document.createTextNode('  '));null");
          shellGeometry(true);geometry(metrics());
        }finally{
          eval("document.getElementById('adCtn').replaceChildren();null");shellGeometry(true);
        }
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
        choose("Обычный вид");require(!"none".equals(metrics().getString("header")),"ordinary view did not restore header");shellGeometry(true);
        choose("Компактный вид");
        for(int n=0;n<5;n++)choose("Увеличить текст");geometry(metrics());capture("large-text");
        for(int n=0;n<5;n++)choose("Уменьшить текст");
      });
      check("keyboard_and_draft",()->{
        AccessibilityNodeInfo input=find("Твоё действие",true);Bundle value=new Bundle();value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"Тестовая заметка");
        require(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,value),"cannot type into composer");
        String beforeHide="";
        try{
          input.performAction(AccessibilityNodeInfo.ACTION_FOCUS);tapNode(find("Твоё действие",true));
          long end=SystemClock.elapsedRealtime()+5000;while(!imeVisible()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);
          require(imeVisible(),"keyboard did not open after real input tap");shell("input text test");waitFixture();
          JSONObject m=metrics();beforeHide=m.getString("command");require(beforeHide.length()=="Тестовая заметка".length()+4,"typing changed unexpected characters");geometry(m);shellGeometry(true);capture("keyboard");
          require(m.getJSONObject("input").getDouble("b")<=m.getDouble("height")+1,"keyboard covers input");
        }finally{
          runOnMainSync(()->((InputMethodManager)MainActivity.current.getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(MainActivity.current.web.getWindowToken(),0));
          long end=SystemClock.elapsedRealtime()+3000;while(imeVisible()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);
        }
        waitFixture();require(metrics().getString("command").equals(beforeHide),"draft changed after keyboard dismissal");
      });
      check("reload_and_game_selection",()->{
        String draft=metrics().getString("command");runOnMainSync(()->MainActivity.current.web.reload());waitFixture();
        require(draft.equals(metrics().getString("command")),"reload lost draft");
        choose("Выбрать игру");click("MNO Engine");waitText("MNO Engine fixture");
        require(Boolean.TRUE.equals(eval("!document.documentElement.classList.contains('gb-apk-game')")),"Broth layout applied to MNO");
        runOnMainSync(()->MainActivity.current.onBackPressed());waitFixture();geometry(metrics());
        AtomicReference<String> restoredTitle=new AtomicReference<>();runOnMainSync(()->restoredTitle.set(((TextView)MainActivity.current.findViewById(R.id.game_title)).getText().toString()));
        require("Ржавый Фаллос".equals(restoredTitle.get()),"native title did not follow browser back to Broth");
        require(draft.equals(metrics().getString("command")),"switching games lost Broth draft");
        require(metrics().getInt("actions")==0,"UI test submitted a game action");
      });
      check("fanta_third_game_and_export",()->{
        choose("Выбрать игру");click("FANTA · Старый Свет");
        waitText("Выбрать завязку →");click("Выбрать завязку →");click("Эта история →");
        eval("document.getElementById('outputIframeEl').contentWindow.postMessage('scroll-start-button','"+MainActivity.FANTA_ORIGIN+"')");
        click("Начать приключение →");waitText("Осмотреть печать");
        JSONObject m=metrics();fantaGeometry(m);shellGeometry(true);
        require(m.getString("version").contains("APK "+BuildConfig.VERSION_NAME)&&m.getString("version").contains("FANTA 0.1"),"FANTA footer versions missing");
        require("Ход 0".equals(m.getString("turnLabel")),"opening consumed a turn");
        require(Boolean.TRUE.equals(eval("typeof window.GameBrothFiles==='undefined'")),"export bridge exposed in top-level shell");
        require(Boolean.TRUE.equals(eval("getComputedStyle(document.getElementById('menuBarEl')).display!=='none'")),"Perchance navigation hidden for FANTA");
        click("Галерея");touchGameButton("Открыть крупно",MainActivity.FANTA_ORIGIN);waitDialog(true);
        runOnMainSync(()->MainActivity.current.onBackPressed());waitDialog(false);
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
      check("nursery_fourth_game",()->workshop(true));
      check("alchemy_fifth_game",()->workshop(false));
      check("two_perchance_placeholders",()->{
        // The upgrade must retain a user who last opened the old local slot.
        runOnMainSync(()->{MainActivity old=MainActivity.current;old.getPreferences(Activity.MODE_PRIVATE).edit().putString("selectedGame",MainActivity.LEGACY_COMING_SOON).apply();Intent intent=new Intent(old,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);old.finish();old.startActivity(intent);});
        waitPlaceholder(1);placeholderPage(1);SystemClock.sleep(200);capture("placeholder-1");
        choose("Выбрать игру");waitText("Заглушка 1");click("Заглушка 2");
        waitPlaceholder(2);placeholderPage(2);SystemClock.sleep(200);capture("placeholder-2");
        choose("Открыть в браузере");
        Intent external=((FixtureActivity)MainActivity.current).lastExternal;
        require(external!=null&&MainActivity.SECOND_PLACEHOLDER.equals(external.getDataString()),"placeholder browser action has the wrong URL");
        runOnMainSync(()->MainActivity.current.onBackPressed());waitPlaceholder(1);placeholderPage(1);
        choose("Выбрать игру");click("Заглушка 2");waitPlaceholder(2);
        runOnMainSync(()->{MainActivity old=MainActivity.current;Intent intent=new Intent(old,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);old.finish();old.startActivity(intent);});
        waitPlaceholder(2);placeholderPage(2);
        choose("Выбрать игру");click("Алхимия образов");waitWorkshop(2);
      });
      check("native_app_and_rotation",()->{
        require(getTargetContext().getApplicationInfo().icon!=0,"launcher icon missing from package");
        require(getTargetContext().getApplicationInfo().loadIcon(getTargetContext().getPackageManager()) instanceof android.graphics.drawable.AdaptiveIconDrawable,"adaptive launcher icon not packaged");
        choose("О приложении");waitText("Game Broth Hub · "+BuildConfig.VERSION_NAME);capture("about");click("Понятно");
        MainActivity before=MainActivity.current;eval("window.__gbRotationMarker='keep-game-alive'");
        try{
          runOnMainSync(()->before.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
          long end=SystemClock.elapsedRealtime()+5000;while(getTargetContext().getResources().getConfiguration().orientation!=android.content.res.Configuration.ORIENTATION_LANDSCAPE&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);
          require(getTargetContext().getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE,"device did not rotate");
          require(MainActivity.current==before,"rotation recreated the game activity");
          require("keep-game-alive".equals(eval("window.__gbRotationMarker")),"rotation reloaded the game document");
          require(metrics().getInt("imageCalls")==0&&metrics().getInt("textCalls")==0,"rotation regenerated game content");capture("landscape");
        }finally{
          runOnMainSync(()->MainActivity.current.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        }
      });
      check("library_rename_and_restart",()->{
        eval("window.__renameMarker='alive'");choose("Переименовать игру");set(find("Название игры",false),"Моя мастерская");click("Готово");
        require("Моя мастерская".equals(nativeTitle()),"rename did not update native toolbar");
        require("alive".equals(eval("window.__renameMarker")),"rename reloaded the game");restartLibrary();waitWorkshop(2);
        require("Моя мастерская".equals(nativeTitle()),"rename lost after restart");
        choose("Переименовать игру");set(find("Название игры",false),"Алхимия образов");click("Готово");capture("library-rename");
      });
      check("library_add_link_and_isolation",()->{
        choose("Добавить игру");click("Добавить по ссылке");set(find("Название игры",false),"Моя игра");set(find("Ссылка Perchance",false),"https://perchance.org/library-user-fixture#story");click("Добавить");
        waitLibraryText("Добавленная тестовая игра");require("Моя игра".equals(nativeTitle()),"custom title missing");
        require(Boolean.TRUE.equals(eval("typeof window.GameBrothFiles==='undefined'&&!document.documentElement.classList.contains('gb-apk-game')")),"custom game received privileged bridge or game-specific layout");
        restartLibrary();waitLibraryText("Добавленная тестовая игра");require("Моя игра".equals(nativeTitle()),"added link lost after restart");
        choose("Выбрать игру");waitText("Моя игра");click("Моя игра");capture("library-custom");
      });
      check("library_empty_game_end_to_end",()->{
        eval("localStorage.removeItem('fixtureLoggedOut');localStorage.fixtureSaveCalls='0';localStorage.removeItem('fixtureWrongSave')");
        choose("Добавить игру");click("Новая пустая игра Perchance");set(find("Название игры",false),"Мой пустой мир");click("Готово");
        waitLibraryText("Новая тестовая игра");require("Мой пустой мир".equals(nativeTitle()),"new empty game not added automatically");
        require("1".equals(eval("localStorage.fixtureSaveCalls")),"native creation did not invoke Perchance Save exactly once");
        require(Boolean.TRUE.equals(eval("location.pathname==='/library-empty-fixture'")),"template accepted instead of saved URL");capture("library-empty");
      });
      check("library_copy_end_to_end",()->{
        eval("localStorage.fixtureCopyCalls='0';localStorage.fixtureCopyTarget='library-copy-fixture';localStorage.removeItem('fixtureHoldCopy')");
        choose("Создать копию игры");click("Готово");waitLibraryText("Отдельная тестовая копия");
        require("Мой пустой мир · копия".equals(nativeTitle()),"native Copy did not add independent page");
        require("1".equals(eval("localStorage.fixtureCopyCalls")),"native Copy did not invoke site duplicate action exactly once");
        require("/library-empty-fixture".equals(eval("localStorage.fixtureSourceAtResult")),"copy source changed before site result");
        require(eval("localStorage.fixtureWrongSave")==JSONObject.NULL,"owned original was saved instead of duplicated");
        choose("Выбрать игру");click("Мой пустой мир");waitLibraryText("Новая тестовая игра");capture("library-copy");
      });
      check("library_copy_waits_for_login",()->{
        eval("localStorage.fixtureCopyCalls='0';localStorage.fixtureLoggedOut='1';localStorage.fixtureCopyTarget='library-login-copy-fixture';localStorage.fixtureCopyDelay='600';fixtureAuth();const a=document.createElement('a');a.href='/library-copy-fixture#edit';a.textContent='старая копия';document.querySelector('[data-ref=duplicateGeneratorStatus]').replaceChildren(a)");
        choose("Создать копию игры");set(find("Название игры",false),"Копия после входа");click("Готово");
        long end=SystemClock.elapsedRealtime()+10000;boolean login=false;
        while(SystemClock.elapsedRealtime()<end){if(Boolean.TRUE.equals(eval("document.getElementById('fixture-login')?.style.display==='block'"))){login=true;break;}SystemClock.sleep(100);}
        require(login,"site login was not opened by native Copy");require("0".equals(eval("localStorage.fixtureCopyCalls")),"copy submitted before login");
        libraryClick("Войти в Perchance");waitLibraryText("Копия после входа: страница");
        require("Копия после входа".equals(nativeTitle()),"copy did not resume after login");require("1".equals(eval("localStorage.fixtureCopyCalls")),"duplicate submitted more than once after login");eval("localStorage.removeItem('fixtureCopyDelay')");capture("library-copy-login");
      });
      check("library_creation_restart_does_not_repeat",()->{
        eval("localStorage.fixtureCopyCalls='0';localStorage.fixtureCopyTarget='library-held-copy-fixture';localStorage.fixtureHoldCopy='1'");
        choose("Создать копию игры");set(find("Название игры",false),"Прерванная копия");click("Готово");
        long end=SystemClock.elapsedRealtime()+10000;boolean submitted=false;
        while(SystemClock.elapsedRealtime()<end){if("1".equals(eval("localStorage.fixtureCopyCalls"))){submitted=true;break;}SystemClock.sleep(100);}
        require(submitted,"site copy action not submitted");restartLibrary();waitLibraryText("Копия после входа: страница");
        require("1".equals(eval("localStorage.fixtureCopyCalls")),"restart repeated a potentially completed server operation");waitText("Проверить результат");
        click("Как?");click("Вставить ссылку уже созданной игры");set(find("Ссылка Perchance",false),"https://perchance.org/library-login-copy-fixture");click("Добавить");
        require(find("Ссылка Perchance",false).isContentInvalid(),"original accepted as a copy");
        set(find("Ссылка Perchance",false),"https://perchance.org/library-held-copy-fixture");click("Добавить");waitLibraryText("Восстановленная копия: страница");
        require("Прерванная копия".equals(nativeTitle()),"manual recovery did not finish pending creation");eval("localStorage.removeItem('fixtureHoldCopy')");capture("library-copy-restored");
      });
      check("library_copy_survives_storage_failure",()->{
        eval("localStorage.fixtureCopyCalls='0';localStorage.fixtureCopyTarget='library-recovered-fixture';localStorage.fixtureCopyDelay='600'");
        runOnMainSync(()->{FixtureActivity a=(FixtureActivity)MainActivity.current;a.failResultWrite=true;a.failLibraryWrite=true;});
        choose("Создать копию игры");set(find("Название игры",false),"Сохранённая копия");click("Готово");
        waitText("Добавить в набор");require(find("Добавить в набор",false).isEnabled(),"result left the creation button disabled");
        require("1".equals(eval("localStorage.fixtureCopyCalls")),"storage failure repeated the server copy");click("Добавить в набор");waitLibraryText("Копия после ошибки записи");
        require("Сохранённая копия".equals(nativeTitle()),"retry lost the created URL");require("1".equals(eval("localStorage.fixtureCopyCalls")),"retry created another copy");eval("localStorage.removeItem('fixtureCopyDelay')");capture("library-storage-recovery");
      });
      check("library_invalid_created_message_recovers",()->{
        eval("localStorage.fixtureCopyCalls='0';localStorage.fixtureHoldCopy='1'");choose("Создать копию игры");click("Готово");
        long end=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<end&&!"1".equals(eval("localStorage.fixtureCopyCalls")))SystemClock.sleep(100);
        require("1".equals(eval("localStorage.fixtureCopyCalls")),"copy not submitted");AtomicReference<String> token=new AtomicReference<>();
        runOnMainSync(()->{try{token.set(new JSONObject(MainActivity.current.getPreferences(Activity.MODE_PRIVATE).getString("pendingCreationV1","{}")).getString("token"));}catch(Exception e){throw new RuntimeException(e);}});
        eval("GameBrothCreation.postMessage(JSON.stringify({type:'created',token:"+JSONObject.quote(token.get())+",url:'https://perchance.org/login'}))");waitText("Проверить результат");
        require(find("Проверить результат",false).isEnabled(),"invalid result left creation locked");click("✕");click("Закрыть создание");eval("localStorage.removeItem('fixtureHoldCopy')");capture("library-invalid-result");
      });
      check("library_restart_after_catalogue_commit",()->{
        runOnMainSync(()->{try{String raw=new JSONObject().put("name","Сохранённая копия").put("source","https://perchance.org/library-held-copy-fixture").put("url","https://perchance.org/library-recovered-fixture").put("phase","ready").put("token","interrupted-finalization").toString();MainActivity.current.getPreferences(Activity.MODE_PRIVATE).edit().putString("pendingCreationV1",raw).commit();}catch(Exception e){throw new RuntimeException(e);}});
        restartLibrary();waitLibraryText("Копия после ошибки записи");waitText("Добавить в набор");click("Добавить в набор");
        require("Сохранённая копия".equals(nativeTitle()),"restart lost the already committed catalogue entry");
        AtomicReference<String> pending=new AtomicReference<>();runOnMainSync(()->pending.set(MainActivity.current.getPreferences(Activity.MODE_PRIVATE).getString("pendingCreationV1",null)));require(pending.get()==null,"finalization left a stuck draft");capture("library-finalization-restart");
      });

    }catch(Throwable e){recordUi("harness","FAIL",e.toString());}
    try{writeUi("results.json",new JSONObject().put("results",results).put("sdk",android.os.Build.VERSION.SDK_INT).put("dismissedPixelLauncherAnrs",launcherAnrs).put("nativeTouchFallbacks",touchFallbacks).toString(2));}catch(Exception ignored){}
    Bundle result=new Bundle();result.putString("stream",results.toString()+"\n");finish(Activity.RESULT_OK,result);
  }
  private String nativeTitle(){AtomicReference<String> value=new AtomicReference<>();runOnMainSync(()->value.set(((TextView)MainActivity.current.findViewById(R.id.game_title)).getText().toString()));return value.get();}
  private void waitLibraryText(String text)throws Exception{
    // WebView's accessibility tree can still refer to the preceding document
    // after Activity restart. Wait on rendered fixture text before using controls.
    long end=SystemClock.elapsedRealtime()+15000;
    while(SystemClock.elapsedRealtime()<end){if(Boolean.TRUE.equals(eval("document.readyState==='complete'&&[...document.querySelectorAll('h1,a')].some(e=>e.textContent.trim()==="+JSONObject.quote(text)+")")))return;SystemClock.sleep(100);}
    throw new Exception("Library fixture did not render: "+text);
  }
  private void libraryClick(String label)throws Exception{
    JSONObject rect=(JSONObject)eval("(()=>{const e=[...document.querySelectorAll('button')].find(b=>b.textContent.trim()==="+JSONObject.quote(label)+");e.scrollIntoView({block:'center'});const r=e.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,w:innerWidth};})()");
    int[] origin=new int[2];AtomicReference<Float> scale=new AtomicReference<>();
    runOnMainSync(()->{MainActivity.current.web.getLocationOnScreen(origin);scale.set((float)MainActivity.current.web.getWidth()/(float)rect.optDouble("w"));});
    tapAt(origin[0]+(float)rect.getDouble("x")*scale.get(),origin[1]+(float)rect.getDouble("y")*scale.get());
  }
  private void restartLibrary(){runOnMainSync(()->{MainActivity old=MainActivity.current;Intent intent=new Intent(old,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);old.finish();old.startActivity(intent);});SystemClock.sleep(1000);}
  private void workshop(boolean nursery)throws Exception{
    String name=nursery?"Питомник":"Алхимия образов",origin=nursery?MainActivity.NURSERY_ORIGIN:MainActivity.ALCHEMY_ORIGIN,page=nursery?MainActivity.NURSERY:MainActivity.ALCHEMY;
    choose("Выбрать игру");click(name);waitText(nursery?"Собрать существо":"Создать образ");
    require(Boolean.TRUE.equals(eval("typeof window.GameBrothFiles==='undefined'")),"export bridge exposed to shell");
    AccessibilityNodeInfo input=find("",true);Bundle text=new Bundle();text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,nursery?"Жук, янтарь, плющ":"Соль, пепел");
    require(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,text),"cannot enter workshop materials");
    click(nursery?"Собрать существо":"Создать образ");
    waitWorkshop(nursery?1:2);JSONObject m=metrics();workshopGeometry(m);shellGeometry(true);
    require(m.getString("version").contains("APK "+BuildConfig.VERSION_NAME)&&m.getString("version").contains(name+" 0.1"),"workshop versions missing");
    if(nursery){touchGameButton("Утвердить эталон",origin);touchGameButton("Испытание 1 · Первый контакт",origin);waitWorkshop(2);require(metrics().getInt("scenes")==2,"nursery did not add first trial");}
    capture(nursery?"nursery":"alchemy");
    touchGameButton("Открыть крупно",origin);waitDialog(true);
    runOnMainSync(()->MainActivity.current.onBackPressed());waitDialog(false);
    runOnMainSync(()->((FixtureActivity)MainActivity.current).lastExport=null);
    eval("document.getElementById('outputIframeEl').contentWindow.postMessage('bad-export','"+origin+"')");SystemClock.sleep(250);
    require(((FixtureActivity)MainActivity.current).lastExport==null,"cross-workshop export was accepted");
    click("Сейвы");click("Скачать сохранение");long end=SystemClock.elapsedRealtime()+5000;
    while(((FixtureActivity)MainActivity.current).lastExport==null&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(50);
    Intent exported=((FixtureActivity)MainActivity.current).lastExport;require(exported!=null&&("broth-"+(nursery?"nursery":"alchemy")+"-save.json").equals(exported.getStringExtra(Intent.EXTRA_TITLE)),"workshop JSON did not reach native picker");
    runOnMainSync(()->{MainActivity old=MainActivity.current;Intent intent=new Intent(old,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);old.finish();old.startActivity(intent);});SystemClock.sleep(700);
    waitText(nursery?"Собрать существо":"Создать образ");waitWorkshop(nursery?2:2);m=metrics();workshopGeometry(m);shellGeometry(true);
    AtomicReference<String> url=new AtomicReference<>();runOnMainSync(()->url.set(MainActivity.current.web.getUrl()));require(page.equals(url.get()),"workshop selection lost after restart");
    require(m.getInt("textCalls")==0&&m.getInt("imageCalls")==0,"workshop regenerated restored images");capture(nursery?"nursery-restored":"alchemy-restored");
  }
  private void waitWorkshop(int images)throws Exception{
    long end=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<end){try{JSONObject m=metrics();if(m.getInt("images")>=images&&!m.optBoolean("pending")&&m.getString("version").contains("APK "+BuildConfig.VERSION_NAME))return;}catch(Exception ignored){}SystemClock.sleep(100);}throw new Exception("Workshop did not finish scene");
  }
  private void waitPlaceholder(int number)throws Exception{
    long end=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<end){try{if(("Заглушка "+number).equals(metrics().optString("stubName")))return;}catch(Exception ignored){}SystemClock.sleep(100);}throw new Exception("Placeholder did not load: "+number);
  }
  private void placeholderPage(int number)throws Exception{
    String expected=number==1?MainActivity.COMING_SOON:MainActivity.SECOND_PLACEHOLDER;
    require("https://perchance.org".equals(eval("location.origin")),"placeholder is not a Perchance page");
    AtomicReference<String> url=new AtomicReference<>();runOnMainSync(()->url.set(MainActivity.current.web.getUrl()));require(expected.equals(url.get()),"wrong placeholder selected");
    require(Boolean.TRUE.equals(eval("getComputedStyle(document.getElementById('menuBarEl')).display!=='none'&&document.getElementById('menuBarEl').textContent.includes('login')&&document.getElementById('menuBarEl').textContent.includes('edit')")),"Perchance login/edit menu is missing");
    require(Boolean.TRUE.equals(eval("typeof window.GameBrothFiles==='undefined'"))&&!metrics().getBoolean("exportBridge"),"export bridge exposed to empty placeholder");
    require(metrics().getInt("width")==metrics().getInt("scrollWidth"),"placeholder has horizontal overflow");shellGeometry(true);
  }
  private void workshopGeometry(JSONObject m)throws Exception{
    require(m.getInt("width")==m.getInt("scrollWidth"),"workshop horizontal overflow");
    require(Math.abs(m.getJSONObject("main").getDouble("b")-m.getJSONObject("footer").getDouble("y"))<=2,"workshop footer overlaps story");
    require(m.getJSONObject("footer").getDouble("b")<=m.getDouble("height")+2,"workshop version outside viewport");
  }
  private void launch()throws Exception{
    Intent intent=new Intent(getTargetContext(),MainActivity.class).putExtra("page",MainActivity.BROTH).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivitySync(intent);waitFixture();
  }
  private void waitFixture()throws Exception{
    long end=SystemClock.elapsedRealtime()+30000;
    while(SystemClock.elapsedRealtime()<end){try{
      JSONObject m=metrics();AtomicReference<Boolean> loaded=new AtomicReference<>(false);
      runOnMainSync(()->loaded.set(MainActivity.current.findViewById(R.id.game_status).getVisibility()==View.GONE));
      if(loaded.get()&&"none".equals(m.optString("header"))){shellGeometry(true);return;}
    }catch(Exception ignored){}SystemClock.sleep(150);}
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
  private void shellGeometry(boolean empty)throws Exception{
    long end=SystemClock.elapsedRealtime()+5000;Exception last=null;
    while(SystemClock.elapsedRealtime()<end){try{
      JSONObject m=(JSONObject)eval("(()=>{const slot=document.getElementById('adCtn').getBoundingClientRect(),frame=document.getElementById('outputIframeEl').getBoundingClientRect(),menu=document.getElementById('menuBarEl').getBoundingClientRect();return {slotHeight:slot.height,frameHeight:frame.height,frameBottom:frame.bottom,frameTop:frame.top,menuBottom:menu.bottom,menuHeight:menu.height,height:innerHeight};})()");
      require(Math.abs(m.getDouble("slotHeight")-(empty?0:50))<=1,"empty/populated slot has wrong height: "+m);
      require(Math.abs(m.getDouble("frameBottom")+m.getDouble("slotHeight")-m.getDouble("height"))<=2,"gap below game viewport: "+m);
      require(m.getDouble("menuHeight")>=26&&Math.abs(m.getDouble("frameTop")-m.getDouble("menuBottom"))<=2,"site navigation clipped: "+m);
      // Android's loading row and iframe resize can settle on different frames.
      // Compare the child to the CURRENT parent viewport, not an older window size.
      require(Math.abs(metrics().getDouble("height")-m.getDouble("frameHeight"))<=1,"game viewport resize has not settled: "+m);
      return;
    }catch(Exception e){last=e;}SystemClock.sleep(100);}
    throw last==null?new Exception("No shell geometry"):last;
  }
  private void fantaGeometry(JSONObject m)throws Exception{
    require(m.getInt("width")==m.getInt("scrollWidth"),"FANTA horizontal overflow");
    require(Math.abs(m.getJSONObject("main").getDouble("b")-m.getJSONObject("footer").getDouble("y"))<=2,"FANTA footer overlaps story");
    require(m.getJSONObject("input").getDouble("h")>=44,"FANTA input is too small");
    require(m.getJSONObject("footer").getDouble("b")<=m.getDouble("height")+2,"FANTA version strip outside viewport");
  }
  private void choose(String label)throws Exception{
    runOnMainSync(()->{View focused=MainActivity.current.getCurrentFocus();if(focused!=null)((InputMethodManager)MainActivity.current.getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(),0);MainActivity.current.findViewById(R.id.game_menu).performClick();});
    click(label);SystemClock.sleep(200);
  }
  private void click(String label)throws Exception{
    AccessibilityNodeInfo n=find(label,false);n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
    try{getUiAutomation().waitForIdle(250,3000);}catch(java.util.concurrent.TimeoutException ignored){}
    n=find(label,false);while(n!=null&&!n.isClickable())n=n.getParent();require(n!=null,"no clickable control: "+label);
    if(!n.performAction(AccessibilityNodeInfo.ACTION_CLICK)){
      // WebView can invalidate a virtual accessibility node during smooth scrolling.
      // A real touch at the freshly observed on-screen bounds verifies the same UI.
      tapNode(find(label,false));touchFallbacks++;
    }
    SystemClock.sleep(200);
  }
  private void tapNode(AccessibilityNodeInfo node)throws Exception{
    Rect bounds=new Rect();node.getBoundsInScreen(bounds);
    int width=getTargetContext().getResources().getDisplayMetrics().widthPixels,height=getTargetContext().getResources().getDisplayMetrics().heightPixels;
    require(bounds.intersect(0,0,width,height)&&bounds.width()>0&&bounds.height()>0,"control outside screen: "+node.getText()+" "+bounds);
    tapAt(bounds.exactCenterX(),bounds.exactCenterY());
  }
  private void touchGameButton(String label,String origin)throws Exception{
    long token=SystemClock.elapsedRealtime();String quoted=JSONObject.quote(origin);
    JSONObject request=new JSONObject().put("type","fixture-locate-button").put("label",label).put("token",token);
    eval("window.__fixtureButton=null;window.addEventListener('message',function locate(e){if(e.origin==="+quoted+"&&e.data?.type==='fixture-button'&&e.data.token==="+token+"){window.__fixtureButton=e.data;window.removeEventListener('message',locate);}});document.getElementById('outputIframeEl').contentWindow.postMessage("+request+","+quoted+");null");
    JSONObject bounds=null;long end=SystemClock.elapsedRealtime()+5000;
    while(SystemClock.elapsedRealtime()<end){Object raw=eval("JSON.stringify(window.__fixtureButton)");if(raw instanceof String&&!"null".equals(raw)){bounds=new JSONObject((String)raw);break;}SystemClock.sleep(50);}
    require(bounds!=null,"fixture button unavailable: "+label);
    require(bounds.getDouble("y")>=0&&bounds.getDouble("y")+bounds.getDouble("h")<=bounds.getDouble("height")+1&&bounds.getDouble("h")>0,"fixture button outside viewport: "+label);
    JSONObject frame=new JSONObject((String)eval("JSON.stringify((()=>{const r=document.getElementById('outputIframeEl').getBoundingClientRect();return {x:r.x,y:r.y,width:innerWidth};})())"));
    int[] location=new int[2];AtomicReference<Integer> webWidth=new AtomicReference<>();runOnMainSync(()->{MainActivity.current.web.getLocationOnScreen(location);webWidth.set(MainActivity.current.web.getWidth());});
    double scale=webWidth.get()/frame.getDouble("width");
    tapAt((float)(location[0]+scale*(frame.getDouble("x")+bounds.getDouble("x")+bounds.getDouble("w")/2)),(float)(location[1]+scale*(frame.getDouble("y")+bounds.getDouble("y")+bounds.getDouble("h")/2)));SystemClock.sleep(200);
  }
  private void tapAt(float x,float y)throws Exception{
    long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,x,y,0);
    down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    try{require(getUiAutomation().injectInputEvent(down,true)&&getUiAutomation().injectInputEvent(up,true),"touch injection failed");}finally{down.recycle();up.recycle();}
  }
  private boolean imeVisible(){
    AtomicReference<Boolean> visible=new AtomicReference<>(false);runOnMainSync(()->{android.view.WindowInsets insets=MainActivity.current.getWindow().getDecorView().getRootWindowInsets();visible.set(insets!=null&&insets.isVisible(android.view.WindowInsets.Type.ime()));});return visible.get();
  }
  private void waitDialog(boolean open)throws Exception{
    long end=SystemClock.elapsedRealtime()+6000;while(SystemClock.elapsedRealtime()<end){if(metrics().getBoolean("dialog")==open)return;SystemClock.sleep(100);}throw new Exception(open?"image viewer did not open":"image viewer did not close");
  }
  private void waitText(String text)throws Exception{find(text,false);}
  private AccessibilityNodeInfo find(String text,boolean editable)throws Exception{
    long end=SystemClock.elapsedRealtime()+10000;
    while(SystemClock.elapsedRealtime()<end){List<AccessibilityNodeInfo> nodes=new ArrayList<>();collectUi(getUiAutomation().getRootInActiveWindow(),nodes);
      boolean launcherAnr=nodes.stream().anyMatch(n->{String label=String.valueOf(n.getText());return label.contains("Pixel Launcher")&&label.contains("responding");});
      if(launcherAnr){for(AccessibilityNodeInfo n:nodes)if("Close app".equals(String.valueOf(n.getText()))){AccessibilityNodeInfo close=n;while(close!=null&&!close.isClickable())close=close.getParent();if(close!=null&&close.performAction(AccessibilityNodeInfo.ACTION_CLICK)){launcherAnrs++;break;}}SystemClock.sleep(300);continue;}
      for(AccessibilityNodeInfo n:nodes){String label=String.valueOf(n.getText()),description=String.valueOf(n.getContentDescription());if(n.isVisibleToUser()&&(editable?n.isEditable():(text.equalsIgnoreCase(label)||text.equalsIgnoreCase(description))))return n;}SystemClock.sleep(100);}
    throw new Exception("Control unavailable: "+text);
  }
  private void collectUi(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> nodes){if(n==null)return;nodes.add(n);for(int i=0;i<n.getChildCount();i++)collectUi(n.getChild(i),nodes);}
  private void shell(String command)throws Exception{try(android.os.ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(command);java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[1024];while(in.read(b)!=-1){}}}
  private void capture(String name)throws Exception{CountDownLatch painted=new CountDownLatch(1);runOnMainSync(()->MainActivity.current.web.postVisualStateCallback(SystemClock.uptimeMillis(),new WebView.VisualStateCallback(){@Override public void onComplete(long id){painted.countDown();}}));painted.await(3,TimeUnit.SECONDS);Bitmap image=getUiAutomation().takeScreenshot();if(image!=null)try(FileOutputStream f=new FileOutputStream(new File(out,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,f);}try{writeUi(name+".json",metrics().toString(2));}catch(Exception ignored){}}
  private void writeUi(String name,String text)throws Exception{try(FileOutputStream f=new FileOutputStream(new File(out,name))){f.write(text.getBytes(StandardCharsets.UTF_8));}}
  private static void require(boolean condition,String message)throws Exception{if(!condition)throw new Exception(message);}
}
