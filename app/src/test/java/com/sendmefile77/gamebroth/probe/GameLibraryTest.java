package com.sendmefile77.gamebroth.probe;

import org.junit.Test;
import static org.junit.Assert.*;

public class GameLibraryTest {
  private GameLibrary defaults(){return GameLibrary.defaults(new String[]{"https://perchance.org/first-game","https://perchance.org/second-game"},new String[]{"Первая","Вторая"});}
  @Test public void catalogueSurvivesRenameAndReload()throws Exception{
    GameLibrary l=defaults();String id=l.entries().get(0).id;
    l.rename(id,"  Моё\nназвание  ");GameLibrary.Entry added=l.add("Новая","perchance.org/third-game#edit");
    GameLibrary restored=GameLibrary.parse(l.serialize());
    assertEquals(3,restored.entries().size());assertEquals("Моё название",restored.byId(id).name);
    assertEquals("https://perchance.org/first-game",restored.byId(id).url);
    assertEquals(added.id,restored.byUrl("https://perchance.org/third-game").id);
    restored.remove(id);assertNull(GameLibrary.parse(restored.serialize()).byId(id));
  }
  @Test public void duplicatesCannotPretendToBeIndependentCopies(){
    GameLibrary l=defaults();
    assertThrows(IllegalArgumentException.class,()->l.add("Копия","https://www.perchance.org/first-game/?x=1#story"));
    assertEquals(2,l.entries().size());
  }
  @Test public void onlyCanonicalGameLinksAreAccepted(){
    assertEquals("https://perchance.org/valid-name",GameLibrary.normalizeUrl(" https://WWW.perchance.org/Valid-Name/?v=2#edit "));
    for(String bad:new String[]{"http://perchance.org/game","https://perchance.org.evil.test/game","https://perchance.org@evil.test/game","https://user@perchance.org/game","https://perchance.org:444/game","https://perchance.org/one/two","https://perchance.org/%67ame","javascript:alert(1)","file:///game","https://perchance.org/","https://perchance.org/blank#edit","https://perchance.org/minimal"})
      assertThrows(bad,IllegalArgumentException.class,()->GameLibrary.normalizeUrl(bad));
  }
  @Test public void invalidStoredDataIsRejectedInsteadOfPartlyOverwritten(){
    assertThrows(Exception.class,()->GameLibrary.parse("{broken"));
    assertThrows(Exception.class,()->GameLibrary.parse("{\"version\":2,\"games\":[]}"));
    String sameId="{\"version\":1,\"games\":[{\"id\":\"one\",\"name\":\"A\",\"url\":\"https://perchance.org/a\"},{\"id\":\"one\",\"name\":\"B\",\"url\":\"https://perchance.org/b\"}]}";
    assertThrows(Exception.class,()->GameLibrary.parse(sameId));
    assertThrows(IllegalArgumentException.class,()->defaults().rename("default-0","  \n "));
  }
}
