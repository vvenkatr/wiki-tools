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

public class WikiRevTools {

  private static final String PAGE_TAG = "page";

  private static final String PAGE_ID_TAG = "id";

  private static final String PAGE_TITLE_TAG = "title";

  private static final String PAGE_REVISION_TAG = "revision";

  private static final String PAGE_REDIRECT_TAG = "redirect";

  private static final String PAGE_REVISION_TEXT_TAG = "text";

  private static Pattern pattern = Pattern.compile("\\[\\[(.*?)\\]\\]");

  private static Options commandLineOptions;

  private static Logger logger_ = LoggerFactory.getLogger(WikiRevTools.class);

  public static Map<String, String> map(File source, File target) throws IOException, XMLStreamException {
    return map(source, target, true);
  }

  public static Map<String, String> map(File source, File target, boolean includeUnchangedEntries) throws IOException, XMLStreamException {
    // maps to store id, title relation
    TIntObjectHashMap<String> idTitleMap = new TIntObjectHashMap<String>();
    TObjectIntHashMap<String> titleIdMap = new TObjectIntHashMap<String>();

    // map to store Redirections
    TIntIntHashMap redirectIds = new TIntIntHashMap();

    // iterate over target and build id-title and title-id relations
    XMLInputFactory factory = XMLInputFactory.newInstance();
    XMLEventReader targetReader = factory.createXMLEventReader(new FileReader(target));
    XMLEventReader sourceReader = factory.createXMLEventReader(new FileReader(source));

    // variables to store page related information
    int pageId = -1;
    String title = null;
    boolean processingRevisionTag = false;
    XMLEvent event = null;
    /*
     * During the first pass over the target :
     *  - Ignore revision tag and all its children (including <id>, text)
     *  - Load Id,Title under Page tag
     */

    // variables for tracking processed page
    int processedPages = 0;
    long start = System.currentTimeMillis();
    logger_.info("Scanning Target for Page Id, Title info...");
    while (targetReader.hasNext()) {
      event = targetReader.nextEvent();
      if(event.isStartDocument() || event.isEndDocument()) {
        // do nothing
      } else if (event.isStartElement()) {
        StartElement startElement = event.asStartElement();
        String strStartElement = startElement.getName().getLocalPart();
        if(strStartElement.equals(PAGE_REVISION_TAG)) {
          processingRevisionTag = true;
        }

        if(!processingRevisionTag) {
          if(strStartElement.equals(PAGE_ID_TAG)) {
            pageId = Integer.parseInt(targetReader.nextEvent().asCharacters().getData());
          } else if(strStartElement.equals(PAGE_TITLE_TAG)) {
            title = targetReader.nextEvent().asCharacters().getData();
          }
        }
      } else if(event.isEndElement()) {
        EndElement endElement = event.asEndElement();
        String strEndElement = endElement.getName().getLocalPart();
        if (strEndElement.equals(PAGE_TAG)) {
          // once we reach end of page, we can update the id and title retrieved for the page in map
          idTitleMap.put(pageId, title);
          titleIdMap.put(title, pageId);

          // reset for new page
          pageId = -1;
          title = null;
          processedPages++;
          if(processedPages % 100000 == 0) {
            logger_.debug("Processed " + processedPages + " page entries.");
          }
        }else if(strEndElement.equals(PAGE_REVISION_TAG)) {
          processingRevisionTag = false;
        }
      }
    }
    logger_.info("Time to scan target (1st scan) : " + (System.currentTimeMillis() - start) / 1000 + " s.");
    processedPages = 0;

    // iterate over target again and resolve REDIRECTS ( and DISAMBIGUATION )
    targetReader = factory.createXMLEventReader(new FileReader(target));
    boolean extractRedirectText = false;
    Matcher redirectMatcher;
    int redirectId = -1;
    String redirectTitle = null;
    start = System.currentTimeMillis();
    while (targetReader.hasNext()) {
      event = targetReader.nextEvent();
      if (event.isStartElement()) {
        StartElement startElement = event.asStartElement();
        String startElementName = startElement.getName().getLocalPart();

        if(!processingRevisionTag) {
          if(startElementName.equals(PAGE_ID_TAG)) {
            pageId = Integer.parseInt(targetReader.nextEvent().asCharacters().getData());
          } else if(startElementName.equals(PAGE_REDIRECT_TAG)) {
            extractRedirectText = true;
          } else if(startElementName.equals(PAGE_REVISION_TAG)) {
            processingRevisionTag = true;
          }
        } else {
          // within revision tag : look out for redirection
          if(extractRedirectText && startElementName.equals(PAGE_REVISION_TEXT_TAG)) {
            redirectMatcher = pattern.matcher(targetReader.getElementText());
            while(redirectMatcher.find()) {
              redirectTitle = redirectMatcher.group(1);
            }
            extractRedirectText = false;
          }
        }
      }

      if(pageId != -1 && redirectTitle != null) {
        redirectId = titleIdMap.get(redirectTitle);
        redirectIds.put(pageId, redirectId);
        redirectTitle = null;
      }

      if (event.isEndElement()) {
        EndElement endElement = event.asEndElement();
        if (endElement.getName().getLocalPart().equals(PAGE_TAG)) {
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

    logger_.info("Time to scan target (2nd Scan - Redirects,Disambiguations) : " + (System.currentTimeMillis() - start) / 1000 + " s.");
    // finally iterate over source dump file and construct the final change map
    processedPages = 0;
    Map<String, String> finalMap = new HashMap<String, String>();
    start = System.currentTimeMillis();
    while(sourceReader.hasNext()) {
      event = sourceReader.nextEvent();
      if (event.isStartElement()) {
        StartElement startElement = event.asStartElement();

        if(pageId == -1 && startElement.getName().getLocalPart().equals(PAGE_ID_TAG)) {
          pageId = Integer.parseInt(sourceReader.nextEvent().asCharacters().getData());
        } else if( title == null && startElement.getName().getLocalPart().equals(PAGE_TITLE_TAG)) {
          title = sourceReader.nextEvent().asCharacters().getData();
        }
      }

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

      if (event.isEndElement()) {
        EndElement endElement = event.asEndElement();
        if (endElement.getName().getLocalPart().equals(PAGE_TAG)) {
          // reset for new page
          pageId = -1;
          title = null;
          processedPages++;
          if(processedPages % 100000 == 0) {
            logger_.debug("Processed " + processedPages + " page entries.");
          }
        }
      }
    }
    logger_.info("Time to scan source dump : " + ((System.currentTimeMillis() - start) / 1000) + " s.");
    return finalMap;
  }

  /*
   * This method resolves redirection pages(including multiple redirections).
   * In case of a cycle, the given id is returned i.e id is mapped on to itself.
   */
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

  public static void mapToFile(File source, File target, File output) throws IOException, XMLStreamException {
    // by default, include the unchanged entries as well.
    mapToFile(source, target, output, true);
  }

  public static void mapToFile(File source, File target, File output, boolean includeUnchangedEntries) throws IOException, XMLStreamException {
    Map<String, String> result = map(source, target, includeUnchangedEntries);
    logger_.debug("Writing results to file : " + output.getName());
    try{
      // just in case delete any old file
      output.delete();
      writeFileContent(output, result);
      System.out.println("Output written");
    }catch(IOException ioe) {
      logger_.info("Failed to write results to file");
    }
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
            "Source dump to be mapped")
            .hasArg()
            .withArgName("SOURCEDUMP")
            .create("s"));
    options
    .addOption(OptionBuilder
        .withLongOpt("target")
        .withDescription(
            "Target dump to be check")
            .hasArg()
            .withArgName("TARGETDUMP")
            .create("t"));
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
    String header = "\n\nWiki Revision Tool:\n\n";
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
        if(!cmd.hasOption('s') || !cmd.hasOption('t')) {
          System.out.println("\n\n MAP command needs SOURCE and TARGET options.\n\n");
          printHelp(commandLineOptions);
        }

        String srcDump = cmd.getOptionValue('s');
        String targetDump = cmd.getOptionValue('t');
        if(cmd.hasOption('w')) {
          String outputFile = cmd.getOptionValue('w');
          mapToFile(new File(srcDump), new File(targetDump), new File(outputFile));
        } else {
          Map<String, String> hshResults = map(new File(srcDump), new File(targetDump));
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