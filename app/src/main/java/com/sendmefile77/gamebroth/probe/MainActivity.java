package com.sendmefile77.gamebroth.probe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Two online games with separate origin storage and system document pickers. */
public class MainActivity extends Activity {
  public static volatile MainActivity current;
  public WebView web;
  private TextView status;
  static final String BROTH="https://perchance.org/game-broth-veyre";
  static final String GAME="https://perchance.org/mnoengine";
  static final String TEXT="https://perchance.org/ai-text-plugin-tester";
  static final String IMAGE="https://perchance.org/text-to-image-with-user-input-example";
  private static final String ART_ORIGIN="https://95f868a04fcef565bd634a26cec6e170.perchance.org";
  private static final Set<String> PAGES=Set.of(BROTH,GAME,TEXT,IMAGE);
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
    String last=getPreferences(MODE_PRIVATE).getString("selectedGame",BROTH);selectedGame=GAME.equals(last)?GAME:BROTH;
    LinearLayout toolbar=new LinearLayout(this);toolbar.setPadding(8,2,8,2);
    Spinner selector=new Spinner(this);selector.setId(0x6002);selector.setContentDescription("Выбрать игру");
    ArrayAdapter<String> items=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"Ржавый Фаллос","MNO Engine"});items.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);selector.setAdapter(items);selector.setSelection(GAME.equals(selectedGame)?1:0);
    toolbar.addView(selector,new LinearLayout.LayoutParams(0,dp(48),1));
    Button browser=new Button(this);browser.setText("↗");browser.setTextSize(20);browser.setContentDescription("Открыть текущую игру в браузере");toolbar.addView(browser,new LinearLayout.LayoutParams(dp(56),dp(48)));root.addView(toolbar);
    status=new TextView(this);status.setTextColor(Color.LTGRAY);status.setTextSize(11);status.setPadding(12,2,12,4);root.addView(status);
    web=new WebView(this);web.setId(0x6001);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setMediaPlaybackRequiresUserGesture(true);s.setBuiltInZoomControls(false);
    CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
    web.setWebChromeClient(new WebChromeClient(){
      @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
        if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;
        try{Intent intent=params.createIntent();intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,PICK_FILE);}catch(Exception e){fileCallback.onReceiveValue(null);fileCallback=null;showStatus("Не удалось открыть выбор файла");}return true;
      }
    });
    if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)){
      WebViewCompat.addWebMessageListener(web,"GameBrothFiles",Set.of(ART_ORIGIN),(view,message,sourceOrigin,isMainFrame,reply)->{
        String currentUrl=web.getUrl();if(currentUrl==null||!BROTH.equals(currentUrl.split("[?#]",2)[0])||!ART_ORIGIN.equals(sourceOrigin.toString())||pendingExport!=null)return;
        try{
          String raw=message.getData();if(raw==null||raw.length()>105000000)throw new Exception("Слишком большое сохранение");
          JSONObject request=new JSONObject(raw);String name=request.getString("name"),text=request.getString("text");
          if(!name.matches("game-broth-day-[0-9]+\\.json"))throw new Exception("Некорректное имя файла");
          String format=new JSONObject(text).getString("format");if(!format.startsWith("game-broth-export/"))throw new Exception("Неизвестный формат");
          pendingExport=text.getBytes(StandardCharsets.UTF_8);
          Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(intent,SAVE_FILE);
        }catch(Exception e){pendingExport=null;showStatus("Ошибка экспорта: "+e.getMessage());}
      });
    }
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
        Uri u=request.getUrl();String h=u.getHost();if("https".equals(u.getScheme())&&h!=null&&(h.equals("perchance.org")||h.endsWith(".perchance.org")))return false;
        if(request.isForMainFrame()){try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){showStatus("Не удалось открыть ссылку");}return true;}return false;
      }
      @Override public void onPageFinished(WebView view,String url){status.setVisibility(View.GONE);CookieManager.getInstance().flush();}
      @Override public void onReceivedError(WebView view,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())showStatus("Ошибка загрузки: "+e.getDescription()+". Попробуйте открыть игру в браузере кнопкой ↗.");}
    });
    root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);root.requestApplyInsets();
    selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
      @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){String page=position==1?GAME:BROTH;if(!page.equals(selectedGame))navigate(page);}
      @Override public void onNothingSelected(AdapterView<?> parent){}
    });
    browser.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(selectedGame)));}catch(Exception e){showStatus("Браузер не найден");}});
    String page=getIntent().getStringExtra("page");if(saved==null||web.restoreState(saved)==null)navigate(page==null?selectedGame:page);
  }
  private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
  private void showStatus(String text){status.setText(text);status.setVisibility(View.VISIBLE);}
  public void navigate(String page){
    if(!PAGES.contains(page))throw new IllegalArgumentException("Unsupported game page");
    if(page.equals(BROTH)||page.equals(GAME)){selectedGame=page;getPreferences(MODE_PRIVATE).edit().putString("selectedGame",page).apply();}
    showStatus("Загрузка игры…");web.loadUrl(page);
  }
  @Override protected void onActivityResult(int code,int result,Intent data){
    super.onActivityResult(code,result,data);
    if(code==PICK_FILE&&fileCallback!=null){fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));fileCallback=null;}
    if(code==SAVE_FILE){byte[] bytes=pendingExport;pendingExport=null;if(result==RESULT_OK&&data!=null&&data.getData()!=null&&bytes!=null){Uri uri=data.getData();new Thread(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new Exception("Нет доступа к файлу");out.write(bytes);runOnUiThread(()->showStatus("Сохранение записано"));}catch(Exception e){runOnUiThread(()->showStatus("Не удалось записать сохранение"));}}).start();}}
  }
  @Override protected void onSaveInstanceState(Bundle out){web.saveState(out);CookieManager.getInstance().flush();super.onSaveInstanceState(out);}
  @Override protected void onPause(){CookieManager.getInstance().flush();super.onPause();}
  @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
  @Override protected void onDestroy(){if(fileCallback!=null)fileCallback.onReceiveValue(null);if(current==this)current=null;web.destroy();super.onDestroy();}
}
