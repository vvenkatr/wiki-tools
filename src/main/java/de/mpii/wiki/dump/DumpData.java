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
import de.mpii.wiki.handlers.DisambiguationHandler;
import de.mpii.wiki.handlers.EmptyHandler;
import de.mpii.wiki.handlers.Handler;
import de.mpii.wiki.handlers.Handler.HandlerType;
import de.mpii.wiki.handlers.RedirectsHandler;

public class DumpData {

  private final DumpType type;

  private final Handler[] handlers;

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

    // TODO: Unused for time being, need to update this map for each scenario.
    stats = new TObjectIntHashMap<HandlerType>();
    stats.put(HandlerType.EMPTY, 0);
    stats.put(HandlerType.REDIRECTS, 0);
    stats.put(HandlerType.DISAMBIGUATIONS, 0);
  }

  public DumpData(DumpType dType) {
    init();
    type = dType;
    handlers = new Handler[] { new RedirectsHandler(), new DisambiguationHandler() };
  }

  public void addPageEntry(int id, String title, String content) {
    // Store the basic info retrieved from the page
    if (type.requiresBasicInfo()) {
      idTitleMap.put(id, title);
      titleIdMap.put(title, id);
    }

    // load page content only for evaluation purpose.
    if(type.loadPageText()) {
      idTextMap.put(id, Utils.cleanAndCompressText(content));
    }

    boolean foundAdditionalInfo = false;

    Handler handlerToExecute = null;
    List<String> lstLinks = null;

    for (Handler handler : handlers) {
      if (handler.canHandle(content)) {
        handlerToExecute = handler;
        foundAdditionalInfo = true;
        break;
      }
    }

    // store redirections and disambiguation only for target dump
    if (type.requiresAdditionalInfo() && foundAdditionalInfo) {
      lstLinks = handlerToExecute.process(content);
      if (handlerToExecute.getType().equals(HandlerType.REDIRECTS)) {
        redirections.put(id, lstLinks);
      } else if (handlerToExecute.getType().equals(HandlerType.DISAMBIGUATIONS)) {
        disambiguations.put(id, lstLinks);
      }
    }

    // store page links for both source and target dumps
    if (!foundAdditionalInfo) {
      // Need to extract page links for both source and target
      handlerToExecute = new EmptyHandler();
      pageLinks.put(id, handlerToExecute.process(content));
      // update stats
    } else {
      // update stats for redirects and disambiguations
    }

    updateCounter();
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
}