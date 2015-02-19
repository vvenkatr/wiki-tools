package de.mpii.wiki.result;

import de.mpii.wiki.dump.DumpSettings.MappedType;


public class MappedResult {
  String source;
  String target;
  MappedType type;
  String sourceText;
  String targetText;
  
  public MappedResult(String source, String target, MappedType type, String srcText, String tgtText) {
    this.source = source;
    this.target = target;
    this.type = type;
  }
  
  public String getSourceTitle() {
    return source;
  }
  
  public String getTargetTitle() {
    return target;
  }
  
  public String getSourceText() {
    return sourceText;
  }
  
  public String getTargetText() {
    return targetText;
  }
  
  public MappedType getMappingType() {
    return type;
  }
}
