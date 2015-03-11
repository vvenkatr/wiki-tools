package de.mpii.wiki.page.handlers;



public class TitleDisambiguationHandler extends Handler {

  private static final String[] DISAMBIGUATION_TERMS = new String[] {
    "(Disambiguation)", "(Surname)", "(Given name)", "(Name)"
  };
  
  @Override
  public String[] getMatcherStrings() {
    return DISAMBIGUATION_TERMS;
  }

  @Override
  public HandlerType getType() {
    return HandlerType.DISAMBIGUATIONS;
  }
}
