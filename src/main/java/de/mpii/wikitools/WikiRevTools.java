package de.mpii.wikitools;

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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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

  // Maximum length of wiki page text to use in output
  private static final int MAX_TEXT_LENGTH = 1000;

  // Wikipedia page revision text will be matched against this pattern to retrieve name links.
  private static Pattern pattern = Pattern.compile("\\[\\[(.*?)\\]\\]");

  //maps to store id, title relation
  private static TIntObjectHashMap<String> idTitleMap;
  private static TObjectIntHashMap<String> titleIdMap;

  // map to store Redirections
  private static TIntIntHashMap redirectIds;

  // map to store final-map-results
  private static Map<String, String> finalMap;

  // map to store title and a portion of original text entry from new(target) dump(needed only for writing to file)
  private static Map<String, String> newDumpPageText;

  //map to store title and a portion of original text entry from old(source) dump(needed only for writing to file)
  private static Map<String, String> oldDumpPageText;

  // set to store redirected titles for verification before writing to file
  private static Set<String> redirectedTitles;

  // set to store disambiguated titles for verification before writing to file
  private static Set<String> disambiguatedTitles;

  /* The following two maps combined together store title -> list of titles of all page entries
   * A map to store any disambiguation entry along with all provided links.
   * A map to store all the links in the current non-disambiguation page. (To resolve disambiguation)
   */
  private static Map<String, List<String>> disambiguationPageLinks;

  private static Map<String, List<String>> pageContent;

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
    init();

    XMLInputFactory factory = XMLInputFactory.newInstance();
    XMLEventReader newDumpReader = factory.createXMLEventReader(new FileReader(newDump));
    XMLEventReader oldDumpReader = factory.createXMLEventReader(new FileReader(oldDump));

    long start = System.currentTimeMillis();
    // iterate over target and build id-title and title-id relations
    processReader(newDumpReader, DumpType.NEW, TargetAction.LOAD_PAGE_INFO, includeUnchangedEntries);
    logger_.debug("Time to scan older version of dump : " + (System.currentTimeMillis() - start)/1000 + " s.");

    // reload and iterate once again over target and construct redirects & disambiguation.
    start = System.currentTimeMillis();
    newDumpReader = factory.createXMLEventReader(new FileReader(newDump));
    processReader(newDumpReader, DumpType.NEW, TargetAction.LOAD_REDIRECTS_DISAMBIGUATIONS, includeUnchangedEntries);
    logger_.debug("Time to re-scan older version of dump : " + (System.currentTimeMillis() - start)/1000 + " s.");

    // finally, iterate over the source dump and build the output mapping.
    // Passing *null* as it will not be considered at all for SOURCE
    start = System.currentTimeMillis();
    processReader(oldDumpReader, DumpType.OLD, null, includeUnchangedEntries);
    logger_.debug("Time to scan new version of dump : " + (System.currentTimeMillis() - start)/1000 + " s.");
    return finalMap;
  }

  private static void init() {
    idTitleMap = new TIntObjectHashMap<String>();
    titleIdMap = new TObjectIntHashMap<String>();
    redirectIds = new TIntIntHashMap();
    finalMap = new HashMap<String, String>();
    disambiguationPageLinks = new HashMap<>();
    pageContent = new HashMap<>();
    redirectedTitles = new HashSet<>();
    disambiguatedTitles = new HashSet<>();
    newDumpPageText = new HashMap<>();
    oldDumpPageText = new HashMap<>();
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
    Map<String, String> result = map(oldDump, newDump, includeUnchangedEntries);
    logger_.debug("Writing results to file : " + output.getName());
    try{
      // just in case delete any old file
      output.delete();
      writeFileContent(output, result);
      logger_.debug(result.size() + " entries written to " + output.getName());
    }catch(IOException ioe) {
      logger_.error("Failed to write results to file");
    }
  }

  // private helper methods

  private static void processReader(XMLEventReader reader,
      DumpType dumpType, TargetAction actionType,
      boolean includeUnchangedEntries) throws XMLStreamException {

    int processedPages = 0;
    int pageId = -1;
    int redirectId = -1;
    String title = null;
    String redirectTitle = null;
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
            // process revision tag for redirection and disambiguation.
            revisionTextContent = reader.getElementText();
            if(extractRedirectText) {
              redirectTitle = extractRedirectTitle(revisionTextContent);
              extractRedirectText = false;
            } else if(loadAdditionalInfo) {
              // ok. now, extract all links the text, to be used for disambiguation
              // TODO: verify the possibility of having current title in some other page's disambiguation
              // if so, need to store the current disambiguation title's content in page content.
              List<String> lstLinks = extractLinks(revisionTextContent);
              if(containsDisambiguation(revisionTextContent)) {
                disambiguationPageLinks.put(title, lstLinks);
              } else {
                pageContent.put(title, lstLinks);
              }
              // store text entry of page
              newDumpPageText.put(title, cleanupText(revisionTextContent));
            }
          }
        }
      }

      if (event.isEndElement()) {
        EndElement endElement = event.asEndElement();
        if (endElement.getName().getLocalPart().equals(PAGE_TAG)) {
          // process retrieved page related information depending on the dump.
          if(loadAdditionalInfo) {
            if(pageId != -1 && redirectTitle != null) {
              redirectId = titleIdMap.get(redirectTitle);
              redirectIds.put(pageId, redirectId);
              redirectTitle = null;
            }
          } else if(dumpType.equals(DumpType.NEW) && actionType.equals(TargetAction.LOAD_PAGE_INFO)) {
            if(pageId != -1 && title != null) {
              idTitleMap.put(pageId, title);
              titleIdMap.put(title, pageId);
            }
          } else if(dumpType.equals(DumpType.OLD)) {
            if(pageId != -1 && title != null) {
              // check whether this id is redirected to another element
              redirectId = resolveRedirection(redirectIds, pageId);
              boolean redirected = (redirectId != pageId);
              String targetTitle = idTitleMap.get(redirectId);

              boolean disambiguated = false;
              if(!redirected && disambiguationPageLinks.containsKey(targetTitle)) {
                targetTitle = disambiguate(targetTitle, revisionTextContent);
                disambiguated = true;
              }

              oldDumpPageText.put(title, cleanupText(revisionTextContent));

              if(!title.equals(targetTitle) || includeUnchangedEntries) {
                finalMap.put(title, targetTitle);
              }

              // keep track of
              if(redirected) {
                redirectedTitles.add(title);
              } else if(disambiguated) {
                disambiguatedTitles.add(title);
              }

              pageId = -1;
              title = null;
            }
          }
          // reset for new page
          pageId = -1;
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

  private static String disambiguate(String targetTitle, String revisionTextContent) {
    // logger_.debug("Disambiguating : " + targetTitle);
    List<String> lstChoices = disambiguationPageLinks.get(targetTitle);
    lstChoices = verifyList(lstChoices);
    // for each disambiguation option, get the content stored in pageContent
    // and compute similarity
    double maxScore = 0.0;
    String result = targetTitle; // return the current targetTitle, if no disambiguations are found
    for(String pageChoice : lstChoices) {
      // wiki entry in source might have been deleted in target. Hence there might be no entries in pageContent
      List<String> pageLinks = pageContent.get(pageChoice);
      List<String> currentPageLinks = extractLinks(revisionTextContent);
      double score = computeSimilarity(currentPageLinks, pageLinks);
      // logger_.debug("Score for " + pageChoice + " : " + score);
      if(score > maxScore) {
        result = pageChoice;
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
  private static int resolveRedirection(TIntIntHashMap redirectIds, int redirectId) {
    TIntSet processed = new TIntHashSet();
    processed.add(redirectId);
    int itK = redirectId;
    boolean found = false;
    while(redirectIds.containsKey(itK)) {
      itK = redirectIds.get(itK);
      if(!processed.contains(itK)) {
        processed.add(itK);
      } else {
        logger_.debug("Cycle Found for id : "+ redirectId +": " + processed);
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

  private static String extractRedirectTitle(String content) {
    List<String> lstLinks = extractLinks(content);
    if(lstLinks.isEmpty())
      return null;
    // for redirect text, ideally there will be only one [[ ]] in the text.
    logger_.debug("Extracted Redirection title : " + lstLinks);
    return lstLinks.get(0);
  }

  private static List<String> extractLinks(String content) {
    Matcher redirectMatcher = pattern.matcher(content);
    List<String> lstLinks = new LinkedList<>();
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

  private static void writeFileContent(File file, Map<String, String> hshResults) throws IOException {
    BufferedWriter writer = getBufferedWriter(file);
    for(Entry<String, String> e : hshResults.entrySet()) {
      String title = e.getKey();
      String target = e.getValue();
      writer.append(title + "\t" + target + "\t");
      if(redirectedTitles.contains(title)) {
        writer.append('R');
      } else if(disambiguatedTitles.contains(title)){
        writer.append('D');
        // make sure to write src text and target text
        writer.append('\t').append(oldDumpPageText.get(title)).append('\t').append(newDumpPageText.get(target));
      } else {
        writer.append('U');
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
          Map<String, String> hshResults = map(new File(oldDump), new File(newDump));
          System.out.println(disambiguatedTitles);
          for(Entry<String, String> e : hshResults.entrySet()) {
            String title = e.getKey();
            String target = e.getValue();
            System.out.print(title + "\t" + target + "\t");
            if(redirectedTitles.contains(title)) {
              System.out.print('R');
            } else if(disambiguatedTitles.contains(title)){
              System.out.print("D\t"+oldDumpPageText.get(title)+"\t"+newDumpPageText.get(target));
              // make sure to write src text and target text
            } else {
              System.out.print('U');
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