package com.sendmefile77.gamebroth.probe;

import android.app.Activity;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.webkit.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Native companion bridge. Only the shell-selected output frame can contribute data. */
final class PlayerController {
  final MainActivity activity;
  final WebView web;
  final PlayerStore store;
  final ExecutorService io=Executors.newSingleThreadExecutor();
  final android.content.SharedPreferences prefs;
  final PlayerPanel panel;
  private final Handler main=new Handler(Looper.getMainLooper());
  private JavaScriptReplyProxy frame;
  private String game,origin;
  private volatile String token=UUID.randomUUID().toString();
  private volatile boolean destroyed;
  private boolean resumed=true,pending,manual;
  private String pendingCommand,pendingCommandId;
  public volatile int completionCount;
  public volatile String lastError="";
  private long generation;
  private final Runnable manualLimit=()->{manual=false;updateService();say("Время ручного ожидания истекло. Повторный запрос не отправлялся.");};
  PlayerController(MainActivity activity,WebView web){this.activity=activity;this.web=web;prefs=activity.getSharedPreferences("player",Context.MODE_PRIVATE);store=new PlayerStore(activity.getApplicationContext());panel=new PlayerPanel(this);applyOrientation();}
  void install()throws Exception{
    if(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)||!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))return;
    Set<String> origins=new HashSet<>(Arrays.asList("https://perchance.org","https://*.perchance.org"));
    WebViewCompat.addWebMessageListener(web,"GameBrothPlayer",origins,(view,message,source,isMain,reply)->{
      String raw=message.getData();if(raw==null||raw.length()>12*1024*1024)return;
      String sourceOrigin=source.toString(),current=PlayerPolicy.page(web.getUrl());if(current==null||!activity.playerPage(current))return;
      if(raw.length()>200000){if(isMain||!sourceOrigin.equals(origin)||frame==null)return;String expected=token,page=game;io.execute(()->processLarge(raw,expected,page));return;}
      try{JSONObject m=new JSONObject(raw);String type=m.optString("type");
        if(isMain&&"https://perchance.org".equals(sourceOrigin)&&"frame".equals(type)){
          String candidate=PlayerPolicy.frameOrigin(current,m.optString("url"));if(candidate==null)return;
          if(!current.equals(game)||!candidate.equals(origin)){reset(current);origin=candidate;}return;
        }
        if(isMain||!current.equals(game)||!sourceOrigin.equals(origin))return;
        if("hello".equals(type)){frame=reply;send(new JSONObject().put("type","welcome").put("token",token).put("archive",enabled("archive")).put("gallery",enabled("gallery")));return;}
        if(!token.equals(m.optString("token")))return;
        if("work".equals(type)){
          boolean next=m.optBoolean("busy");if(next){if(!pending)generation++;pending=true;updateService();}
          else if(pending){pending=false;manual=false;main.removeCallbacks(manualLimit);long run=generation;String page=game;boolean failed=m.optBoolean("failed");io.execute(()->main.post(()->{if(destroyed||!token.equals(m.optString("token"))||generation!=run)return;updateService();completionCount++;if(enabled("signal")){if(!resumed)GenerationService.completed(activity,page,failed);else{web.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);say(failed?"Запрос завершился с ошибкой":"Сцена готова");}}}));}
        }else if("scene".equals(type)){String page=game,expected=token;io.execute(()->{if(!destroyed&&expected.equals(token)&&enabled("archive"))try{store.saveScene(page,m);}catch(Exception e){error(e);}});}
        else if("media".equals(type)){String expected=token,page=game;io.execute(()->processMedia(m,expected,page));}
        else if("commandAck".equals(type)){if(!m.optString("requestId").equals(pendingCommandId))return;String text=pendingCommand;pendingCommand=null;pendingCommandId=null;if(!m.optBoolean("inserted")&&text!=null)copy(text,"Поле действия недоступно. Команда скопирована.");else say("Команда вставлена. Проверьте её перед отправкой.");}
        else if("captureDone".equals(type))say(m.optBoolean("found")?"Сцена передана в архив":"Сцену не удалось распознать. Откройте историю игры.");
      }catch(Exception e){lastError="Не удалось прочитать сообщение игры";}
    });
    String script;try(InputStream in=activity.getAssets().open("player.js")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);script=bytes.toString(StandardCharsets.UTF_8.name());}
    WebViewCompat.addDocumentStartJavaScript(web,script,origins);
  }
  private void processLarge(String raw,String expected,String page){try{JSONObject m=new JSONObject(raw);if("media".equals(m.optString("type")))processMedia(m,expected,page);}catch(Exception e){error(e);}}
  private void processMedia(JSONObject m,String expected,String page){if(destroyed||!expected.equals(token)||!expected.equals(m.optString("token"))||(!enabled("gallery")&&!enabled("archive")))return;try{store.saveImage(page,m);}catch(Exception e){error(e);}}
  private void error(Exception e){lastError=e.getMessage()==null?"Ошибка архива":e.getMessage();main.post(()->{if(!destroyed)say(lastError);});}
  void reset(String page){token=UUID.randomUUID().toString();frame=null;origin=null;game=PlayerPolicy.page(page);pending=false;manual=false;pendingCommand=null;generation++;main.removeCallbacks(manualLimit);activity.stopService(new Intent(activity,GenerationService.class));}
  String game(){String current=PlayerPolicy.page(web.getUrl());return current==null?"https://perchance.org/unknown":current;}
  boolean connected(){return frame!=null;}
  boolean busy(){return pending||manual;}
  boolean enabled(String key){return prefs.getBoolean(key,true);}
  void setting(String key,boolean value){prefs.edit().putBoolean(key,value).apply();if(key.equals("background"))updateService();try{send(new JSONObject().put("type","settings").put("archive",enabled("archive")).put("gallery",enabled("gallery")));}catch(Exception ignored){}}
  void resume(){resumed=true;web.onResume();web.resumeTimers();if(busy())updateService();}
  void pause(){resumed=false;if(busy())updateService();}
  private void updateService(){
    if(destroyed)return;
    if(enabled("background")&&busy())try{if(!GenerationService.running)activity.startForegroundService(new Intent(activity,GenerationService.class));}catch(Exception e){lastError="Android не разрешил фоновое ожидание. Вернитесь в игру и включите его снова.";say(lastError);}
    else activity.stopService(new Intent(activity,GenerationService.class));
  }
  void holdManually(){manual=!manual;main.removeCallbacks(manualLimit);if(manual)main.postDelayed(manualLimit,15*60*1000L);updateService();say(manual?"Сеанс оставлен открытым на время ожидания (до 15 минут)":"Ручное ожидание выключено");}
  void capture(){try{if(frame==null){say("Адаптер ещё не подключён к игре");return;}send(new JSONObject().put("type","capture"));}catch(Exception e){say("Не удалось запросить сцену");}}
  void insert(String text){if(frame==null){copy(text,"Команда скопирована — вставьте её в игру");return;}pendingCommand=text;String requestId=UUID.randomUUID().toString();pendingCommandId=requestId;String saved=token;try{send(new JSONObject().put("type","command").put("text",text).put("requestId",requestId));main.postDelayed(()->{if(saved.equals(token)&&requestId.equals(pendingCommandId)){pendingCommand=null;copy(text,"Ответ не получен. Команда скопирована.");}},2500);}catch(Exception e){copy(text,"Команда скопирована");}}
  private void copy(String text,String note){((android.content.ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Команда игры",text));say(note);}
  private void send(JSONObject message){if(frame!=null)try{message.put("token",token);frame.postMessage(message.toString());}catch(Exception ignored){frame=null;}}
  JSONArray commands(){try{return new JSONArray(prefs.getString("commands:"+game(),"[{\"name\":\"Осмотреться\",\"text\":\"Осмотреться и отметить важные детали вокруг.\"},{\"name\":\"Снаряжение\",\"text\":\"Проверить своё снаряжение и доступные предметы.\"},{\"name\":\"Текущая цель\",\"text\":\"Напомни мою текущую цель и известные зацепки.\"}]"));}catch(Exception e){return new JSONArray();}}
  void commands(JSONArray value){prefs.edit().putString("commands:"+game(),value.toString()).apply();}
  void orientation(int value){prefs.edit().putInt("orientation",value).apply();applyOrientation();}
  void applyOrientation(){int value=prefs.getInt("orientation",0);activity.setRequestedOrientation(value==1?ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:value==2?ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);}
  void requestNotifications(){if(Build.VERSION.SDK_INT>=33&&activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},77);else say("Уведомления разрешены");}
  void say(String text){if(!destroyed)Toast.makeText(activity,text,Toast.LENGTH_SHORT).show();}
  void close(){destroyed=true;main.removeCallbacksAndMessages(null);activity.stopService(new Intent(activity,GenerationService.class));panel.close();io.execute(store::close);io.shutdown();}
}
