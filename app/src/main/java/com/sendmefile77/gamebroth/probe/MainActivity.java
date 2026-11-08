package com.sendmefile77.gamebroth.probe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.view.WindowInsets;
import android.graphics.Insets;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Compatibility probe. All AI runs on the original Perchance pages.
 * This APK does not contain a translated MNO or an independent Perchance API. */
public class MainActivity extends Activity {
  public static volatile MainActivity current;
  public WebView web;
  private TextView status;
  static final String GAME = "https://perchance.org/mnoengine";
  static final String TEXT = "https://perchance.org/ai-text-plugin-tester";
  static final String IMAGE = "https://perchance.org/text-to-image-with-user-input-example";
  private static final Set<String> PAGES = Set.of(GAME, TEXT, IMAGE);

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    current = this;
    LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.rgb(22,24,30));
    if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
    root.setOnApplyWindowInsetsListener((view, insets) -> {
      int left, top, right, bottom;
      if (Build.VERSION.SDK_INT >= 30) {
        Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        Insets keyboard = insets.getInsets(WindowInsets.Type.ime());
        left = bars.left; top = bars.top; right = bars.right;
        bottom = Math.max(bars.bottom, keyboard.bottom);
      } else {
        left = insets.getSystemWindowInsetLeft(); top = insets.getSystemWindowInsetTop();
        right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom();
      }
      view.setPadding(left, top, right, bottom);
      return insets;
    });
    TextView title = new TextView(this);
    title.setText("Perchance · проверка");
    title.setTextColor(Color.WHITE); title.setTextSize(18); title.setPadding(20,16,20,8);
    root.addView(title);
    LinearLayout buttons = new LinearLayout(this);
    addButton(buttons,"Игра",GAME); addButton(buttons,"Текст",TEXT); addButton(buttons,"Картинка",IMAGE);
    root.addView(buttons);
    status = new TextView(this); status.setTextColor(Color.LTGRAY); status.setTextSize(11);
    status.setPadding(16,4,16,8); root.addView(status);
    web = new WebView(this); web.setId(0x6001);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
    s.setAllowFileAccess(false); s.setAllowContentAccess(false);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    s.setMediaPlaybackRequiresUserGesture(true);
    s.setBuiltInZoomControls(false);
    // No user-agent spoofing, security bypass, custom TLS trust or exposed JS bridge.
    CookieManager.getInstance().setAcceptCookie(true);
    CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
    web.setWebChromeClient(new WebChromeClient());
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
        Uri uri = r.getUrl(); String h = uri.getHost();
        if ("https".equals(uri.getScheme()) && h != null &&
            (h.equals("perchance.org") || h.endsWith(".perchance.org"))) return false;
        if (r.isForMainFrame()) { startActivity(new Intent(Intent.ACTION_VIEW,uri)); return true; }
        return false;
      }
      @Override public void onPageFinished(WebView v,String url){
        status.setText("Онлайн · " + url);
        CookieManager.getInstance().flush();
      }
      @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){
        if(r.isForMainFrame()) status.setText("Ошибка загрузки: "+e.getErrorCode()+" "+e.getDescription());
      }
    });
    root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
    setContentView(root);
    root.requestApplyInsets();
    String page = getIntent().getStringExtra("page");
    if(state==null || web.restoreState(state)==null) navigate(page==null?GAME:page);
  }

  private void addButton(LinearLayout row,String title,String url){
    Button b=new Button(this); b.setText(title); b.setTextSize(12);
    b.setOnClickListener(v->navigate(url));
    row.addView(b,new LinearLayout.LayoutParams(0,-2,1));
  }
  public void navigate(String page){
    if(!PAGES.contains(page)) throw new IllegalArgumentException("Unsupported probe page");
    status.setText("Загрузка Perchance…"); web.loadUrl(page);
  }
  @Override protected void onSaveInstanceState(Bundle out){
    web.saveState(out); CookieManager.getInstance().flush(); super.onSaveInstanceState(out);
  }
  @Override protected void onPause(){
    CookieManager.getInstance().flush(); super.onPause();
  }
  @Override public void onBackPressed(){ if(web.canGoBack())web.goBack();else super.onBackPressed(); }
  @Override protected void onDestroy(){
    if(current==this)current=null;
    web.destroy();super.onDestroy();
  }
}

