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
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Native reader chrome; presentation overrides exist only inside this APK. */
public class MainActivity extends Activity {
  public static volatile MainActivity current;
  public WebView web;
  private TextView status,title,day;
  private Button menu;
  private boolean compact,toolsOpen,uiSupported;
  private int textZoom,pendingBack,backSequence;
  private final Map<String,JavaScriptReplyProxy> uiFrames=new HashMap<>();
  static final String BROTH="https://perchance.org/game-broth-veyre";
  static final String GAME="https://perchance.org/mnoengine";
  static final String FANTA="https://perchance.org/n89crmz16e";
  static final String NURSERY="https://perchance.org/0oobcp6o2a";
  static final String ALCHEMY="https://perchance.org/8kwa4xkn39";
  static final String NURSERY_ORIGIN="https://187f8df9978dc03e9b4030aed545ed9a.perchance.org";
  static final String ALCHEMY_ORIGIN="https://5df1b204e9f4b8a13dc8abcbc1fed0e6.perchance.org";
  static final String TEXT="https://perchance.org/ai-text-plugin-tester";
  static final String IMAGE="https://perchance.org/text-to-image-with-user-input-example";
  static final String ART_ORIGIN="https://95f868a04fcef565bd634a26cec6e170.perchance.org";
  static final String FANTA_ORIGIN="https://42c8e742df686040c4bbc145d38e02fc.perchance.org";
  private static final String[] HUB_URLS={BROTH,GAME,FANTA,NURSERY,ALCHEMY};
  private static final String[] HUB_NAMES={"Ржавый Фаллос","MNO Engine","FANTA · Старый Свет","Питомник","Алхимия образов"};
  private static final Set<String> HUB_GAMES=Set.of(BROTH,GAME,FANTA,NURSERY,ALCHEMY);
  private static final Set<String> PAGES=Set.of(BROTH,GAME,FANTA,NURSERY,ALCHEMY,TEXT,IMAGE);
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
    String last=getPreferences(MODE_PRIVATE).getString("selectedGame",BROTH);selectedGame=HUB_GAMES.contains(last)?last:BROTH;
    compact=getPreferences(MODE_PRIVATE).getBoolean("compact",true);textZoom=getPreferences(MODE_PRIVATE).getInt("textZoom",100);
    LinearLayout toolbar=new LinearLayout(this);toolbar.setId(0x6003);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(4),0,dp(10),0);
    menu=new Button(this);menu.setId(0x6002);menu.setText("☰");menu.setTextSize(18);menu.setPadding(0,0,0,0);menu.setContentDescription("Меню игры");toolbar.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48)));
    title=new TextView(this);title.setTextColor(Color.rgb(240,232,220));title.setTextSize(14);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(dp(7),0,dp(6),0);toolbar.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));title.setGravity(Gravity.CENTER_VERTICAL);
    day=new TextView(this);day.setId(0x6005);day.setTextSize(12);day.setTextColor(Color.rgb(219,166,108));day.setSingleLine(true);day.setText("Онлайн");toolbar.addView(day);root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(48)));
    status=new TextView(this);status.setId(0x6004);status.setTextColor(Color.LTGRAY);status.setTextSize(12);status.setPadding(dp(12),dp(3),dp(12),dp(3));status.setVisibility(View.GONE);root.addView(status);
    web=new WebView(this);web.setId(0x6001);web.setBackgroundColor(Color.rgb(20,23,25));WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setMediaPlaybackRequiresUserGesture(true);s.setBuiltInZoomControls(false);s.setTextZoom(textZoom);
    CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
    web.setWebChromeClient(new WebChromeClient(){
      @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
        if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;
        try{Intent intent=params.createIntent();intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,PICK_FILE);}catch(Exception e){fileCallback.onReceiveValue(null);fileCallback=null;showStatus("Не удалось открыть выбор файла");}return true;
      }
    });
    if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)){
      WebViewCompat.addWebMessageListener(web,"GameBrothFiles",Set.of(ART_ORIGIN,FANTA_ORIGIN,NURSERY_ORIGIN,ALCHEMY_ORIGIN),(view,message,sourceOrigin,isMainFrame,reply)->{
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
          boolean valid=nursery?"broth-nursery/1".equals(format):alchemy?"broth-alchemy/1".equals(format):fanta?"fanta-whfb-save/1".equals(format):Set.of("game-broth-export/1","game-broth-export/2").contains(format);
          if(!valid)throw new Exception("Неизвестный формат");
          pendingExport=text.getBytes(StandardCharsets.UTF_8);
          Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(intent,SAVE_FILE);
        }catch(Exception e){pendingExport=null;showStatus("Ошибка экспорта: "+e.getMessage());}
      });
      WebViewCompat.addWebMessageListener(web,"GameBrothUi",Set.of("https://perchance.org",ART_ORIGIN,FANTA_ORIGIN,NURSERY_ORIGIN,ALCHEMY_ORIGIN),(view,message,origin,isMainFrame,reply)->{
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
      String js=readAsset("compact-ui.js").replace("__GB_CONFIG__",new JSONObject().put("compact",compact).put("version","0.8").toString()).replace("__GB_CSS__",JSONObject.quote(readAsset("compact-ui.css")));
      WebViewCompat.addDocumentStartJavaScript(web,js,Set.of("https://perchance.org",ART_ORIGIN,FANTA_ORIGIN,NURSERY_ORIGIN,ALCHEMY_ORIGIN));
    }catch(Exception e){uiSupported=false;}}
    web.setWebViewClient(createWebViewClient());
    root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);root.requestApplyInsets();menu.setOnClickListener(v->showMenu());updateTitle();
    String page=getIntent().getStringExtra("page");if(saved==null||web.restoreState(saved)==null)navigate(page==null?selectedGame:page);
  }
  protected WebViewClient createWebViewClient(){return new WebViewClient(){
    @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
      Uri u=request.getUrl();String h=u.getHost();if("https".equals(u.getScheme())&&h!=null&&(h.equals("perchance.org")||h.endsWith(".perchance.org")))return false;
      if(request.isForMainFrame()){try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){showStatus("Не удалось открыть ссылку");}return true;}return false;
    }
    @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){uiFrames.clear();pendingBack=0;toolsOpen=false;day.setText("Онлайн");}
    @Override public void onPageFinished(WebView view,String url){status.setVisibility(View.GONE);CookieManager.getInstance().flush();}
    @Override public void onReceivedError(WebView view,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())showStatus("Ошибка загрузки: "+e.getDescription()+". Открыть браузер можно из меню ☰.");}
  };}
  private String readAsset(String name)throws Exception{try(java.io.InputStream in=getAssets().open(name)){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toString(StandardCharsets.UTF_8.name());}}
  private int gameIndex(){for(int i=0;i<HUB_URLS.length;i++)if(HUB_URLS[i].equals(selectedGame))return i;return 0;}
  private void updateTitle(){title.setText(HUB_NAMES[gameIndex()]);day.setVisibility(compact&&(BROTH.equals(selectedGame)||FANTA.equals(selectedGame)||NURSERY.equals(selectedGame)||ALCHEMY.equals(selectedGame))?View.VISIBLE:View.GONE);}
  private void sendPresentation(JavaScriptReplyProxy reply){try{reply.postMessage(new JSONObject().put("type","config").put("compact",compact).put("tools",toolsOpen).toString());}catch(Exception ignored){}}
  private void applyPresentation(){for(JavaScriptReplyProxy reply:uiFrames.values())sendPresentation(reply);getPreferences(MODE_PRIVATE).edit().putBoolean("compact",compact).putInt("textZoom",textZoom).apply();web.getSettings().setTextZoom(textZoom);updateTitle();}
  private void showMenu(){
    PopupMenu popup=new PopupMenu(this,menu);popup.getMenu().add(0,1,0,"Выбрать игру");popup.getMenu().add(0,2,1,"Увеличить текст");popup.getMenu().add(0,3,2,"Уменьшить текст");popup.getMenu().add(0,4,3,compact?"Обычный вид":"Компактный вид").setEnabled(uiSupported);
    popup.getMenu().add(0,5,4,toolsOpen?"Скрыть панель ИИ":"Панель ИИ и сохранения").setEnabled(uiSupported&&compact&&BROTH.equals(selectedGame));popup.getMenu().add(0,6,5,"Обновить страницу");popup.getMenu().add(0,7,6,"Открыть в браузере");
    popup.setOnMenuItemClickListener(item->{switch(item.getItemId()){
      case 1:new AlertDialog.Builder(this).setTitle("Выбрать игру").setSingleChoiceItems(HUB_NAMES,gameIndex(),(d,n)->{d.dismiss();String page=HUB_URLS[n];if(!page.equals(selectedGame))navigate(page);}).setNegativeButton("Отмена",null).show();break;
      case 2:textZoom=Math.min(150,textZoom+10);applyPresentation();break;
      case 3:textZoom=Math.max(90,textZoom-10);applyPresentation();break;
      case 4:compact=!compact;toolsOpen=false;applyPresentation();break;
      case 5:toolsOpen=!toolsOpen;applyPresentation();break;
      case 6:web.reload();break;
      case 7:try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(selectedGame)));}catch(Exception e){showStatus("Браузер не найден");}break;
    }return true;});popup.show();
  }
  private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
  private void showStatus(String text){status.setText(text);status.setVisibility(View.VISIBLE);status.removeCallbacks(hideStatus);status.postDelayed(hideStatus,8000);}
  private final Runnable hideStatus=()->{if(status!=null)status.setVisibility(View.GONE);};
  public void navigate(String page){
    if(!PAGES.contains(page))throw new IllegalArgumentException("Unsupported game page");
    if(HUB_GAMES.contains(page)){selectedGame=page;getPreferences(MODE_PRIVATE).edit().putString("selectedGame",page).apply();}updateTitle();showStatus("Загрузка игры…");web.loadUrl(page);
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
