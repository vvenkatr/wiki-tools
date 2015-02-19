package de.mpii.wiki.common;

import java.util.List;

import org.junit.Test;

public class UtilsTest {

  @Test
  public void verifyForTextContainingGivenWords() {
    String strToVerify = "a quick brown fox jumped over the lazy dog";
    String[][] lookUpWords = new String[][] {{"Quick"}, {"QUICK"}, {"Brown"}, {"LAzY"}, {"slow"}, {"under"}};
    boolean[] outcomes = new boolean[] {true, true, true, true, false, false};
    
    for(int i=0;i<lookUpWords.length;i++) {
      String[] words = lookUpWords[i];
      boolean outcome = outcomes[i];      
      assert Utils.containsAny(strToVerify, words) == outcome;
    }    
  }
  
  @Test
  public void verifyLinksExtractedFromWikiPages() {
    String text = "[[Albert_Einstein|Albert Einstein]] was a German-American theoretical physicist. Einstein was born in [[Ulm_Link|Ulm]], [[Germany_Link|Germany]]. He developed [[general_theory_of_relativity|general theory of relativity]]. He was awarded Nobel prize in Physics in 1921.";
    List<String> links = Utils.extractLinks(text);
    
    assert links.size() == 4;
    
    String[] linksToCheck = new String[] {"Albert Einstein", "Albert_Einstein", "Einstein", "Ulm_Link", "Ulm", "Germany_Link", "Germany", "general_theory_of_relativity", "general theory of relativity"};
    boolean[] outcomes = new boolean[] {false, true, false, true, false, true, false, true, false};
    
    for(int i=0;i<linksToCheck.length;i++) {
      String link = linksToCheck[i];
      boolean outcome = outcomes[i];
      assert links.contains(link) == outcome;      
    }
  }
}
