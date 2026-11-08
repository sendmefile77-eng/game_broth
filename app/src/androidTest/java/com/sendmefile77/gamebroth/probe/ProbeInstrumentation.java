package com.sendmefile77.gamebroth.probe;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Live, real-network tests through Android accessibility.
 * No mocked AI responses, no CAPTCHA solving, no secret endpoints. */
public class ProbeInstrumentation extends Instrumentation {
  private File out;
  private final JSONArray results=new JSONArray();
  private final String hero="ProbeHero";
  @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
  @Override public void onStart(){
    out=new File(getTargetContext().getExternalFilesDir(null),"probe");
    out.mkdirs();
    try {
      AccessibilityServiceInfo info=getUiAutomation().getServiceInfo();
      info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
      getUiAutomation().setServiceInfo(info);
      JSONObject env=new JSONObject();
      env.put("android",android.os.Build.VERSION.RELEASE);
      env.put("sdk",android.os.Build.VERSION.SDK_INT);
      android.content.pm.PackageInfo provider=android.webkit.WebView.getCurrentWebViewPackage();
      env.put("webview",provider==null?"unknown":provider.packageName+" "+provider.versionName);
      write("environment.json",env.toString(2));
      startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      phase("russian_text",()->textTest());
      phase("generated_image",()->imageTest());
      phase("mno_game_and_restore",()->gameTest());
    }catch(Throwable e){ record("harness","FAIL",e.toString()); }
    try {write("results.json",new JSONObject().put("results",results).toString(2));}catch(Exception e){}
    Bundle b=new Bundle();b.putString("stream",results.toString()+"\n");
    finish(Activity.RESULT_OK,b);
  }
  interface Task{void run()throws Exception;}
  void phase(String name,Task task){
    try{task.run();record(name,"PASS","Live Android WebView test completed");}
    catch(Throwable e){record(name,"FAIL",e.toString());}
    try{snapshot(name);screenshot(name);}catch(Exception ignored){}
  }
  void record(String name,String status,String detail){
    try{results.put(new JSONObject().put("name",name).put("status",status).put("detail",detail));
      android.util.Log.i("PerchanceProbe",name+" "+status+" "+detail);}
    catch(Exception ignored){}
  }
  void navigate(String url)throws Exception{
    runOnMainSync(()->MainActivity.current.navigate(url));
    SystemClock.sleep(2000);
  }
  List<AccessibilityNodeInfo> nodes(){
    List<AccessibilityNodeInfo> list=new ArrayList<>();
    collect(getUiAutomation().getRootInActiveWindow(),list);return list;
  }
  void collect(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> list){
    if(n==null)return;list.add(n);
    for(int i=0;i<n.getChildCount();i++)collect(n.getChild(i),list);
  }
  String label(AccessibilityNodeInfo n){
    return String.valueOf(n.getText()==null?"":n.getText())+" "+String.valueOf(n.getContentDescription()==null?"":n.getContentDescription());
  }
  String screen(){
    StringBuilder s=new StringBuilder();
    for(AccessibilityNodeInfo n:nodes())s.append(label(n)).append("\n");
    return s.toString();
  }
  void blockCheck(String s)throws Exception{
    String l=s.toLowerCase(Locale.ROOT);
    if(l.contains("verify you are human")||l.contains("checking your browser")||l.contains("just a moment")||l.contains("verify that you are human"))
      throw new Exception("BLOCKED_BY_HUMAN_VERIFICATION: no bypass or automatic challenge solving attempted");
  }
  AccessibilityNodeInfo waitLabel(String fragment,int seconds)throws Exception{
    long end=SystemClock.elapsedRealtime()+seconds*1000L;
    while(SystemClock.elapsedRealtime()<end){
      String all=screen();blockCheck(all);
      for(AccessibilityNodeInfo n:nodes())if(label(n).toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT))&&n.isVisibleToUser())return n;
      SystemClock.sleep(1200);
    }
    throw new Exception("Timed out waiting for visible control: "+fragment);
  }
  void click(AccessibilityNodeInfo n)throws Exception{
    AccessibilityNodeInfo c=n;
    while(c!=null){if(c.isClickable()&&c.performAction(AccessibilityNodeInfo.ACTION_CLICK)){SystemClock.sleep(800);return;}c=c.getParent();}
    throw new Exception("No clickable accessibility control: "+label(n));
  }
  void set(AccessibilityNodeInfo n,String value)throws Exception{
    Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
    if(!n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))throw new Exception("Cannot set text: "+label(n));
  }
  List<AccessibilityNodeInfo> editable(){
    List<AccessibilityNodeInfo> r=new ArrayList<>();for(AccessibilityNodeInfo n:nodes())if(n.isEditable()&&n.isVisibleToUser())r.add(n);return r;
  }
  AccessibilityNodeInfo byId(String id){
    for(AccessibilityNodeInfo n:nodes()){
      String resource=n.getViewIdResourceName();
      if(resource!=null&&(resource.equals(id)||resource.endsWith("/"+id)))return n;
    }return null;
  }
  void textTest()throws Exception{
    navigate(MainActivity.TEXT);
    waitLabel("Instruction",90);
    List<AccessibilityNodeInfo> fields=editable();
    if(fields.size()<2)throw new Exception("Tester instruction/output fields unavailable");
    String instruction="Напиши по-русски два предложения: герой подходит к старой башне на закате. Затем предложи три действия.";
    set(fields.get(0),instruction);
    click(waitLabel("generate",15));
    long end=SystemClock.elapsedRealtime()+150000;
    String answer="";
    while(SystemClock.elapsedRealtime()<end){
      blockCheck(screen());
      List<AccessibilityNodeInfo> current=editable();
      for(int i=1;i<current.size();i++){
        String value=String.valueOf(current.get(i).getText());
        if(value.length()>80&&value.matches("(?s).*[А-Яа-яЁё].*"))answer=value;
      }
      if(!answer.isEmpty())break;
      SystemClock.sleep(1500);
    }
    if(answer.isEmpty())throw new Exception("No generated Russian response within 150 seconds");
    write("russian-response.txt",answer);
  }
  void imageTest()throws Exception{
    navigate(MainActivity.IMAGE);
    waitLabel("Text-to-Image with User Input",90);
    List<AccessibilityNodeInfo> fields=editable();
    if(fields.isEmpty())throw new Exception("Image prompt input unavailable");
    set(fields.get(0),"ancient stone wizard tower at sunset, peaceful fantasy landscape, no people, digital painting");
    click(waitLabel("generate",15));
    long end=SystemClock.elapsedRealtime()+180000;
    while(SystemClock.elapsedRealtime()<end){
      String all=screen();blockCheck(all);
      if(all.contains("prompt=ancient stone wizard tower")&&all.contains("seed=")){
        write("image-accessibility.txt",all);return;
      }
      SystemClock.sleep(1500);
    }
    throw new Exception("No completed generated image within 180 seconds");
  }
  void gameTest()throws Exception{
    navigate(MainActivity.GAME);
    waitLabel("begin",90);
    click(waitLabel("begin",10));
    click(waitLabel("Use this world",30));
    waitLabel("FORGE YOUR HERO",30);
    AccessibilityNodeInfo name=byId("cc_name");
    List<AccessibilityNodeInfo> fields=editable();
    if(name==null && !fields.isEmpty())name=fields.get(0);
    if(name==null)throw new Exception("Hero name input unavailable");
    set(name,hero);
    AccessibilityNodeInfo extra=byId("cc_extra");
    if(extra!=null)set(extra,"Narrate in Russian. Peaceful exploration, no adult scenes.");
    click(waitLabel("Next",15));
    // Bio -> Avatar -> Stats -> Review. Do not generate an extra portrait.
    click(waitLabel("Next",15));
    click(waitLabel("Next",15));
    snapshot("mno-review");
    // Current labels are also archived if the engine changes and this assertion fails.
    AccessibilityNodeInfo start=null;
    for(AccessibilityNodeInfo n:nodes()){
      String l=label(n).toLowerCase(Locale.ROOT);
      if(n.isVisibleToUser()&&(l.contains("begin adventure")||l.contains("start adventure")||l.contains("begin your adventure")||l.contains("enter world"))){start=n;break;}
    }
    if(start==null)throw new Exception("Review start control unavailable; inspect mno-review accessibility evidence");
    click(start);
    waitLabel("Scene image",90);
    SystemClock.sleep(15000);
    snapshot("mno-before-restart");screenshot("mno-before-restart");
    // Restart the Activity; WebView storage is not cleared.
    runOnMainSync(()->{
      MainActivity old=MainActivity.current;
      Intent i=new Intent(old,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      old.finish();old.startActivity(i);
    });
    SystemClock.sleep(4000);
    waitLabel("adventure",90);
    boolean found=false;
    for(AccessibilityNodeInfo n:nodes()){
      if(label(n).toLowerCase(Locale.ROOT).contains("adventures")&&n.isVisibleToUser()){
        try{click(n);found=true;break;}catch(Exception ignored){}
      }
    }
    if(!found){
      // Mobile navigation is collapsed.
      for(AccessibilityNodeInfo n:nodes())if(label(n).toLowerCase(Locale.ROOT).contains("open menu")){
        click(n);SystemClock.sleep(500);click(waitLabel("adventures",15));found=true;break;
      }
    }
    waitLabel(hero,45);
    write("game-restore.txt","Created "+hero+" in MNO, restarted the Android Activity without clearing WebView storage, and found the saved adventure.");
  }
  void snapshot(String name)throws Exception{
    JSONArray a=new JSONArray();
    for(AccessibilityNodeInfo n:nodes()){
      Rect r=new Rect();n.getBoundsInScreen(r);
      a.put(new JSONObject().put("text",String.valueOf(n.getText())).put("description",String.valueOf(n.getContentDescription()))
        .put("id",String.valueOf(n.getViewIdResourceName())).put("class",String.valueOf(n.getClassName()))
        .put("editable",n.isEditable()).put("clickable",n.isClickable()).put("visible",n.isVisibleToUser()).put("bounds",r.toShortString()));
    }
    write(name+".json",a.toString(2));
  }
  void screenshot(String name)throws Exception{
    Bitmap b=getUiAutomation().takeScreenshot();
    if(b!=null)try(FileOutputStream f=new FileOutputStream(new File(out,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,f);}
  }
  void write(String name,String text)throws Exception{
    try(FileOutputStream f=new FileOutputStream(new File(out,name))){f.write(text.getBytes(StandardCharsets.UTF_8));}
  }
}
