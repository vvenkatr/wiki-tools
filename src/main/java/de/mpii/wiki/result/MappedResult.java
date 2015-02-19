package de.mpii.wiki.result;

import de.mpii.wiki.dump.DumpSettings.MappedType;


public class MappedResult {

  private final String source;
  private final String target;
  private final MappedType type;
  private final String sourceText;
  private final String targetText;

  private static final String NO_STR = "--NA--";

  public MappedResult(String source, String target, MappedType type, String srcText, String tgtText) {
    this.source = source;
    this.target = target;
    this.type = type;
    this.sourceText = srcText;
    this.targetText = tgtText;
  }

  public String getSourceTitle() {
    return source;
  }

  public String getTargetTitle() {
    return target;
  }

  public String getSourceText() {
    return (sourceText != null) ? sourceText : NO_STR;
  }

  public String getTargetText() {
    return (targetText !=null) ? targetText : NO_STR;
  }

  public MappedType getMappingType() {
    return type;
  }
}
