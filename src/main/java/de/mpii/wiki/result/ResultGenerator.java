package de.mpii.wiki.result;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.mpii.wiki.dump.DumpData;
import de.mpii.wiki.dump.DumpSettings.MappedType;

public class ResultGenerator {

  private static Logger logger_ = LoggerFactory.getLogger(ResultGenerator.class);

  public static List<MappedResult> generate(DumpData sourceData, DumpData targetData) {
    List<MappedResult> results = new ArrayList<>();

    int[] srcIds = sourceData.getPageIds();
    for (int srcId : srcIds) {
      int tgtId = 0;

      MappedType type = null;

      String srcTitle = null;
      String tgtTitle = null;

      String srcText = "--NA--";
      String tgtText = "--NA--";

      srcTitle = sourceData.getTitle(srcId);

      if(!sourceData.isValidId(srcId)) {
        // Source entry is either redirect/disambiguation and ignored 
        type = MappedType.SOURCE_IGNORED;
        if(targetData.hasId(srcId)) {
          tgtTitle = targetData.getTitle(srcId);
        } else {
          tgtTitle = srcTitle;
        }
      } else if (targetData.isRedirect(srcId)) {
        // source id is valid, check target for redirections
        tgtId = targetData.getRedirectedId(srcId);
        if (tgtId == srcId) {
          type = MappedType.REDIRECTED_CYCLE;
        } else {
          type = MappedType.REDIRECTED;
        }

        tgtTitle = targetData.getTitle(tgtId);
        logger_.debug(srcTitle + "(" + srcId + ") redirects to : " + tgtTitle + "(" + tgtId + ")");
      } else if (targetData.isDisambiguation(srcId)) {
        // not a redirection, verifying for disambiguation
        type = MappedType.DISAMBIGUATED;
        
        tgtId = targetData.getDisambiguatedId(srcId, sourceData.getPageLinks(srcId));
        tgtTitle = targetData.getTitle(tgtId);

        logger_.debug(srcTitle + "(" + srcId + ") disambiguates to : " + tgtTitle + "(" + tgtId + ")");
        srcText = sourceData.getPageText(srcId);
        tgtText = targetData.getPageText(tgtId);
      } else if(!targetData.hasId(srcId)) {
        type = MappedType.DELETED;
        //TODO : check which one to use null or srcTitle?
        tgtTitle = null; 
      } else {
        // if not any of above, check whether it has been updated/unchanged!        
        tgtTitle = targetData.getTitle(srcId);
        if(!srcTitle.equals(tgtTitle)) {
          type = MappedType.UPDATED;
          results.add(new MappedResult(srcTitle, tgtTitle, type, srcText, tgtText));
          continue;
        }
        // A valid source id that is not deleted, updated, redirected or disambiguated in target is an Unchanged entry
        type = MappedType.UNCHANGED;
      } 
      results.add(new MappedResult(srcTitle, tgtTitle, type, srcText, tgtText));
    }
    return results;
  }
}