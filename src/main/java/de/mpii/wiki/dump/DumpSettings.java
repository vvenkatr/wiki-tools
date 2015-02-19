package de.mpii.wiki.dump;


public class DumpSettings {
  
  public enum DumpType {
    
    SOURCE(true, false), TARGET(true, true);
    
    boolean extractBasicInfo;
    boolean extractAdditionalInfo;
    
    DumpType(boolean extractBasic, boolean extractAdditional) {
      extractBasicInfo = extractBasic;
      extractAdditionalInfo = extractAdditional;
    }
    
    public boolean requiresBasicInfo() {
      return extractBasicInfo;
    }
    
    public boolean requiresAdditionalInfo() {
      return extractAdditionalInfo;
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
