package com.sendmefile77.gamebroth.probe;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Local catalogue only. Perchance remains responsible for saving and copying source files. */
final class GameLibrary {
  static final String KEY="gameLibraryV1";
  static final class Entry {
    final String id,name,url;
    Entry(String id,String name,String url){this.id=id;this.name=name;this.url=url;}
  }
  private final ArrayList<Entry> entries=new ArrayList<>();
  static GameLibrary defaults(String[] urls,String[] names){
    GameLibrary library=new GameLibrary();
    for(int i=0;i<urls.length;i++)library.entries.add(new Entry("default-"+i,names[i],urls[i]));
    return library;
  }
  static GameLibrary parse(String json)throws Exception{
    JSONObject root=new JSONObject(json);
    if(root.getInt("version")!=1)throw new IllegalArgumentException("Неизвестная версия библиотеки");
    GameLibrary library=new GameLibrary();JSONArray rows=root.getJSONArray("games");
    for(int i=0;i<rows.length();i++){
      JSONObject row=rows.getJSONObject(i);String id=row.getString("id");
      if(!id.matches("[A-Za-z0-9-]{1,80}")||library.byId(id)!=null)throw new IllegalArgumentException("Повреждён список игр");
      String url=normalizeUrl(row.getString("url"));
      if(library.byUrl(url)!=null)throw new IllegalArgumentException("Повторная ссылка в библиотеке");
      library.entries.add(new Entry(id,cleanName(row.getString("name")),url));
    }
    return library;
  }
  String serialize(){
    try{
      JSONArray rows=new JSONArray();
      for(Entry e:entries)rows.put(new JSONObject().put("id",e.id).put("name",e.name).put("url",e.url));
      return new JSONObject().put("version",1).put("games",rows).toString();
    }catch(Exception e){throw new IllegalStateException("Не удалось сохранить список",e);}
  }
  List<Entry> entries(){return Collections.unmodifiableList(entries);}
  Entry byId(String id){for(Entry e:entries)if(e.id.equals(id))return e;return null;}
  Entry byUrl(String url){if(url==null)return null;for(Entry e:entries)if(e.url.equals(url))return e;return null;}
  Entry add(String name,String input){
    String url=normalizeUrl(input),label=cleanName(name);
    if(byUrl(url)!=null)throw new IllegalArgumentException("Эта игра уже есть в библиотеке");
    Entry entry=new Entry(UUID.randomUUID().toString(),label,url);entries.add(entry);return entry;
  }
  Entry rename(String id,String name){
    Entry old=byId(id);if(old==null)throw new IllegalArgumentException("Игра больше не в списке");
    Entry next=new Entry(id,cleanName(name),old.url);entries.set(entries.indexOf(old),next);return next;
  }
  void remove(String id){Entry old=byId(id);if(old!=null)entries.remove(old);}
  static String cleanName(String input){
    String name=input==null?"":input.replaceAll("[\\p{Cntrl}\\s]+"," ").trim();
    if(name.isEmpty())throw new IllegalArgumentException("Введите название игры");
    if(name.length()>80)throw new IllegalArgumentException("Название — до 80 символов");
    return name;
  }
  static String normalizeUrl(String input){
    try{
      String value=input==null?"":input.trim();
      if(value.startsWith("perchance.org/")||value.startsWith("www.perchance.org/"))value="https://"+value;
      URI u=new URI(value);String host=u.getHost(),path=u.getRawPath();
      if(!"https".equalsIgnoreCase(u.getScheme())||host==null||
          !("perchance.org".equalsIgnoreCase(host)||"www.perchance.org".equalsIgnoreCase(host))||
          u.getRawUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443)||path==null)throw new Exception();
      String slug=path.replaceFirst("/$","").replaceFirst("^/","").toLowerCase(Locale.ROOT);
      if(!slug.matches("[a-z0-9][a-z0-9-]{0,99}"))throw new Exception();
      switch(slug){
        case "blank":case "minimal":case "basic-template":case "welcome":case "generators":
        case "login":case "signup":case "tutorial":case "examples":case "privacy-policy":case "terms-of-service":
          throw new IllegalArgumentException("Сначала сохраните новую игру в Perchance, чтобы получить её собственную ссылку");
      }
      return "https://perchance.org/"+slug;
    }catch(IllegalArgumentException e){throw e;}
    catch(Exception e){throw new IllegalArgumentException("Нужна ссылка вида https://perchance.org/имя-игры");}
  }
}
