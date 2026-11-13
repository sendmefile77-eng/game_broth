package com.sendmefile77.gamebroth.probe;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.media.AudioAttributes;

/** Protects the existing WebView host while a user-started generation is pending.
 * Does not recreate pages, retry requests, or promise survival after process death. */
public final class GenerationService extends Service {
  static final String WAIT="generation-wait",READY="generation-ready";
  public static volatile boolean running;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private PowerManager.WakeLock wake;
  private final Runnable limit=()->{stopSelf();};
  static void channels(android.content.Context context){NotificationManager manager=context.getSystemService(NotificationManager.class);NotificationChannel wait=new NotificationChannel(WAIT,"Ожидание генерации",NotificationManager.IMPORTANCE_LOW);wait.setDescription("Сохранение сеанса Perchance при сворачивании");wait.setSound(null,null);manager.createNotificationChannel(wait);NotificationChannel ready=new NotificationChannel(READY,"Готовность сцены",NotificationManager.IMPORTANCE_DEFAULT);ready.enableVibration(true);ready.setDescription("Результат текста или иллюстраций получен");manager.createNotificationChannel(ready);}
  static PendingIntent open(android.content.Context context){Intent i=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);return PendingIntent.getActivity(context,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
  static void completed(android.content.Context context,String game,boolean failed){
    if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
    channels(context);Notification n=new Notification.Builder(context,READY).setSmallIcon(R.drawable.ic_session).setContentTitle(failed?"Запрос завершился с ошибкой":"Сцена готова").setContentText(game).setContentIntent(open(context)).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();context.getSystemService(NotificationManager.class).notify(152,n);
  }
  @Override public void onCreate(){super.onCreate();channels(this);}
  @Override public int onStartCommand(Intent intent,int flags,int id){
    Notification n=new Notification.Builder(this,WAIT).setSmallIcon(R.drawable.ic_session).setContentTitle("Ожидаем результат Perchance").setContentText("Можно свернуть приложение. Нажмите, чтобы вернуться в игру.").setContentIntent(open(this)).setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();
    if(Build.VERSION.SDK_INT>=29)startForeground(151,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);else startForeground(151,n);
    getSystemService(NotificationManager.class).cancel(152);running=true;
    if(wake==null){wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":generation");wake.setReferenceCounted(false);wake.acquire(15*60*1000L);}
    handler.removeCallbacks(limit);handler.postDelayed(limit,15*60*1000L);
    return START_NOT_STICKY;
  }
  @Override public void onTimeout(int startId,int fgsType){stopSelf();}
  @Override public void onTaskRemoved(Intent rootIntent){stopSelf();}
  @Override public void onDestroy(){running=false;handler.removeCallbacks(limit);if(wake!=null&&wake.isHeld())wake.release();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
  @Override public IBinder onBind(Intent intent){return null;}
}
