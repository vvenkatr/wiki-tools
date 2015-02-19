package de.mpii.wiki.handlers;



public class RedirectsHandler extends Handler {

  @Override
  public String[] getMatcherStrings() {
    return new String[] { 
        "#REDIRECT","#Redirect"
    };
  }

  @Override
  public HandlerType getType() {
    return HandlerType.REDIRECTS;
  }
}
