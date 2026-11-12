package com.sendmefile77.gamebroth.probe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.text.InputType;
import android.view.inputmethod.InputMethodManager;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/** Native reader chrome; presentation overrides exist only inside this APK. */
public class MainActivity extends Activity {
  private static Set<String> set(String... values){return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));}
  public static volatile MainActivity current;
  public WebView web;
  private TextView status,title,day;
  private LinearLayout creationPanel;
  private TextView creationLabel;
  private Button menu;
  private GameLibrary library;
  private String selectedId,pendingName,pendingSource,pendingCreationUrl,pendingPhase="idle",pendingToken="",pendingDetail="";
  private Button creationButton;
  private boolean libraryReadable=true;
  private boolean compact,toolsOpen,uiSupported;
  private int textZoom,pendingBack,backSequence;
  private final Map<String,JavaScriptReplyProxy> uiFrames=new HashMap<>();
  static final String BROTH="https://perchance.org/game-broth-veyre";
  static final String GAME="https://perchance.org/mnoengine";
  static final String FANTA="https://perchance.org/n89crmz16e";
  static final String NURSERY="https://perchance.org/0oobcp6o2a";
  static final String ALCHEMY="https://perchance.org/8kwa4xkn39";
  static final String LEGACY_COMING_SOON="https://appassets.androidplatform.net/assets/coming-soon.html";
  static final String COMING_SOON="https://perchance.org/j4o3qot7f6";
  static final String SECOND_PLACEHOLDER="https://perchance.org/1x315uqdpf";
  static final String NURSERY_ORIGIN="https://187f8df9978dc03e9b4030aed545ed9a.perchance.org";
  static final String ALCHEMY_ORIGIN="https://5df1b204e9f4b8a13dc8abcbc1fed0e6.perchance.org";
  static final String TEXT="https://perchance.org/ai-text-plugin-tester";
  static final String IMAGE="https://perchance.org/text-to-image-with-user-input-example";
  static final String ART_ORIGIN="https://95f868a04fcef565bd634a26cec6e170.perchance.org";
  static final String FANTA_ORIGIN="https://42c8e742df686040c4bbc145d38e02fc.perchance.org";
  private static final String[] HUB_URLS={BROTH,GAME,FANTA,NURSERY,ALCHEMY,COMING_SOON,SECOND_PLACEHOLDER};
  private static final String[] HUB_NAMES={"Ржавый Фаллос","MNO Engine","FANTA · Старый Свет","Питомник","Алхимия образов","Заглушка 1","Заглушка 2"};
  private static final Set<String> HUB_GAMES=set(BROTH,GAME,FANTA,NURSERY,ALCHEMY,COMING_SOON,SECOND_PLACEHOLDER);
  private static final Set<String> PAGES=set(BROTH,GAME,FANTA,NURSERY,ALCHEMY,COMING_SOON,SECOND_PLACEHOLDER,TEXT,IMAGE);
  private static final int PICK_FILE=41,SAVE_FILE=42;
  private String selectedGame=BROTH;
  private ValueCallback<Uri[]> fileCallback;
  private byte[] pendingExport;

  @Override public void onCreate(Bundle saved){
    super.onCreate(saved);current=this;
    LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(22,24,27));
    if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
    root.setOnApplyWindowInsetsListener((view,insets)->{
      int l,t,r,b;
      if(Build.VERSION.SDK_INT>=30){Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()),ime=insets.getInsets(WindowInsets.Type.ime());l=bars.left;t=bars.top;r=bars.right;b=Math.max(bars.bottom,ime.bottom);}
      else{l=insets.getSystemWindowInsetLeft();t=insets.getSystemWindowInsetTop();r=insets.getSystemWindowInsetRight();b=insets.getSystemWindowInsetBottom();}
      view.setPadding(l,t,r,b);return insets;
    });
    loadLibrary();loadCreation();
    compact=getPreferences(MODE_PRIVATE).getBoolean("compact",true);textZoom=getPreferences(MODE_PRIVATE).getInt("textZoom",100);
    LinearLayout toolbar=new LinearLayout(this);toolbar.setId(R.id.game_toolbar);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(4),0,dp(10),0);
    menu=new Button(this);menu.setId(R.id.game_menu);menu.setText("☰");menu.setTextSize(18);menu.setPadding(0,0,0,0);menu.setContentDescription("Меню игры");toolbar.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48)));
    title=new TextView(this);title.setId(R.id.game_title);title.setTextColor(Color.rgb(240,232,220));title.setTextSize(14);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(dp(7),0,dp(6),0);toolbar.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));title.setGravity(Gravity.CENTER_VERTICAL);
    day=new TextView(this);day.setId(R.id.game_day);day.setTextSize(12);day.setTextColor(Color.rgb(219,166,108));day.setSingleLine(true);day.setText("Онлайн");toolbar.addView(day);root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(48)));
    creationPanel=new LinearLayout(this);creationPanel.setOrientation(LinearLayout.VERTICAL);creationPanel.setPadding(dp(12),dp(6),dp(12),dp(4));creationPanel.setBackgroundColor(Color.rgb(46,39,30));
    creationLabel=new TextView(this);creationLabel.setTextColor(Color.rgb(255,227,185));creationLabel.setTextSize(13);creationPanel.addView(creationLabel);
    LinearLayout creationActions=new LinearLayout(this);
    creationButton=new Button(this);creationButton.setId(R.id.library_finish);creationButton.setOnClickListener(v->creationPrimary());creationActions.addView(creationButton,new LinearLayout.LayoutParams(0,dp(48),1));
    Button helpCreation=new Button(this);helpCreation.setText("Как?");helpCreation.setContentDescription("Как сохранить новую игру");helpCreation.setOnClickListener(v->creationHelp());creationActions.addView(helpCreation,new LinearLayout.LayoutParams(dp(64),dp(48)));
    Button cancelCreation=new Button(this);cancelCreation.setText("✕");cancelCreation.setContentDescription("Закрыть создание игры");cancelCreation.setOnClickListener(v->confirmCancelCreation());creationActions.addView(cancelCreation,new LinearLayout.LayoutParams(dp(48),dp(48)));
    creationPanel.addView(creationActions);root.addView(creationPanel);updateCreationPanel();
    status=new TextView(this);status.setId(R.id.game_status);status.setTextColor(Color.LTGRAY);status.setTextSize(12);status.setPadding(dp(12),dp(3),dp(12),dp(3));status.setVisibility(View.GONE);root.addView(status);
    web=new WebView(this);web.setId(R.id.game_web);web.setBackgroundColor(Color.rgb(20,23,25));WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setMediaPlaybackRequiresUserGesture(true);s.setBuiltInZoomControls(false);s.setTextZoom(textZoom);
    CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
    web.setWebChromeClient(new WebChromeClient(){
      @Override public boolean onJsAlert(WebView view,String url,String message,android.webkit.JsResult result){
        if(pendingName!=null&&"running".equals(pendingPhase)&&url!=null&&url.startsWith("https://perchance.org/")){
          pendingPhase="paused";pendingDetail=message==null?"Perchance показал предупреждение":message.substring(0,Math.min(message.length(),240));saveCreation();updateCreationPanel();
        }
        return false;
      }
      @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
        if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;
        try{Intent intent=params.createIntent();intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,PICK_FILE);}catch(Exception e){fileCallback.onReceiveValue(null);fileCallback=null;showStatus("Не удалось открыть выбор файла");}return true;
      }
    });
    if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)){
      WebViewCompat.addWebMessageListener(web,"GameBrothCreation",set("https://perchance.org"),(view,message,origin,isMainFrame,reply)->{
        if(!isMainFrame||pendingName==null||!"https://perchance.org".equals(origin.toString()))return;
        try{
          String raw=message.getData();if(raw==null||raw.length()>1024)return;JSONObject m=new JSONObject(raw);String type=m.optString("type");
          if("state".equals(type)&&pendingToken.equals(m.optString("token"))){
            String phase=m.optString("phase");if(set("running","login","paused").contains(phase)){pendingPhase=phase;pendingDetail=m.optString("detail");saveCreation();updateCreationPanel();}
          }else if("created".equals(type)&&pendingToken.equals(m.optString("token"))&&set("running","login").contains(pendingPhase)){
            rememberCreationUrl(m.optString("url"));if(pendingCreationUrl!=null)addCreatedGame();
          }else if(type.isEmpty()&&!set("loading","running","login").contains(pendingPhase))rememberCreationUrl(m.optString("url"));
        }catch(Exception ignored){}
      });
      WebViewCompat.addWebMessageListener(web,"GameBrothFiles",set(ART_ORIGIN,FANTA_ORIGIN,NURSERY_ORIGIN,ALCHEMY_ORIGIN),(view,message,sourceOrigin,isMainFrame,reply)->{
        String currentUrl=web.getUrl();if(currentUrl==null||isMainFrame||pendingExport!=null)return;
        String page=currentUrl.split("[?#]",2)[0];
        boolean broth=BROTH.equals(page)&&ART_ORIGIN.equals(sourceOrigin.toString());
        boolean fanta=FANTA.equals(page)&&FANTA_ORIGIN.equals(sourceOrigin.toString());
        boolean nursery=NURSERY.equals(page)&&NURSERY_ORIGIN.equals(sourceOrigin.toString()),alchemy=ALCHEMY.equals(page)&&ALCHEMY_ORIGIN.equals(sourceOrigin.toString());if(!broth&&!fanta&&!nursery&&!alchemy)return;
        try{
          String raw=message.getData();if(raw==null||raw.length()>105000000)throw new Exception("Слишком большое сохранение");
          JSONObject request=new JSONObject(raw);String name=request.getString("name"),text=request.getString("text");
          String pattern=nursery?"broth-nursery-save\\.json":alchemy?"broth-alchemy-save\\.json":fanta?"fanta-whfb-turn-[0-9]+\\.json":"game-broth-day-[0-9]+\\.json";
          if(!name.matches(pattern))throw new Exception("Некорректное имя файла");
          String format=new JSONObject(text).getString("format");
          boolean valid=nursery?"broth-nursery/1".equals(format):alchemy?"broth-alchemy/1".equals(format):fanta?"fanta-whfb-save/1".equals(format):set("game-broth-export/1","game-broth-export/2").contains(format);
          if(!valid)throw new Exception("Неизвестный формат");
          pendingExport=text.getBytes(StandardCharsets.UTF_8);
          Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(intent,SAVE_FILE);
        }catch(Exception e){pendingExport=null;showStatus("Ошибка экспорта: "+e.getMessage());}
      });
      WebViewCompat.addWebMessageListener(web,"GameBrothUi",set("https://perchance.org",ART_ORIGIN,FANTA_ORIGIN,NURSERY_ORIGIN,ALCHEMY_ORIGIN),(view,message,origin,isMainFrame,reply)->{
        String currentUrl=web.getUrl();if(currentUrl==null)return;String canonical=currentUrl.split("[?#]",2)[0];if(!HUB_GAMES.contains(canonical))return;
        boolean shell="https://perchance.org".equals(origin.toString())&&isMainFrame;
        boolean game=!isMainFrame&&(ART_ORIGIN.equals(origin.toString())&&BROTH.equals(canonical)||FANTA_ORIGIN.equals(origin.toString())&&FANTA.equals(canonical)||NURSERY_ORIGIN.equals(origin.toString())&&NURSERY.equals(canonical)||ALCHEMY_ORIGIN.equals(origin.toString())&&ALCHEMY.equals(canonical));if(!shell&&!game)return;
        try{
          String raw=message.getData();if(raw==null||raw.length()>500)return;JSONObject m=new JSONObject(raw);String type=m.optString("type");
          if("ready".equals(type)){uiFrames.put(game?"game":"shell",reply);sendPresentation(reply);}
          else if(game&&"hud".equals(type)){String label=m.optString("label");if(label.length()<=32)day.setText(label);}
          else if(game&&"tools".equals(type)){toolsOpen=m.optBoolean("open",false);}
          else if(game&&"back".equals(type)&&pendingBack!=0&&m.optInt("token")==pendingBack){pendingBack=0;if(!m.optBoolean("handled"))defaultBack();}
        }catch(Exception ignored){}
      });
    }
    uiSupported=WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)&&WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER);
    if(uiSupported){try{
      WebViewCompat.addDocumentStartJavaScript(web,readAsset("creation-link.js"),set("https://perchance.org"));
      String js=readAsset("compact-ui.js").replace("__GB_CONFIG__",new JSONObject().put("compact",compact).put("version",BuildConfig.VERSION_NAME).toString()).replace("__GB_CSS__",JSONObject.quote(readAsset("compact-ui.css")));
      WebViewCompat.addDocumentStartJavaScript(web,js,set("https://perchance.org",ART_ORIGIN,FANTA_ORIGIN,NURSERY_ORIGIN,ALCHEMY_ORIGIN));
    }catch(Exception e){uiSupported=false;}}
    web.setWebViewClient(createWebViewClient());
    root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);root.requestApplyInsets();menu.setOnClickListener(v->showMenu());updateTitle();
    String page=getIntent().getStringExtra("page");if(LEGACY_COMING_SOON.equals(page))page=COMING_SOON;
    if(saved==null||web.restoreState(saved)==null){if(page!=null&&isAllowedPage(page))navigate(page);else if(pendingName!=null)openCreationEditor();else navigate(selectedGame);}
    else if(LEGACY_COMING_SOON.equals(web.getUrl()))navigate(COMING_SOON);
    if(!libraryReadable)showStatus("Не удалось прочитать библиотеку. Исходный список сохранён; изменения отключены.");
  }
  protected WebViewClient createWebViewClient(){return new WebViewClient(){
    @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
      Uri u=request.getUrl();String h=u.getHost();if("https".equals(u.getScheme())&&h!=null&&(h.equals("perchance.org")||h.endsWith(".perchance.org")))return false;
      if(request.isForMainFrame()){try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){showStatus("Не удалось открыть ссылку");}return true;}return false;
    }
    @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){
      uiFrames.clear();pendingBack=0;toolsOpen=false;day.setText("Онлайн");
      String page=url==null?"":url.split("[?#]",2)[0];
      GameLibrary.Entry entry=library.byUrl(page);
      if(entry!=null&&pendingName==null)selectEntry(entry);
    }
    @Override public void onPageFinished(WebView view,String url){status.setVisibility(View.GONE);CookieManager.getInstance().flush();if(pendingName!=null&&"loading".equals(pendingPhase))startCreationOperation();}
    @Override public void onReceivedError(WebView view,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())showStatus("Ошибка загрузки: "+e.getDescription()+". Открыть браузер можно из меню ☰.");}
  };}
  private String readAsset(String name)throws Exception{try(java.io.InputStream in=getAssets().open(name)){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toString(StandardCharsets.UTF_8.name());}}
  private int gameIndex(){for(int i=0;i<library.entries().size();i++)if(library.entries().get(i).id.equals(selectedId))return i;return -1;}
  private void updateTitle(){GameLibrary.Entry e=library.byId(selectedId);title.setText(e==null?"Game Broth":e.name);day.setVisibility(pendingName==null&&compact&&(BROTH.equals(selectedGame)||FANTA.equals(selectedGame)||NURSERY.equals(selectedGame)||ALCHEMY.equals(selectedGame))?View.VISIBLE:View.GONE);}
  private void sendPresentation(JavaScriptReplyProxy reply){try{reply.postMessage(new JSONObject().put("type","config").put("compact",compact).put("tools",toolsOpen).toString());}catch(Exception ignored){}}
  private void applyPresentation(){for(JavaScriptReplyProxy reply:uiFrames.values())sendPresentation(reply);getPreferences(MODE_PRIVATE).edit().putBoolean("compact",compact).putInt("textZoom",textZoom).apply();web.getSettings().setTextZoom(textZoom);updateTitle();}
  private void showMenu(){
    PopupMenu popup=new PopupMenu(this,menu);popup.getMenu().add(0,1,0,"Выбрать игру");
    popup.getMenu().add(0,9,1,"Добавить игру").setEnabled(libraryReadable);
    popup.getMenu().add(0,10,2,"Переименовать игру").setEnabled(libraryReadable&&library.byId(selectedId)!=null);
    popup.getMenu().add(0,11,3,"Создать копию игры").setEnabled(libraryReadable&&library.byId(selectedId)!=null&&pendingName==null);
    popup.getMenu().add(0,12,4,"Управление играми").setEnabled(libraryReadable);
    popup.getMenu().add(0,2,5,"Увеличить текст");popup.getMenu().add(0,3,6,"Уменьшить текст");popup.getMenu().add(0,4,7,compact?"Обычный вид":"Компактный вид").setEnabled(uiSupported&&HUB_GAMES.contains(selectedGame)&&!COMING_SOON.equals(selectedGame)&&!SECOND_PLACEHOLDER.equals(selectedGame));
    popup.getMenu().add(0,5,4,toolsOpen?"Скрыть панель ИИ":"Панель ИИ и сохранения").setEnabled(uiSupported&&compact&&BROTH.equals(selectedGame));popup.getMenu().add(0,6,5,"Обновить страницу");popup.getMenu().add(0,7,6,"Открыть в браузере");
    popup.getMenu().add(0,8,7,"О приложении");
    popup.setOnMenuItemClickListener(item->{switch(item.getItemId()){
      case 1:chooseGame(false);break;
      case 9:addGameMenu();break;
      case 10:renameGame(library.byId(selectedId));break;
      case 11:requestCreation(library.byId(selectedId));break;
      case 12:chooseGame(true);break;
      case 2:textZoom=Math.min(150,textZoom+10);applyPresentation();break;
      case 3:textZoom=Math.max(90,textZoom-10);applyPresentation();break;
      case 4:compact=!compact;toolsOpen=false;applyPresentation();break;
      case 5:toolsOpen=!toolsOpen;applyPresentation();break;
      case 6:web.reload();break;
      case 7:try{String url=web.getUrl();startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url==null?selectedGame:url)));}catch(Exception e){showStatus("Браузер не найден");}break;
      case 8:new AlertDialog.Builder(this).setIcon(R.mipmap.ic_launcher).setTitle("Game Broth Hub · "+BuildConfig.VERSION_NAME).setMessage("Библиотека игр: добавление ссылок, пустых страниц и копий Perchance, свои названия.\n\nПереименование меняет подпись только в APK. Код копирует сам Perchance; игровые сохранения переносятся отдельно через экспорт и импорт в игре.\n\nПрогресс хранится на этом устройстве. Генерация требует интернета.").setPositiveButton("Понятно",null).show();break;
    }return true;});popup.show();
  }
  private void loadLibrary(){
    String raw=getPreferences(MODE_PRIVATE).getString(GameLibrary.KEY,null);
    try{library=raw==null?GameLibrary.defaults(HUB_URLS,HUB_NAMES):GameLibrary.parse(raw);}
    catch(Exception e){libraryReadable=false;library=GameLibrary.defaults(HUB_URLS,HUB_NAMES);}
    String last=getPreferences(MODE_PRIVATE).getString("selectedGame",BROTH);if(LEGACY_COMING_SOON.equals(last))last=COMING_SOON;
    GameLibrary.Entry entry=library.byUrl(last);
    if(entry==null)entry=library.byId(getPreferences(MODE_PRIVATE).getString("selectedEntryId",null));
    if(entry==null&&!library.entries().isEmpty())entry=library.entries().get(0);
    selectedId=entry==null?null:entry.id;selectedGame=entry==null?BROTH:entry.url;
  }
  private boolean persistLibrary(String before){
    if(libraryReadable&&getPreferences(MODE_PRIVATE).edit().putString(GameLibrary.KEY,library.serialize()).commit())return true;
    try{library=GameLibrary.parse(before);}catch(Exception ignored){}
    showStatus("Не удалось записать библиотеку. Попробуйте ещё раз.");return false;
  }
  private void selectEntry(GameLibrary.Entry entry){
    selectedId=entry.id;selectedGame=entry.url;
    getPreferences(MODE_PRIVATE).edit().putString("selectedGame",entry.url).putString("selectedEntryId",entry.id).apply();
    if(title!=null)updateTitle();
  }
  private boolean isAllowedPage(String page){return PAGES.contains(page)||library.byUrl(page)!=null;}
  private LinearLayout form(String explanation){
    LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(8),dp(20),0);
    TextView note=new TextView(this);note.setText(explanation);note.setTextSize(14);body.addView(note);return body;
  }
  private EditText field(LinearLayout body,int id,String hint,String value,boolean url){
    EditText input=new EditText(this);input.setId(id);input.setSingleLine(true);input.setHint(hint);input.setContentDescription(hint);
    input.setInputType(url?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    if(!url)input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
    input.setText(value);body.addView(input,new LinearLayout.LayoutParams(-1,dp(56)));return input;
  }
  private interface NameAction{void run(String name);}
  private void nameDialog(String heading,String note,String value,NameAction action){
    LinearLayout body=form(note);EditText input=field(body,R.id.library_name,"Название игры",value,false);
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle(heading).setView(body).setPositiveButton("Готово",null).setNegativeButton("Отмена",null).create();
    dialog.setOnShowListener(v->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{try{action.run(GameLibrary.cleanName(input.getText().toString()));dialog.dismiss();}catch(IllegalArgumentException e){input.setError(e.getMessage());}});input.requestFocus();});dialog.show();
  }
  private void chooseGame(boolean manage){
    java.util.List<GameLibrary.Entry> snapshot=new java.util.ArrayList<>(library.entries());String[] names=new String[snapshot.size()];for(int i=0;i<names.length;i++)names[i]=snapshot.get(i).name;
    new AlertDialog.Builder(this).setTitle(manage?"Управление играми":"Выбрать игру").setSingleChoiceItems(names,gameIndex(),(d,n)->{
      d.dismiss();GameLibrary.Entry e=snapshot.get(n);if(manage)gameActions(e);else if(!e.url.equals(selectedGame)||!e.url.equals(web.getUrl()))navigate(e.url);
    }).setPositiveButton("＋ Добавить",(d,w)->addGameMenu()).setNegativeButton("Закрыть",null).show();
  }
  private void gameActions(GameLibrary.Entry entry){
    new AlertDialog.Builder(this).setTitle(entry.name).setItems(new String[]{"Открыть","Переименовать","Создать копию Perchance","Скопировать ссылку","Убрать из набора"},(d,n)->{
      if(n==0)navigate(entry.url);
      else if(n==1)renameGame(entry);
      else if(n==2)requestCreation(entry);
      else if(n==3){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText(entry.name,entry.url));showStatus("Ссылка скопирована");}
      else removeGame(entry);
    }).setNegativeButton("Закрыть",null).show();
  }
  private void renameGame(GameLibrary.Entry entry){
    if(entry==null||!libraryReadable)return;
    nameDialog("Переименовать игру","Название в APK. Адрес и название страницы Perchance сохраняются.",entry.name,name->{
      String before=library.serialize();library.rename(entry.id,name);if(persistLibrary(before)){updateTitle();showStatus("Название сохранено");}
    });
  }
  private void removeGame(GameLibrary.Entry entry){
    if(!libraryReadable)return;
    if(library.entries().size()<2){showStatus("Сначала добавьте другую игру");return;}
    new AlertDialog.Builder(this).setTitle("Убрать «"+entry.name+"»?").setMessage("Страница Perchance и её сохранения останутся. Чтобы вернуть игру, добавьте эту ссылку:\n\n"+entry.url).setNegativeButton("Отмена",null).setPositiveButton("Убрать из набора",(d,w)->{
      String before=library.serialize();library.remove(entry.id);if(persistLibrary(before)){if(entry.id.equals(selectedId))navigate(library.entries().get(0).url);showStatus("Игра убрана из набора");}
    }).show();
  }
  private void addGameMenu(){
    if(!libraryReadable)return;
    new AlertDialog.Builder(this).setTitle("Добавить игру").setItems(new String[]{"Новая пустая игра Perchance","Добавить по ссылке"},(d,n)->{if(n==0)requestCreation(null);else linkDialog(false);}).setNegativeButton("Отмена",null).show();
  }
  private void linkDialog(boolean created){
    String candidate=created?creationCandidate():"";
    LinearLayout body=form(created?(candidate.isEmpty()?"Ссылка новой игры ещё не найдена. В Perchance выберите settings → duplicate it. После успеха нажмите «Добавить в набор» снова или вставьте ссылку копии вручную.":"Найдена ссылка новой игры. Проверьте название и добавьте её в набор."):"Вставьте ссылку на сохранённую игру Perchance.");
    EditText name=field(body,R.id.library_name,"Название игры",created?pendingName:"",false);
    EditText url=field(body,R.id.library_url,"Ссылка Perchance",candidate,true);
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle(created?"Добавить созданную игру":"Добавить по ссылке").setView(body).setPositiveButton("Добавить",null).setNegativeButton("Отмена",null).create();
    dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{
      String label;
      try{label=GameLibrary.cleanName(name.getText().toString());}catch(IllegalArgumentException e){name.setError(e.getMessage());return;}
      try{
        String address=GameLibrary.normalizeUrl(url.getText().toString());
        if(created&&address.equals(pendingSource))throw new IllegalArgumentException("Это ссылка на оригинал. Создайте отдельную копию через settings → duplicate it и сохраните её.");
        String before=library.serialize();GameLibrary.Entry entry=library.add(label,address);
        if(!persistLibrary(before))return;
        if(created)clearCreation();dialog.dismiss();navigate(entry.url);showStatus("Игра добавлена в набор");
      }catch(IllegalArgumentException e){url.setError(e.getMessage());}
    }));dialog.show();
  }
  private void requestCreation(GameLibrary.Entry source){
    if(!libraryReadable)return;
    if(pendingName!=null){creationHelp();return;}
    nameDialog(source==null?"Новая пустая игра":"Копия игры",source==null?"APK откроет пустой шаблон и запустит его сохранение в Perchance. При необходимости войдите в аккаунт.":"APK запустит штатное копирование «"+source.name+"» в Perchance и добавит результат в набор. Сохранения игры переносятся отдельно.",source==null?"Новая игра":copyName(source.name),name->{
      String original=source==null?"":source.url;
      try{
        String token=java.util.UUID.randomUUID().toString();String raw=new JSONObject().put("name",name).put("source",original).put("phase","loading").put("token",token).toString();
        if(!getPreferences(MODE_PRIVATE).edit().putString("pendingCreationV1",raw).commit())throw new IllegalArgumentException("Не удалось сохранить черновик");
        pendingName=name;pendingSource=original;pendingCreationUrl=null;pendingPhase="loading";pendingToken=token;pendingDetail="";updateCreationPanel();updateTitle();openCreationEditor();
      }catch(org.json.JSONException e){throw new IllegalStateException(e);}
    });
  }
  private String copyName(String name){String suffix=" · копия";return name.substring(0,Math.min(name.length(),80-suffix.length()))+suffix;}
  private void loadCreation(){
    String raw=getPreferences(MODE_PRIVATE).getString("pendingCreationV1",null);if(raw==null)return;
    try{JSONObject p=new JSONObject(raw);pendingName=GameLibrary.cleanName(p.getString("name"));String source=p.getString("source");pendingSource=source.isEmpty()?"":GameLibrary.normalizeUrl(source);pendingCreationUrl=validCreationUrl(p.optString("url"));pendingToken=p.optString("token",java.util.UUID.randomUUID().toString());pendingPhase=p.optString("phase","idle");if(set("loading","running","login").contains(pendingPhase))pendingPhase="paused";}
    catch(Exception e){pendingName=null;pendingSource=null;pendingCreationUrl=null;}
  }
  private void updateCreationPanel(){
    creationPanel.setVisibility(pendingName==null?View.GONE:View.VISIBLE);
    if(pendingName!=null){
      boolean busy=set("loading","running").contains(pendingPhase);creationButton.setEnabled(!busy);
      creationButton.setText(pendingCreationUrl!=null?"Добавить в набор":busy?"Создаём…":"login".equals(pendingPhase)?"Продолжить":"paused".equals(pendingPhase)?"Проверить результат":pendingSource.isEmpty()?"Создать пустую игру":"Создать копию");
      String detail=pendingCreationUrl!=null?"Ссылка получена":!pendingDetail.isEmpty()?pendingDetail:busy?"Ожидаем Perchance…":"paused".equals(pendingPhase)?"Создание прервано. Проверьте результат перед повтором.":"Нажмите кнопку, чтобы запустить создание в Perchance";
      creationLabel.setText((pendingSource.isEmpty()?"Новая игра: ":"Копия: ")+pendingName+"\n"+detail);
    }
  }
  private String validCreationUrl(String value){try{String url=GameLibrary.normalizeUrl(value);return url.equals(pendingSource)||library.byUrl(url)!=null?null:url;}catch(IllegalArgumentException e){return null;}}
  private String creationCandidate(){if(pendingCreationUrl!=null)return pendingCreationUrl;String candidate=validCreationUrl(web.getUrl());return candidate==null?"":candidate;}
  private void rememberCreationUrl(String value){
    if(pendingName==null)return;String candidate=validCreationUrl(value);if(candidate==null||candidate.equals(pendingCreationUrl))return;
    try{
      String raw=creationRecord().put("url",candidate).toString();
      if(!getPreferences(MODE_PRIVATE).edit().putString("pendingCreationV1",raw).commit()){showStatus("Не удалось сохранить ссылку копии");return;}
      pendingCreationUrl=candidate;updateCreationPanel();
    }catch(org.json.JSONException ignored){}
  }
  private void openCreationEditor(){web.loadUrl(pendingCreationUrl!=null?pendingCreationUrl+"#edit":pendingSource.isEmpty()?"https://perchance.org/blank#edit":pendingSource+"#edit");}
  private JSONObject creationRecord()throws org.json.JSONException{return new JSONObject().put("name",pendingName).put("source",pendingSource).put("url",pendingCreationUrl).put("phase",pendingPhase).put("token",pendingToken);}
  private boolean saveCreation(){try{return getPreferences(MODE_PRIVATE).edit().putString("pendingCreationV1",creationRecord().toString()).commit();}catch(Exception e){return false;}}
  private void startCreationOperation(){
    if(pendingName==null||pendingCreationUrl!=null)return;
    String page=web.getUrl();if(page==null||!page.startsWith("https://perchance.org/"))return;
    String source=pendingSource.isEmpty()?"https://perchance.org/blank":pendingSource;
    if(!source.equals(page.split("[?#]",2)[0])){pendingPhase="loading";saveCreation();updateCreationPanel();web.loadUrl(source+"#edit");return;}
    pendingPhase="running";pendingDetail="Подготавливаем Perchance…";
    if(!saveCreation()){pendingPhase="paused";pendingDetail="Не удалось сохранить состояние создания";updateCreationPanel();return;}
    updateCreationPanel();
    try{JSONObject config=new JSONObject().put("token",pendingToken).put("source",source);
      web.evaluateJavascript("if(!window.GameBrothCreationDriver){"+readAsset("creation-link.js")+"}window.GameBrothCreationDriver.start("+config+");",null);
    }catch(Exception e){pendingPhase="paused";pendingDetail="Не удалось открыть создание. Попробуйте ещё раз.";saveCreation();updateCreationPanel();}
  }
  private void creationPrimary(){
    if(pendingName==null)return;if(pendingCreationUrl!=null){addCreatedGame();return;}
    if("paused".equals(pendingPhase)){
      finishCreation();return;
    }
    if(!set("loading","running").contains(pendingPhase))startCreationOperation();
  }
  private void addCreatedGame(){
    if(pendingName==null||pendingCreationUrl==null)return;
    String before=library.serialize();try{GameLibrary.Entry entry=library.add(pendingName,pendingCreationUrl);if(!persistLibrary(before))return;clearCreation();navigate(entry.url);showStatus("Новая игра добавлена в набор");}catch(IllegalArgumentException e){showStatus(e.getMessage());}
  }
  private void creationHelp(){
    if(pendingName==null)return;
    new AlertDialog.Builder(this).setTitle("Создание «"+pendingName+"»").setItems(new String[]{"Вставить ссылку уже созданной игры","Открыть редактор","Повторить создание"},(d,n)->{
      if(n==0)linkDialog(true);else if(n==1)openCreationEditor();else new AlertDialog.Builder(this).setTitle("Повторить создание?").setMessage("Если Perchance уже создал страницу, повтор создаст ещё одну. Можно вставить ссылку существующей копии.").setNegativeButton("Отмена",null).setPositiveButton("Создать ещё раз",(a,b)->{pendingToken=java.util.UUID.randomUUID().toString();pendingPhase="loading";pendingCreationUrl=null;pendingDetail="";saveCreation();updateCreationPanel();openCreationEditor();}).show();
    }).setNegativeButton("Закрыть",null).show();
  }
  private void finishCreation(){
    if(pendingName==null)return;String creationName=pendingName,creationSource=pendingSource;
    web.evaluateJavascript("(function(){var a=document.querySelector('[data-ref=\"duplicateGeneratorStatus\"] a[href]');return {copyUrl:a?a.href:'',pageUrl:location.href};})()",raw->{
      if(!creationName.equals(pendingName)||!creationSource.equals(pendingSource)||isFinishing()||isDestroyed())return;
      try{JSONObject result=new JSONObject(raw);String copied=validCreationUrl(result.optString("copyUrl"));rememberCreationUrl(copied!=null?copied:result.optString("pageUrl"));}catch(Exception ignored){}
      if(pendingCreationUrl!=null)addCreatedGame();else new AlertDialog.Builder(this).setTitle("Результат пока не найден").setMessage("Создание могло завершиться в Perchance. Вставьте ссылку копии или откройте действия создания, чтобы повторить операцию.").setPositiveButton("Вставить ссылку",(d,w)->linkDialog(true)).setNegativeButton("Закрыть",null).show();
    });
  }
  private void clearCreation(){getPreferences(MODE_PRIVATE).edit().remove("pendingCreationV1").apply();pendingName=null;pendingSource=null;pendingCreationUrl=null;pendingPhase="idle";pendingToken="";pendingDetail="";updateCreationPanel();updateTitle();}
  private void confirmCancelCreation(){
    new AlertDialog.Builder(this).setTitle("Закрыть создание игры?").setMessage("Несохранённые изменения в редакторе могут потеряться. Уже сохранённая страница Perchance останется; её можно добавить по ссылке.").setNegativeButton("Продолжить",null).setPositiveButton("Закрыть создание",(d,w)->{clearCreation();navigate(selectedGame);}).show();
  }
  private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
  private void showStatus(String text){status.setText(text);status.setVisibility(View.VISIBLE);status.removeCallbacks(hideStatus);status.postDelayed(hideStatus,8000);}
  private final Runnable hideStatus=()->{if(status!=null)status.setVisibility(View.GONE);};
  public void navigate(String page){
    if(!isAllowedPage(page))throw new IllegalArgumentException("Unsupported game page");
    GameLibrary.Entry entry=library.byUrl(page);if(entry!=null)selectEntry(entry);updateTitle();showStatus("Загрузка игры…");web.loadUrl(page);
  }
  @Override protected void onActivityResult(int code,int result,Intent data){
    super.onActivityResult(code,result,data);
    if(code==PICK_FILE&&fileCallback!=null){fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));fileCallback=null;}
    if(code==SAVE_FILE){byte[] bytes=pendingExport;pendingExport=null;if(result==RESULT_OK&&data!=null&&data.getData()!=null&&bytes!=null){Uri uri=data.getData();new Thread(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new Exception("Нет доступа к файлу");out.write(bytes);runOnUiThread(()->showStatus("Сохранение записано"));}catch(Exception e){runOnUiThread(()->showStatus("Не удалось записать сохранение"));}}).start();}}
  }
  @Override protected void onSaveInstanceState(Bundle out){web.saveState(out);CookieManager.getInstance().flush();super.onSaveInstanceState(out);}
  @Override protected void onPause(){CookieManager.getInstance().flush();super.onPause();}
  private void defaultBack(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
  @Override public void onBackPressed(){
    JavaScriptReplyProxy reply=uiFrames.get("game");if(reply==null){defaultBack();return;}final int token=++backSequence;pendingBack=token;
    try{reply.postMessage(new JSONObject().put("type","back").put("token",token).toString());web.postDelayed(()->{if(pendingBack==token){pendingBack=0;defaultBack();}},350);}catch(Exception e){pendingBack=0;defaultBack();}
  }
  @Override protected void onDestroy(){if(fileCallback!=null)fileCallback.onReceiveValue(null);if(current==this)current=null;status.removeCallbacks(hideStatus);web.destroy();super.onDestroy();}
}
