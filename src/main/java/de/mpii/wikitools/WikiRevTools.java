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
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
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

  // Wikipedia page revision text will be matched against this pattern to retrieve name links.
  private static Pattern pattern = Pattern.compile("\\[\\[(.*?)\\]\\]");

  //maps to store id, title relation
  private static TIntObjectHashMap<String> idTitleMap;
  private static TObjectIntHashMap<String> titleIdMap;

  // map to store Redirections
  private static TIntIntHashMap redirectIds;

  // map to store final-map-results
  private static Map<String, String> finalMap;

  private static Options commandLineOptions;

  private static Logger logger_ = LoggerFactory.getLogger(WikiRevTools.class);

  private enum DumpType {
    OLD, NEW;
  }

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
    idTitleMap = new TIntObjectHashMap<String>();
    titleIdMap = new TObjectIntHashMap<String>();
    redirectIds = new TIntIntHashMap();
    finalMap = new HashMap<String, String>();

    XMLInputFactory factory = XMLInputFactory.newInstance();
    XMLEventReader newDumpReader = factory.createXMLEventReader(new FileReader(newDump));
    XMLEventReader oldDumpReader = factory.createXMLEventReader(new FileReader(oldDump));

    long start = System.currentTimeMillis();
    // iterate over target and build id-title and title-id relations
    processReader(newDumpReader, DumpType.NEW, TargetAction.LOAD_PAGE_INFO, includeUnchangedEntries);
    logger_.debug("Time to scan older version of dump : " + (System.currentTimeMillis() - start)/1000 + " s.");

    // reload and iterate once again over target and construct redirects.
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
    Matcher redirectMatcher;

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
          if(extractRedirectText && strStartElement.equals(PAGE_REVISION_TEXT_TAG)) {
            // process revision tag only for redirection.
            redirectMatcher = pattern.matcher(reader.getElementText());
            while(redirectMatcher.find()) {
              redirectTitle = redirectMatcher.group(1);
            }
            extractRedirectText = false;
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
              String targetTitle = idTitleMap.get(redirectId);
              // check whether the source dump's id is pointing to same title
              if(!title.equals(targetTitle) || includeUnchangedEntries) {
                finalMap.put(title, targetTitle);
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
        } else if(endElement.getName().getLocalPart().equals(PAGE_REVISION_TAG)) {
          processingRevisionTag = false;
        }
      }
    }
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
      writer.append(e.getKey() + "\t" + e.getValue()+"\n");
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
          for(Entry<String, String> e : hshResults.entrySet()) {
            System.out.println(e.getKey() + "\t" + e.getValue());
          }
        }
        // done with the map
        break;
      default:
        printHelp(commandLineOptions);
    }
  }
}