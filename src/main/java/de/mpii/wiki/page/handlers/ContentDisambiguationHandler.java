package de.mpii.wiki.page.handlers;



public class ContentDisambiguationHandler extends Handler {

  private static final String[] DISAMBIGUATION_TERMS = new String[] {
    "{{Disambig}}","{{Airport_disambig}}","{{Battledist}}",
    "{{Callsigndis}}","{{Chemistry disambiguation}}",
    "{{Church_disambig}}","{{Disambig-Chinese-char-title}}",
    "{{Disambig-cleanup}}","{{Genus_disambiguation}}",
    "{{Geodis}}","{{Hndis}}","{{Hndis-cleanup}}","{{Hospitaldis}}",
    "{{Hurricane_disambig}}","{{Letter_disambig}}",
    "{{Letter-NumberCombDisambig}}","{{Mathdab}}","{{MolFormDisambig}}",
    "{{NA_Broadcast_List}}","{{Numberdis}}","{{Schooldis}}",
    "{{Species_Latin name abbreviation disambiguation}}",
    "{{Taxonomy_disambiguation}}","{{Species_Latin_name_disambiguation}}",
    "{{WP_disambig}}", "{{given name}}", "{{surname}}",
    // old entries
    "{{dab}}","{{disambiguation}}","{{geodab}}","{{geo-dis}}",
    "{{disambig|geo}}", "{{Disambig-CU}}", "{{disamb}}",
    "Category:Disambiguation pages",
    "Category:Molecular formula disambiguation pages"
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
