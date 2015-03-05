package de.mpii.wiki.page;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import de.mpii.wiki.page.handlers.Handler;

public class PageIdentifierTest {
  
  @Test
  public void identifyRedirectPageFromContent() {
    Handler handler = PageIdentifier.getHandler("Test", "#REDIRECT [[Test3]] {{R from CamelCase}}");
    
    assertEquals(Handler.HandlerType.REDIRECTS, handler.getType());
  }
  
  @Test
  public void identifyDisambiguationPageFromContent() {
    Handler handler = PageIdentifier.getHandler("Test", "'''Einstein''' may refer to:* [[Einstein (crater)]], a large lunar crater along the Moons west limb* [[Einstein Observatory]], tje forst first fully imaging X-ray telescope put into orbit* [[Einstein Tower]], an astrophysical observatory in Potsdan, Germany __NOTOC__ {{Disambig}}");
    
    assertEquals(Handler.HandlerType.DISAMBIGUATIONS, handler.getType());
  }
  
  @Test
  public void identifyDisambiguationPageFromTitle() {
    Handler handler = PageIdentifier.getHandler("Title (Disambiguation)", "'''Einstein''' may refer to:* [[Einstein (crater)]], a large lunar crater along the Moons west limb* [[Einstein Observatory]], tje forst first fully imaging X-ray telescope put into orbit* [[Einstein Tower]], an astrophysical observatory");
    
    assertEquals(Handler.HandlerType.DISAMBIGUATIONS, handler.getType());
  }
  
  @Test
  public void identifyNormalPageWithDisambiguationMarkerInBracket() {
    Handler handler = PageIdentifier.getHandler("Title", "Some times an entity page contains (disambiguation) even though it is a normal page");
    
    assertEquals(Handler.HandlerType.NORMAL, handler.getType());
  }
}
