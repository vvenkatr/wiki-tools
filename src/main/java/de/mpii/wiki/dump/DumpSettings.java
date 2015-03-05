package de.mpii.wiki.dump;


public class DumpSettings {
  
  public enum DumpType {
    
    SOURCE(true, false, false), TARGET(true, true, false), SOURCE_EVAL(true, false, true), TARGET_EVAL(true, true, true);
    
    boolean extractBasicInfo;
    boolean extractAdditionalInfo;
    boolean loadPageText;
    
    DumpType(boolean extractBasic, boolean extractAdditional, boolean loadText) {
      extractBasicInfo = extractBasic;
      extractAdditionalInfo = extractAdditional;
      loadPageText = loadText;
    }
    
    public boolean requiresBasicInfo() {
      return extractBasicInfo;
    }
    
    public boolean processSpecialPage() {
      return extractAdditionalInfo;
    }

    public boolean loadPageText() {
      return loadPageText;
    }
  }
  
  
  public enum MappedType {
    DELETED("__DL__"), UNCHANGED("__UC__"), UPDATED("__UP__"), REDIRECTED("__R__"), 
    DISAMBIGUATED("__D__"), REDIRECTED_CYCLE("__RC__"), SOURCE_IGNORED("__SI__");
  
    String reprText;
    
    MappedType(String repr) {
      reprText = repr;
    }
  
    @Override
    public String toString() {      
      return reprText;
    }
  }
  
}
