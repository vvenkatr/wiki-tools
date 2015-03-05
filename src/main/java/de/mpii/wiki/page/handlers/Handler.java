package de.mpii.wiki.page.handlers;

import java.util.List;

import de.mpii.wiki.common.Utils;


public abstract class Handler {
  
  public enum HandlerType {
    REDIRECTS(true), DISAMBIGUATIONS(true), NORMAL(false);

    private final boolean containsAdditionalInfo;

    HandlerType(boolean addnInfo) {
      containsAdditionalInfo = addnInfo;
    }

    public boolean isSpecialInfoPage() {
      return containsAdditionalInfo;
    }
  }

  public boolean canHandle(String text) {
    if(getType().equals(HandlerType.NORMAL)) {
      return true;
    }
    
    if(text == null || text.equals("")) {
      return false;
    }
    
    return Utils.containsAny(text, getMatcherStrings());
  }

  /*
   * This method extracts all the links in the page (irrespective of the type of handler)
   */
  public List<String> process(String text) {
    return Utils.extractLinks(text);
  }

  public abstract String[] getMatcherStrings();

  public abstract HandlerType getType();
}