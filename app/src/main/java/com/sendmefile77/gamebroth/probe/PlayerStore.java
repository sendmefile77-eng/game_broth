package com.sendmefile77.gamebroth.probe;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import android.util.Base64OutputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Local reading archive, independent of the games' own saves. Access on one IO executor. */
final class PlayerStore extends SQLiteOpenHelper {
  static final long MAX_STORAGE=250L*1024*1024;
  static final int MAX_IMAGE=8*1024*1024;
  private final File mediaDir;
  PlayerStore(Context context){super(context,"player-archive.db",null,1);mediaDir=new File(context.getFilesDir(),"player-media");if(!mediaDir.isDirectory()&&!mediaDir.mkdirs())throw new IllegalStateException("Не удалось открыть папку галереи");}
  @Override public void onCreate(SQLiteDatabase db){
    db.execSQL("CREATE TABLE scenes(id TEXT PRIMARY KEY,game TEXT NOT NULL,title TEXT NOT NULL,body TEXT NOT NULL,images TEXT NOT NULL,created INTEGER NOT NULL,updated INTEGER NOT NULL)");
    db.execSQL("CREATE INDEX scene_game ON scenes(game,created)");
    db.execSQL("CREATE TABLE media(id TEXT PRIMARY KEY,mime TEXT NOT NULL,bytes INTEGER NOT NULL,created INTEGER NOT NULL)");
    db.execSQL("CREATE TABLE album(game TEXT NOT NULL,media TEXT NOT NULL,caption TEXT NOT NULL,PRIMARY KEY(game,media))");
  }
  @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int version){throw new IllegalStateException("Неизвестная версия архива");}
  static final class Scene {String id,game,title,body;JSONArray images;long created;}
  static final class Picture {String id,mime,caption;long bytes;}
  File imageFile(String id){if(id==null||!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Неверный идентификатор картинки");return new File(mediaDir,id);}
  void saveImage(String game,JSONObject data)throws Exception{
    String id=data.getString("id"),url=data.getString("data"),caption=PlayerPolicy.clean(data.optString("caption"),180);imageFile(id);
    int comma=url.indexOf(',');if(comma<0||comma>40)throw new IllegalArgumentException("Неверный формат картинки");String header=url.substring(0,comma),mime;
    switch(header){case "data:image/png;base64":mime="image/png";break;case "data:image/jpeg;base64":mime="image/jpeg";break;case "data:image/webp;base64":mime="image/webp";break;default:throw new IllegalArgumentException("Поддерживаются PNG, JPEG и WebP");}
    if(url.length()-comma>MAX_IMAGE*4L/3+16)throw new IllegalArgumentException("Картинка больше 8 МБ");
    byte[] bytes=Base64.decode(url.substring(comma+1),Base64.DEFAULT);if(bytes.length==0||bytes.length>MAX_IMAGE||!PlayerPolicy.hash(bytes).equals(id))throw new IllegalArgumentException("Повреждена картинка");
    android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
    if(options.outWidth<=0||options.outHeight<=0||options.outWidth>16384||options.outHeight>16384)throw new IllegalArgumentException("Неверный размер картинки");
    SQLiteDatabase db=getWritableDatabase();boolean exists=false;try(Cursor c=db.rawQuery("SELECT id FROM media WHERE id=?",new String[]{id})){exists=c.moveToFirst();}
    if(!exists){if(storageBytes()+bytes.length>MAX_STORAGE)throw new IllegalStateException("Галерея заполнена (250 МБ). Удалите ненужные картинки в панели APK.");File temp=new File(mediaDir,id+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);out.getFD().sync();}if(!temp.renameTo(imageFile(id))){temp.delete();throw new IOException("Не удалось записать картинку");}
      ContentValues v=new ContentValues();v.put("id",id);v.put("mime",mime);v.put("bytes",bytes.length);v.put("created",System.currentTimeMillis());db.insertOrThrow("media",null,v);
    }
    ContentValues link=new ContentValues();link.put("game",game);link.put("media",id);link.put("caption",caption);db.insertWithOnConflict("album",null,link,SQLiteDatabase.CONFLICT_IGNORE);
  }
  void saveScene(String game,JSONObject data)throws Exception{
    String body=PlayerPolicy.clean(data.optString("text"),60000),title=PlayerPolicy.clean(data.optString("title"),160);if(body.length()<8)return;
    String id=PlayerPolicy.hash(game+"\n"+body);JSONArray requested=data.optJSONArray("images"),images=new JSONArray();SQLiteDatabase db=getWritableDatabase();
    if(requested!=null)for(int i=0;i<Math.min(12,requested.length());i++){String ref=requested.optString(i);try(Cursor c=db.rawQuery("SELECT media FROM album WHERE game=? AND media=?",new String[]{game,ref})){if(c.moveToFirst()&&imageFile(ref).isFile())images.put(ref);}}
    long now=System.currentTimeMillis(),created=now;try(Cursor c=db.rawQuery("SELECT created FROM scenes WHERE id=?",new String[]{id})){if(c.moveToFirst())created=c.getLong(0);}
    ContentValues row=new ContentValues();row.put("id",id);row.put("game",game);row.put("title",title.isEmpty()?"Сцена":title);row.put("body",body);row.put("images",images.toString());row.put("created",created);row.put("updated",now);
    db.insertWithOnConflict("scenes",null,row,SQLiteDatabase.CONFLICT_REPLACE);
  }
  List<Scene> scenes(String game)throws Exception{return queryScenes(game,true);}
  List<Scene> summaries(String game)throws Exception{return queryScenes(game,false);}
  Scene scene(String id)throws Exception{try(Cursor c=getReadableDatabase().query("scenes",new String[]{"id","game","title","body","images","created"},"id=?",new String[]{id},null,null,null)){if(!c.moveToFirst())return null;Scene s=new Scene();s.id=c.getString(0);s.game=c.getString(1);s.title=c.getString(2);s.body=c.getString(3);s.images=new JSONArray(c.getString(4));s.created=c.getLong(5);return s;}}
  private List<Scene> queryScenes(String game,boolean body)throws Exception{List<Scene> result=new ArrayList<>();try(Cursor c=getReadableDatabase().query("scenes",new String[]{"id","game","title",body?"body":"'' AS body","images","created"},"game=?",new String[]{game},null,null,"created DESC")){while(c.moveToNext()){Scene s=new Scene();s.id=c.getString(0);s.game=c.getString(1);s.title=c.getString(2);s.body=c.getString(3);s.images=new JSONArray(c.getString(4));s.created=c.getLong(5);result.add(s);}}return result;}
  List<Picture> pictures(String game){List<Picture> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT m.id,m.mime,a.caption,m.bytes FROM media m JOIN album a ON m.id=a.media WHERE a.game=? ORDER BY m.created DESC",new String[]{game})){while(c.moveToNext()){Picture p=new Picture();p.id=c.getString(0);p.mime=c.getString(1);p.caption=c.getString(2);p.bytes=c.getLong(3);result.add(p);}}return result;}
  long storageBytes(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(bytes),0) FROM media",null)){c.moveToFirst();return c.getLong(0);}}
  void deleteScene(String id){getWritableDatabase().delete("scenes","id=?",new String[]{id});}
  void deleteImage(String game,String id)throws Exception{SQLiteDatabase db=getWritableDatabase();db.beginTransaction();boolean orphan;try{db.delete("album","game=? AND media=?",new String[]{game,id});for(Scene s:summaries(game)){JSONArray next=new JSONArray();for(int i=0;i<s.images.length();i++)if(!id.equals(s.images.optString(i)))next.put(s.images.getString(i));ContentValues v=new ContentValues();v.put("images",next.toString());db.update("scenes",v,"id=?",new String[]{s.id});}try(Cursor c=db.rawQuery("SELECT 1 FROM album WHERE media=? LIMIT 1",new String[]{id})){orphan=!c.moveToFirst();}if(orphan)db.delete("media","id=?",new String[]{id});db.setTransactionSuccessful();}finally{db.endTransaction();}if(orphan)imageFile(id).delete();}
  static String html(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
  void exportArchive(String game,OutputStream out)throws Exception{
    List<Scene> list=summaries(game);java.util.Collections.reverse(list);write(out,"<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Архив игры</title><style>body{max-width:760px;margin:32px auto;padding:0 18px;background:#15191c;color:#eee;font:18px/1.65 system-ui}article{margin:48px 0}p{white-space:pre-wrap}img{max-width:100%;height:auto;border-radius:12px}small{color:#aaa}</style><h1>Архив игры</h1><small>"+html(game)+"</small>");
    for(Scene summary:list){Scene s=scene(summary.id);if(s==null)continue;write(out,"<article><h2>"+html(s.title)+"</h2><p>"+html(s.body)+"</p>");for(int i=0;i<s.images.length();i++){String id=s.images.getString(i),mime="image/png";try(Cursor c=getReadableDatabase().rawQuery("SELECT mime FROM media WHERE id=?",new String[]{id})){if(!c.moveToFirst())continue;mime=c.getString(0);}File f=imageFile(id);if(!f.isFile())continue;write(out,"<img alt='' src='data:"+mime+";base64,");OutputStream nonClosing=new FilterOutputStream(out){@Override public void close()throws IOException{flush();}};try(Base64OutputStream encoded=new Base64OutputStream(nonClosing,Base64.NO_WRAP);InputStream input=new FileInputStream(f)){byte[] b=new byte[16384];int n;while((n=input.read(b))!=-1)encoded.write(b,0,n);}write(out,"'>");}write(out,"</article>");}
  }
  private static void write(OutputStream out,String text)throws IOException{out.write(text.getBytes(StandardCharsets.UTF_8));}
}
