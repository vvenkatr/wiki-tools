package de.mpii.wiki.handlers;

import java.util.List;

import de.mpii.wiki.common.Utils;


public abstract class Handler {
  
  public enum HandlerType {
    REDIRECTS, DISAMBIGUATIONS, EMPTY
  }

  public boolean canHandle(String text) {
    if(getType().equals(HandlerType.EMPTY)) {
      return true;
    }
    
    if(text == null || text.equals("")) {
      return false;
    }
    
    return Utils.containsAny(text, getMatcherStrings());
  }

  public List<String> process(String text) {
    return Utils.extractLinks(text);
  }

  public abstract String[] getMatcherStrings();

  public abstract HandlerType getType();
}