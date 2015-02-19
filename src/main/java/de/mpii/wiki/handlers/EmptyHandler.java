package de.mpii.wiki.handlers;


public class EmptyHandler extends Handler {

  @Override
  public String[] getMatcherStrings() {
    return null;
  }

  @Override
  public HandlerType getType() {
    return HandlerType.EMPTY;
  }

}
