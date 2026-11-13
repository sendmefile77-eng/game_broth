package com.sendmefile77.gamebroth.probe;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class PlayerPolicyTest {
 @Test public void canonicalPage(){assertEquals("https://perchance.org/fanta",PlayerPolicy.page("https://perchance.org/fanta/#/story"));assertNull(PlayerPolicy.page("http://perchance.org/fanta"));assertNull(PlayerPolicy.page("https://perchance.org.evil/fanta"));assertNull(PlayerPolicy.page("https://user@perchance.org/fanta"));}
 @Test public void outputFrameMustBelongToTheCurrentGame(){assertEquals("https://abc123.perchance.org",PlayerPolicy.frameOrigin("https://perchance.org/fanta#story","https://abc123.perchance.org/fanta?version=3"));assertNull(PlayerPolicy.frameOrigin("https://perchance.org/fanta","https://abc123.perchance.org/other"));assertNull(PlayerPolicy.frameOrigin("https://perchance.org/fanta","https://abc123.perchance.org.evil/fanta"));assertNull(PlayerPolicy.frameOrigin("https://perchance.org/fanta","https://abc123.perchance.org:8443/fanta"));assertNull(PlayerPolicy.frameOrigin("https://perchance.org/fanta","https://abc123.perchance.org/../fanta"));}
 @Test public void templatesPreserveLiteralCharacters(){Map<String,String> values=new HashMap<>();values.put("предмет","$10 \\ ключ");assertEquals("Осмотреть $10 \\ ключ; снова $10 \\ ключ",PlayerPolicy.expand("Осмотреть {предмет}; снова {предмет}",values));assertArrayEquals(new String[]{"предмет"},PlayerPolicy.fields("{предмет}, {предмет}"));}
 @Test public void missingTemplateFieldIsAnError(){try{PlayerPolicy.expand("Осмотреть {предмет}",Collections.emptyMap());fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("предмет"));}}
 @Test public void imageAndSceneKeysAreStable(){assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",PlayerPolicy.hash("abc"));assertNotEquals(PlayerPolicy.hash("game-a:scene"),PlayerPolicy.hash("game-b:scene"));}
}
