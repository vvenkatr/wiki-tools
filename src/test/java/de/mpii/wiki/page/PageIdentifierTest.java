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

    handler = PageIdentifier.getHandler("Test", "{{Wiktionary|gross|groß}}'''Gross''' may refer to:*[[Gross (economics)]], before deductions*[[Gross (unit)]], a counting unit equal to 144*[[Gross weight]]*[[Gross, Nebraska]], a US village*[[Gross!]], a television show on Discovery Channel* A [[colloquialism]] meaning [[disgust]]ing.People with the surname '''Gross''':*[[Gross (surname)]]*''See also'' [[Grosz]]==See also==*[[Gross examination]], in anatomical pathology, identification of disease with the naked eye*[[Gross anatomy]], macroscopic anatomy*[[Gross indecency]], in law, flagrant indecency*[[Gross negligence]], in law, flagrant negligence*[[Daniel J. Gross Catholic High School]], Omaha Gross High School {{disambiguation}} [[cs:Gross]][[de:Groß]][[fr:Gross]][[he:גרוס]][[lv:Gross]][[ja:グロス]][[pt:Gross]][[ru:Гросс]]");
    assertEquals(Handler.HandlerType.DISAMBIGUATIONS, handler.getType());
  }

  @Test
  public void identifyDisambiguationPageFromTitle() {
    Handler handler = PageIdentifier.getHandler("Montanaro (surname)", "'''Montanaro''' is the last name of several people:*[[Donato A. Montanaro]], co-founder, chairman and CEO of online brokerage house TradeKing*[[Tony Montanaro]], mime artist*[[Lucio Montanaro]], Italian actor{{surname|Montanaro}}*[[Domingo M. Montanaro]] Nice Guy!");    
    assertEquals(Handler.HandlerType.DISAMBIGUATIONS, handler.getType());
    
    handler = PageIdentifier.getHandler("Title (Disambiguation)", "'''Einstein''' may refer to:* [[Einstein (crater)]], a large lunar crater along the Moons west limb* [[Einstein Observatory]], tje forst first fully imaging X-ray telescope put into orbit* [[Einstein Tower]], an astrophysical observatory");    
    assertEquals(Handler.HandlerType.DISAMBIGUATIONS, handler.getType());
  }
  
  @Test
  public void identifyNormalPageWithDisambiguationMarkerInBracket() {
    Handler handler = PageIdentifier.getHandler("Title", "Some times an entity page contains (disambiguation) even though it is a normal page");
    
    assertEquals(Handler.HandlerType.NORMAL, handler.getType());
  }
}
