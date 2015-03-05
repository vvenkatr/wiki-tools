package de.mpii.wiki.page.handlers;


public class NormalHandler extends Handler {

  @Override
  public String[] getMatcherStrings() {
    return null;
  }

  @Override
  public HandlerType getType() {
    return HandlerType.NORMAL;
  }

}
