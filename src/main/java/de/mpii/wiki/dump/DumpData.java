package de.mpii.wiki.dump;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.mpii.wiki.common.Utils;
import de.mpii.wiki.compute.Jaccard;
import de.mpii.wiki.dump.DumpSettings.DumpType;
import de.mpii.wiki.page.PageIdentifier;
import de.mpii.wiki.page.handlers.Handler;
import de.mpii.wiki.page.handlers.Handler.HandlerType;

public class DumpData {

  private final DumpType dumpType;

  /*
   * Basic information regarding a page in wiki dump
   */

  // pageId -> pageTitle map
  private TIntObjectMap<String> idTitleMap;

  // pageTitle -> pageId map
  private TObjectIntMap<String> titleIdMap;

  // pageId -> page Content map (portion of text)
  private TIntObjectMap<String> idTextMap;

  /*
   * Additional information for entries in dump
   */

  // Stores Page Id and list of links present in the page
  private TIntObjectMap<List<String>> pageLinks;

  // Stores Page Id and list of titles to which the page disambiguates to
  private TIntObjectMap<List<String>> disambiguations;

  // Stores Redirections (id -> id map)
  private TIntObjectMap<List<String>> redirections;

  // number of page entries processed
  private int processedPages = 0;

  // just to keep track of count of processed page types
  private TObjectIntMap<HandlerType> stats;

  // This would be populated *only* in source dump with disambiguation ids from target dump. (To avoid storing too much unused data)
  private TIntSet targetDisambiguationIds;
  
  private static Logger logger_ = LoggerFactory.getLogger(DumpData.class);

  private void updateCounter() {
    processedPages++;
    if (processedPages % 100000 == 0) {
      logger_.debug("Processed " + processedPages + " page entries.");
    }
  }

  private void init() {
    idTitleMap = new TIntObjectHashMap<String>();
    titleIdMap = new TObjectIntHashMap<String>();
    idTextMap = new TIntObjectHashMap<String>();

    pageLinks = new TIntObjectHashMap<List<String>>();
    disambiguations = new TIntObjectHashMap<List<String>>();
    redirections = new TIntObjectHashMap<List<String>>();

    stats = new TObjectIntHashMap<HandlerType>();
    stats.put(HandlerType.NORMAL, 0);
    stats.put(HandlerType.REDIRECTS, 0);
    stats.put(HandlerType.DISAMBIGUATIONS, 0);
    
    targetDisambiguationIds = new TIntHashSet();
  }

  public DumpData(DumpType dType) {
    init();
    dumpType = dType;
  }

  public DumpData(DumpType dType, TIntSet targetDisambiguationIds) {
    this(dType);
    this.targetDisambiguationIds = targetDisambiguationIds;
  }

  public void addPageEntry(int id, String title, String content) {
    // Store the basic info retrieved from the page
    if (dumpType.requiresBasicInfo()) {
      idTitleMap.put(id, title);
      titleIdMap.put(title, id);
    }

    // load page content only for evaluation purpose.
    if (dumpType.loadPageText()) {
      idTextMap.put(id, Utils.cleanAndCompressText(content));
    }

    boolean isSpecialPage = false;
    Handler handlerToExecute = null;
    List<String> lstLinks = null;

    handlerToExecute = PageIdentifier.getHandler(title, content);
    isSpecialPage = handlerToExecute.getType().isSpecialInfoPage();
    lstLinks = handlerToExecute.process(content);

    // store redirections and disambiguation only for target dump
    if (dumpType.processSpecialPage() && isSpecialPage) {
      if (handlerToExecute.getType().equals(HandlerType.REDIRECTS)) {
        redirections.put(id, lstLinks);
      } else if (handlerToExecute.getType().equals(HandlerType.DISAMBIGUATIONS)) {
        disambiguations.put(id, lstLinks);
      }
    }

    if (!isSpecialPage) {
      // Need to extract page links for both source(only if the source id is marked as disambiguation in target) and target
      if(isSrcIdDisambiguatedInTarget(id) || isTargetDump(dumpType)) {
        pageLinks.put(id, lstLinks);
      }      
    }

    // update stat
    updateStat(handlerToExecute.getType());
    
    updateCounter();
  }

  private boolean isSrcIdDisambiguatedInTarget(int id) {
    return isSourceDump(dumpType) && targetDisambiguationIds.contains(id);
  }
  
  private boolean isSourceDump(DumpType dumpType) {
   return  (dumpType.equals(DumpType.SOURCE) 
           || dumpType.equals(DumpType.SOURCE_EVAL));
  }
  
  private boolean isTargetDump(DumpType dumpType) {
    return  (dumpType.equals(DumpType.TARGET) 
            || dumpType.equals(DumpType.TARGET_EVAL));
   }
  
  private void updateStat(HandlerType type) {
    int count = stats.get(type);
    stats.put(type, count + 1);
  }

  public int size() {
    return idTitleMap.size();
  }

  public int[] getPageIds() {
    return idTitleMap.keys();
  }
  
  public String getTitle(int id) {
    return idTitleMap.get(id);
  }
  
  public String getPageText(int id) {
    return idTextMap.get(id);
  }
  
  public boolean isValidId(int id) {
    return pageLinks.containsKey(id);
  }
  
  public boolean isRedirect(int id) {
    return redirections.containsKey(id);
  }

  public int getRedirectedId(int id) {
    return resolveRedirection(id);
  }
  
  public int getDisambiguatedId(int id, List<String> links) {
    return disambiguate(id, links);
  }
    
  public boolean isDisambiguation(int id) {
    return disambiguations.containsKey(id);
  }
  
  public List<String> getPageLinks(int pId) {
    return pageLinks.get(pId);
  }

  public boolean hasId(int id) {
    return idTitleMap.containsKey(id);
  }
  

  private int disambiguate(int srcPageId, List<String> srcPageLinks) {    
    List<String> tgtPageDisambiguationLinks = disambiguations.get(srcPageId);
    tgtPageDisambiguationLinks = Utils.verifyList(tgtPageDisambiguationLinks);

    double maxScore = 0.0;
    int result = srcPageId; // return the current pageId, if no disambiguations are found

    // for each disambiguation option, get the content stored in pageContent and compute similarity
    for(String tgtPageTitle : tgtPageDisambiguationLinks) {
      int tgtPageId = titleIdMap.get(tgtPageTitle);
      List<String> tgtPageLinks = pageLinks.get(tgtPageId);      
      double score = Jaccard.compute(srcPageLinks, tgtPageLinks);
      logger_.debug("Target Disambiguation Page : "+ tgtPageTitle + " with score : " + score);
      if(score > maxScore) {
        result = tgtPageId;
        maxScore = score;
      }
    }
    return result;
  }

  //This method resolves redirection pages(including multiple redirections).
  //  In case of a cycle, the given id is returned i.e id is mapped on to itself.
  private int resolveRedirection(int redirectId) {
    TIntSet processed = new TIntHashSet();
    processed.add(redirectId);
    int itK = redirectId;
    boolean found = false;
    while(redirections.containsKey(itK)) {
      
      List<String> tmp = redirections.get(itK);
      
      if(tmp == null || tmp.isEmpty()) {
        return itK;
      }

      //FIXME: if tmp size is greater than 1, then something is wrong with the redirect page : Not handled!
      itK = titleIdMap.get(tmp.get(0));

      if(!processed.contains(itK)) {
        processed.add(itK);
      } else {
        logger_.warn("Cycle Found for id : "+ redirectId +": " + processed);
        // redirectionCyclesPageIds.add(redirectId);
        found = true;
        break;
      }
    }
    if(found) return redirectId;
    return itK;
  }

  public TIntSet getDisambiguationIds() {
    return disambiguations.keySet();
  }
}