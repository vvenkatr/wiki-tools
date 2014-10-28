package de.mpii.wikitools;

import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class consists of static methods that operate on either individual Wikipedia dump file or
 * on multiple versions of dumps.
 *
 * @author vvenkatr
 *
 */
public class WikiRevTools {

  // Xml markups used in Wikipedia dump file.

  private static final String PAGE_TAG = "page";
  private static final String PAGE_ID_TAG = "id";
  private static final String PAGE_TITLE_TAG = "title";
  private static final String PAGE_REVISION_TAG = "revision";
  private static final String PAGE_REDIRECT_TAG = "redirect";
  private static final String PAGE_REVISION_TEXT_TAG = "text";

  // Mapping result indicator
  private static final String redirectedEntry = "__R__";
  private static final String disambiguatedEntry = "__D__";
  private static final String unchangedEntry = "__U__";
  private static final String redirectedCyle = "__C__";

  // Maximum length of wiki page text to use in output
  private static final int MAX_TEXT_LENGTH = 1000;

  // Wikipedia page revision text will be matched against this pattern to retrieve name links.
  private static Pattern pattern = Pattern.compile("\\[\\[(.*?)\\]\\]");

  //maps to store id, title relation (for target dump)
  private static TIntObjectMap<String> targetDumpIdTitleMap;
  private static TObjectIntMap<String> targetDumpTitleIdMap;

  //maps to store id, title relation (for source dump)
  private static TIntObjectMap<String> sourceDumpIdTitleMap;

  // map to store Redirections
  private static TIntIntMap redirectIds;

  // map to store final-map-results
  private static Map<String, String> finalMap;

  private static TIntIntMap finalIdMap;

  // map to store title and a portion of original text entry from new(target) dump(needed only for writing to file)
  private static TIntObjectMap<String> newDumpPageIdText;

  //map to store title and a portion of original text entry from old(source) dump(needed only for writing to file)
  private static TIntObjectMap<String> oldDumpPageIdText;

  // set to store redirected titles for verification before writing to file
  private static TIntSet redirectedPageIds;

  // set to store disambiguated titles for verification before writing to file
  private static TIntSet disambiguatedPageIds;

  // set to store pages that have redirection cycles
  private static TIntSet redirectionCyclesPageIds;

  // Stores Page Id and list of titles to which the page disambiguates to
  private static TIntObjectMap<List<String>> disambiguationPageIdLinks;

  //Stores Page Id and list of titles referred in the text
  private static TIntObjectMap<List<String>> pageIdContent;

  private static Options commandLineOptions;

  private static Logger logger_ = LoggerFactory.getLogger(WikiRevTools.class);

  private enum DumpType {
    OLD, NEW;
  }

  private static final String[] DISAMBIGUATION_TERMS = new String[] {"{{Disambig}}","{{Airport_disambig}}",
    "{{Battledist}}","{{Callsigndis}}","{{Chemistry disambiguation}}",
    "{{Church_disambig}}","{{Disambig-Chinese-char-title}}",
    "{{Disambig-cleanup}}","{{Genus_disambiguation}}",
    "{{Geodis}}","{{Hndis}}","{{Hndis-cleanup}}","{{Hospitaldis}}",
    "{{Hurricane_disambig}}","{{Letter_disambig}}",
    "{{Letter-NumberCombDisambig}}","{{Mathdab}}","{{MolFormDisambig}}",
    "{{NA_Broadcast_List}}","{{Numberdis}}","{{Schooldis}}",
    "{{Species_Latin name abbreviation disambiguation}}",
    "{{Taxonomy_disambiguation}}","{{Species_Latin_name_disambiguation}}",
    "{{WP_disambig}}"
  };

  private enum TargetAction {
    LOAD_PAGE_INFO, LOAD_REDIRECTS_DISAMBIGUATIONS;
  }

  /**
   * Returns Map of Wiki page titles from old dump to new dump. The map also includes page entries that
   * remain unchanged between the old and new dump. If any entry is deleted in the new dump, then the title
   * from old dump will be mapped to null.
   *
   * In case of cyclic redirects, the old title will be mapped to itself.
   *
   * @param oldDump The old dump to verify.
   * @param newDump The new dump to compare with.
   * @return Map of old page titles to new page titles.
   * @throws IOException  if loading of dumps fail.
   * @throws XMLStreamException if dump xml is invalid.
   */
  public static Map<String, String> map(File oldDump, File newDump) throws IOException, XMLStreamException {
    return map(oldDump, newDump, true);
  }

  /**
   * Returns Map of Wiki page titles from old dump to new dump. If includeUnchangedEntries is false, unchanged
   * entries will not be added to the final map returned. If any entry is deleted in the new dump, then the title
   * from old dump will be mapped to null.
   *
   * In case of cyclic redirects, the old title will be mapped to itself.
   *
   * @param oldDump The old dump to verify.
   * @param newDump The new dump to compare with.
   * @param includeUnchangedEntries Flag to include/exclude unchanged entries.
   * @return Map of old page titles to new page titles.
   * @throws IOException  if loading of dumps fail.
   * @throws XMLStreamException if dump xml is invalid.
   */

  public static Map<String, String> map(File oldDump, File newDump, boolean includeUnchangedEntries) throws IOException, XMLStreamException {
    TIntIntMap results = mapImpl(oldDump, newDump);
    for(int pid : results.keys()) {
      String title = sourceDumpIdTitleMap.get(pid);
      String target = targetDumpIdTitleMap.get(results.get(pid));
      if(includeEntry(title, target, includeUnchangedEntries)) {
        finalMap.put(title, target);
      }
    }
    return finalMap;
  }

  private static void init() {
    targetDumpIdTitleMap = new TIntObjectHashMap<String>();
    targetDumpTitleIdMap = new TObjectIntHashMap<String>();
    sourceDumpIdTitleMap = new TIntObjectHashMap<String>();
    redirectIds = new TIntIntHashMap();
    finalMap = new HashMap<String, String>();
    finalIdMap = new TIntIntHashMap();
    disambiguationPageIdLinks = new TIntObjectHashMap<>();
    pageIdContent = new TIntObjectHashMap<>();
    redirectedPageIds = new TIntHashSet();
    redirectionCyclesPageIds = new TIntHashSet();
    disambiguatedPageIds = new TIntHashSet();
    newDumpPageIdText = new TIntObjectHashMap<String>();
    oldDumpPageIdText = new TIntObjectHashMap<String>();
  }

  private static TIntIntMap mapImpl(File oldDump, File newDump) throws IOException, XMLStreamException  {
    init();

    XMLInputFactory factory = XMLInputFactory.newInstance();
    XMLEventReader newDumpReader = factory.createXMLEventReader(new FileReader(newDump));
    XMLEventReader oldDumpReader = factory.createXMLEventReader(new FileReader(oldDump));

    long start = System.currentTimeMillis();
    // iterate over target and build id-title and title-id relations
    processReader(newDumpReader, DumpType.NEW, TargetAction.LOAD_PAGE_INFO);
    logger_.debug("Time to scan new version of dump : " + (System.currentTimeMillis() - start)/1000 + " s.");

    // reload and iterate once again over target and construct redirects & disambiguation.
    start = System.currentTimeMillis();
    newDumpReader = factory.createXMLEventReader(new FileReader(newDump));
    processReader(newDumpReader, DumpType.NEW, TargetAction.LOAD_REDIRECTS_DISAMBIGUATIONS);
    logger_.debug("Time to re-scan new version of dump : " + (System.currentTimeMillis() - start)/1000 + " s.");

    // finally, iterate over the source dump and build the output mapping.
    // Passing *null* as it will not be considered at all for SOURCE
    start = System.currentTimeMillis();
    processReader(oldDumpReader, DumpType.OLD, null);
    logger_.debug("Time to scan old version of dump : " + (System.currentTimeMillis() - start)/1000 + " s.");

    return finalIdMap;
  }

  /**
   * Writes the result of map method to file provided. The map also includes page entries that
   * remain unchanged between the old and new dump. If any entry is deleted in the new dump, then the title
   * from old dump will be mapped to null.
   *
   * In case of cyclic redirects, the old title will be mapped to itself.
   *
   * @param oldDump The old dump to verify.
   * @param newDump The new dump to compare with.
   * @param output  The path to write the final results.
   * @throws IOException  if loading of dumps fail.
   * @throws XMLStreamException if dump xml is invalid.
   */
  public static void mapToFile(File oldDump, File newDump, File output) throws IOException, XMLStreamException {
    // by default, include the unchanged entries as well.
    mapToFile(oldDump, newDump, output, true);
  }

  /**
   * Writes the result of map method to file provided. If includeUnchangedEntries is false, unchanged
   * entries will not be added to the final map returned. If any entry is deleted in the new dump, then the title
   * from old dump will be mapped to null.
   *
   * In case of cyclic redirects, the old title will be mapped to itself.
   *
   * @param oldDump The old dump to verify.
   * @param newDump The new dump to compare with.
   * @param output  The path to write the final results.
   * @throws IOException  if loading of dumps fail.
   * @throws XMLStreamException if dump xml is invalid.
   */
  public static void mapToFile(File oldDump, File newDump, File output, boolean includeUnchangedEntries) throws IOException, XMLStreamException {
    TIntIntMap result = mapImpl(oldDump, newDump);
    logger_.debug("Writing results to file : " + output.getName());
    try{
      // just in case delete any old file
      output.delete();
      writeFileContent(output, result, includeUnchangedEntries);
      logger_.debug(result.size() + " entries written to " + output.getName());
    }catch(IOException ioe) {
      logger_.error("Failed to write results to file");
    }
  }

  // private helper methods

  private static boolean includeEntry(String title, String targetTitle, boolean includeUnchangedEntries) {
    return (!title.equals(targetTitle) || includeUnchangedEntries);
  }

  private static void processReader(XMLEventReader reader,
      DumpType dumpType, TargetAction actionType) throws XMLStreamException {

    int processedPages = 0;
    int pageId = -1;
    int targetPageId = -1;
    String title = null;
    boolean processingRevisionTag = false;
    boolean extractRedirectText = false;
    String revisionTextContent = null;

    boolean loadAdditionalInfo = dumpType.equals(DumpType.NEW)
        && actionType.equals(TargetAction.LOAD_REDIRECTS_DISAMBIGUATIONS);

    while (reader.hasNext()) {
      XMLEvent event = reader.nextEvent();
      if (event.isStartElement()) {
        StartElement startElement = event.asStartElement();
        String strStartElement = startElement.getName().getLocalPart();

        if(strStartElement.equals(PAGE_REVISION_TAG)) {
          processingRevisionTag = true;
        }

        if(!processingRevisionTag) {
          if(strStartElement.equals(PAGE_ID_TAG)) {
            pageId = Integer.parseInt(reader.nextEvent().asCharacters().getData());
          } else if(strStartElement.equals(PAGE_TITLE_TAG)) {
            title = reader.nextEvent().asCharacters().getData();
          } else if( loadAdditionalInfo  && strStartElement.equals(PAGE_REDIRECT_TAG)) {
            extractRedirectText = true;
          }
        } else {
          if(strStartElement.equals(PAGE_REVISION_TEXT_TAG)) {
            revisionTextContent = reader.getElementText();
            if(extractRedirectText) {
              targetPageId = extractRedirectId(revisionTextContent);
              extractRedirectText = false;
            } else if(loadAdditionalInfo) {
              // extract all links the text, to be used for disambiguation
              List<String> lstLinks = extractLinks(revisionTextContent);
              if(containsDisambiguation(revisionTextContent)) {
                disambiguationPageIdLinks.put(pageId, lstLinks);
              } else {
                pageIdContent.put(pageId, lstLinks);
              }
              // store text entry of page
              newDumpPageIdText.put(pageId, cleanupText(revisionTextContent));
            }
          }
        }
      }

      if (event.isEndElement()) {
        EndElement endElement = event.asEndElement();
        if (endElement.getName().getLocalPart().equals(PAGE_TAG)) {
          // process retrieved page related information depending on the dump.
          if(loadAdditionalInfo) {
            if(pageId != -1 && targetPageId != -1) {
              redirectIds.put(pageId, targetPageId);
            }
          } else if(dumpType.equals(DumpType.NEW) && actionType.equals(TargetAction.LOAD_PAGE_INFO)) {
            if(pageId != -1 && title != null) {
              targetDumpIdTitleMap.put(pageId, title);
              targetDumpTitleIdMap.put(title, pageId);
            }
          } else if(dumpType.equals(DumpType.OLD)) {
            if(pageId != -1 && title != null) {
              sourceDumpIdTitleMap.put(pageId, title);

              targetPageId = resolveRedirection(redirectIds, pageId);
              boolean redirected = (targetPageId != pageId);
              boolean disambiguated = false;
              if(!redirected && disambiguationPageIdLinks.containsKey(pageId)) {
                // If the source page is also a disambiguation page, do nothing.
                if(!containsDisambiguation(revisionTextContent)) {
                  targetPageId = disambiguate(pageId, revisionTextContent);
                  disambiguated = true;
                }
              }

              oldDumpPageIdText.put(pageId, cleanupText(revisionTextContent));
              finalIdMap.put(pageId, targetPageId);

              if(redirected) {
                redirectedPageIds.add(pageId);
              } else if(disambiguated) {
                disambiguatedPageIds.add(pageId);
              }
            }
          }
          // reset for new page
          pageId = -1;
          targetPageId = -1;
          title = null;
          processedPages++;
          if(processedPages % 100000 == 0) {
            logger_.debug("Processed " + processedPages + " page entries.");
          }
        }else if(endElement.getName().getLocalPart().equals(PAGE_REVISION_TAG)) {
          processingRevisionTag = false;
        }
      }
    }
  }

  private static int disambiguate(int pageId, String revisionTextContent) {
    List<String> lstChoices = disambiguationPageIdLinks.get(pageId);
    lstChoices = verifyList(lstChoices);
    // for each disambiguation option, get the content stored in pageContent and compute similarity
    double maxScore = 0.0;
    int result = pageId; // return the current pageId, if no disambiguations are found
    for(String pageChoice : lstChoices) {
      int pId = targetDumpTitleIdMap.get(pageChoice);
      // wiki entry in source might have been deleted in target. Hence there might be no entries in pageContent
      List<String> pageLinks = pageIdContent.get(pId);
      List<String> currentPageLinks = extractLinks(revisionTextContent);
      double score = computeSimilarity(currentPageLinks, pageLinks);
      if(score > maxScore) {
        result = pId;
        maxScore = score;
      }
    }
    return result;
  }

  private static String cleanupText(String text) {
    if(text == null) return text;
    text = text.replaceAll("\n", "");
    text = text.replaceAll("\\s+", " ");
    int maxLimit = (text.length() < MAX_TEXT_LENGTH) ? text.length() : MAX_TEXT_LENGTH;
    return text.substring(0, maxLimit);
  }

  //This method resolves redirection pages(including multiple redirections).
  //  In case of a cycle, the given id is returned i.e id is mapped on to itself.
  private static int resolveRedirection(TIntIntMap redirectIds, int redirectId) {
    TIntSet processed = new TIntHashSet();
    processed.add(redirectId);
    int itK = redirectId;
    boolean found = false;
    while(redirectIds.containsKey(itK)) {
      itK = redirectIds.get(itK);
      if(!processed.contains(itK)) {
        processed.add(itK);
      } else {
        logger_.warn("Cycle Found for id : "+ redirectId +": " + processed);
        redirectionCyclesPageIds.add(redirectId);
        found = true;
        break;
      }
    }
    if(found) return redirectId;
    return itK;
  }

  private static double computeSimilarity(List<String> list1, List<String> list2) {
    Set<String> set1 = new HashSet<>(verifyList(list1));
    Set<String> set2 = new HashSet<>(verifyList(list2));

    int sizeCurrentSet = set1.size();
    set1.retainAll(set2);
    set2.removeAll(set1);

    int union = sizeCurrentSet + set2.size();
    int intersection = set1.size();
    return ((double)intersection)/union;
  }

  private static List<String> verifyList(List<String> list) {
    return (list != null) ? list : new ArrayList<String>();
  }

  private static int extractRedirectId(String content) {
    String tmp = extractRedirectTitle(content);
    int id = -1;
    if(targetDumpTitleIdMap.containsKey(tmp)) {
      id = targetDumpTitleIdMap.get(tmp);
    }
    return id;
  }

  private static String extractRedirectTitle(String content) {
    List<String> lstLinks = extractLinks(content);
    if(lstLinks.isEmpty())
      return null;
    // for redirect text, ideally there will be only one [[ ]] in the text.
    return lstLinks.get(0);
  }

  private static List<String> extractLinks(String content) {
    Matcher redirectMatcher = pattern.matcher(content);
    List<String> lstLinks = new ArrayList<>();
    while(redirectMatcher.find()) {
      String tmp = redirectMatcher.group(1);
      int idx = tmp.indexOf('|');
      if(idx >= 0) {
        tmp = tmp.substring(0, idx).trim();
      }
      // tmp = tmp.replaceAll(" ", "_");
      lstLinks.add(tmp);
    }
    return lstLinks;
  }

  private static boolean containsDisambiguation(String content) {
    for(String dTerm : DISAMBIGUATION_TERMS) {
      if(content.contains(dTerm))
        return true;
    }
    return false;
  }

  @SuppressWarnings("static-access")
  private static Options buildCommandLineOptions() throws ParseException {
    Options options = new Options();
    options
    .addOption(OptionBuilder
        .withLongOpt("cmd")
        .withDescription(
            "Command to be executed (Currently supports only MAP command)")
            .isRequired()
            .hasArg()
            .create("c"));
    options
    .addOption(OptionBuilder
        .withLongOpt("source")
        .withDescription(
            "Old dump to be mapped")
            .hasArg()
            .withArgName("OLD_DUMP")
            .create("o"));
    options
    .addOption(OptionBuilder
        .withLongOpt("target")
        .withDescription(
            "New dump to check against")
            .hasArg()
            .withArgName("NEW_DUMP")
            .create("n"));
    options
    .addOption(OptionBuilder
        .withLongOpt("output")
        .withDescription(
            "Write to file")
            .hasArg()
            .withArgName("FILENAME")
            .create("w"));
    options.addOption(OptionBuilder.withLongOpt("help").create('h'));
    return options;
  }

  private static void printHelp(Options commandLineOptions) {
    String header = "\n\nWikipedia Revision Tools:\n\n";
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("WikiRevTools", header,
        commandLineOptions, "", true);
    System.exit(0);
  }

  private static void writeFileContent(File file, TIntIntMap hshResults, boolean includeUnchangedEntries) throws IOException {
    BufferedWriter writer = getBufferedWriter(file);
    for(int pId : hshResults.keys()) {
      String title = sourceDumpIdTitleMap.get(pId);
      String target = targetDumpIdTitleMap.get(hshResults.get(pId));
      if(!includeEntry(title, target, includeUnchangedEntries)) {
        continue;
      }
      writer.append(title + "\t" + target + "\t");
      if(redirectionCyclesPageIds.contains(pId)) {
        writer.append(redirectedCyle);
      }else if(redirectedPageIds.contains(pId)) {
        writer.append(redirectedEntry);
      } else if(disambiguatedPageIds.contains(pId)){
        writer.append(disambiguatedEntry);
        // make sure to write src text and target text
        writer.append('\t').append(oldDumpPageIdText.get(pId)).append('\t').append(newDumpPageIdText.get(pId));
      } else {
        writer.append(unchangedEntry);
      }
      writer.append("\n");
    }
    writer.flush();
    writer.close();
  }

  private static BufferedWriter getBufferedWriter(File file) throws IOException {
    // need to append entries to the file
    return new BufferedWriter(new FileWriter(file, true));
  }

  public static void main(String args[]) throws Exception {
    commandLineOptions = buildCommandLineOptions();
    CommandLineParser parser = new PosixParser();
    CommandLine cmd = null;

    try {
      cmd = parser.parse(commandLineOptions, args);
    } catch (MissingOptionException e) {
      System.out.println("\n\n" + e + "\n\n");
      printHelp(commandLineOptions);
    }
    if (cmd.hasOption("h")) {
      printHelp(commandLineOptions);
    }

    String cmdStr = "";
    cmdStr = cmd.getOptionValue('c');
    switch (cmdStr) {
      case "MAP":
        if(!cmd.hasOption('o') || !cmd.hasOption('n')) {
          System.out.println("\n\n MAP command needs OLD_DUMP and NEW_DUMP options.\n\n");
          printHelp(commandLineOptions);
        }

        String oldDump = cmd.getOptionValue('o');
        String newDump = cmd.getOptionValue('n');
        if(cmd.hasOption('w')) {
          String outputFile = cmd.getOptionValue('w');
          mapToFile(new File(oldDump), new File(newDump), new File(outputFile));
        } else {
          TIntIntMap hshResults = mapImpl(new File(oldDump), new File(newDump));
          for(int pId : hshResults.keys()) {
            String title = sourceDumpIdTitleMap.get(pId);
            String target = targetDumpIdTitleMap.get(hshResults.get(pId));
            System.out.print(title + "\t" + target + "\t");
            if(redirectedPageIds.contains(pId)) {
              System.out.print(redirectedEntry);
            } else if(disambiguatedPageIds.contains(pId)){
              System.out.print(disambiguatedEntry + "\t"+oldDumpPageIdText.get(pId)+"\t"+newDumpPageIdText.get(pId));
              // make sure to write src text and target text
            } else {
              System.out.print(unchangedEntry);
            }
            System.out.println();
          }
        }
        // done with the map
        break;
      default:
        printHelp(commandLineOptions);
    }
  }
}