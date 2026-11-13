package com.sendmefile77.gamebroth.probe;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure validation shared by archive, frame handshake and command editor. */
final class PlayerPolicy {
  static String page(String value){
    try{URI u=new URI(value);String path=u.getPath();
      if(!"https".equals(u.getScheme())||!"perchance.org".equals(u.getHost())||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443)||path==null||!path.matches("/[a-z0-9][a-z0-9-]{0,99}/?"))return null;
      return "https://perchance.org"+path.replaceFirst("/$","");
    }catch(Exception e){return null;}
  }
  static String frameOrigin(String top,String frame){
    try{String page=page(top);if(page==null)return null;URI u=new URI(frame);String h=u.getHost();
      if(!"https".equals(u.getScheme())||h==null||!h.matches("[a-z0-9-]+\\.perchance\\.org")||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443))return null;
      if(!new URI(page).getPath().equals(u.getPath().replaceFirst("/$","")))return null;
      return "https://"+h;
    }catch(Exception e){return null;}
  }
  static String hash(byte[] value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value);StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
  static String hash(String value){return hash(value.getBytes(StandardCharsets.UTF_8));}
  static String clean(String value,int max){if(value==null)return "";return value.replace('\u0000',' ').trim().substring(0,Math.min(value.replace('\u0000',' ').trim().length(),max));}
  static String[] fields(String template){LinkedHashSet<String> fields=new LinkedHashSet<>();Matcher m=Pattern.compile("\\{([^{}\\r\\n]{1,40})\\}").matcher(template);while(m.find())fields.add(m.group(1));return fields.toArray(new String[0]);}
  static String expand(String template,Map<String,String> values){Matcher m=Pattern.compile("\\{([^{}\\r\\n]{1,40})\\}").matcher(template);StringBuffer out=new StringBuffer();while(m.find()){String v=values.get(m.group(1));if(v==null||v.trim().isEmpty())throw new IllegalArgumentException("Заполните: "+m.group(1));m.appendReplacement(out,Matcher.quoteReplacement(v.trim()));}m.appendTail(out);if(out.length()>4000)throw new IllegalArgumentException("Команда — до 4000 символов");return out.toString();}
  private PlayerPolicy(){}
}
