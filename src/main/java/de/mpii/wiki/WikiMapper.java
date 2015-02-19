package de.mpii.wiki;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;

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

import de.mpii.wiki.common.FileUtils;
import de.mpii.wiki.dump.DumpData;
import de.mpii.wiki.dump.DumpReader;
import de.mpii.wiki.dump.DumpSettings.DumpType;
import de.mpii.wiki.dump.DumpSettings.MappedType;
import de.mpii.wiki.result.MappedResult;
import de.mpii.wiki.result.ResultGenerator;

/**
 * This class consists of static methods that operate on either individual Wikipedia dump file or
 * on multiple versions of dumps.
 *
 * @author vvenkatr
 *
 */
public class WikiMapper {

  private static Options commandLineOptions;

  private static Logger logger_ = LoggerFactory.getLogger(WikiMapper.class);

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
    List<MappedResult> results = mapImpl(oldDump, newDump);
    Map<String, String> finalMap = new HashMap<>();
    for(MappedResult result : results) {
      String source = result.getSourceTitle();
      String target = result.getTargetTitle();
      MappedType mapType = result.getMappingType();
      boolean include = true;
      logger_.debug(source +"->"+target+"("+mapType+")");
      if(mapType.equals(MappedType.UNCHANGED) && !includeUnchangedEntries) {
        include = false;        
      }
      
      if(include) {
        finalMap.put(source, target);
      }      
    }
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
    List<MappedResult> result = mapImpl(oldDump, newDump);
    logger_.debug("Writing results to file : " + output.getName());
    try{
      // just in case delete any old file
      output.delete();
      FileUtils.writeFileContent(output, result);
      logger_.debug(result.size() + " entries written to " + output.getName());
    }catch(IOException ioe) {
      logger_.error("Failed to write results to file");
    }
  }

  private static List<MappedResult> mapImpl(File oldDump, File newDump) throws IOException, XMLStreamException  {    

    XMLInputFactory factory = XMLInputFactory.newInstance();

    XMLEventReader newDumpReader = factory.createXMLEventReader(new FileReader(newDump));
    XMLEventReader oldDumpReader = factory.createXMLEventReader(new FileReader(oldDump));

    DumpData newDumpData = new DumpData(DumpType.TARGET);
    DumpData oldDumpData = new DumpData(DumpType.SOURCE);
    
    long start = System.currentTimeMillis();
    
    DumpReader.read(newDumpReader, newDumpData);
    logger_.debug("Time to scan new dump : " + (System.currentTimeMillis() - start)/1000 + " s.");

    // iterate over the source dump
    start = System.currentTimeMillis();
    DumpReader.read(oldDumpReader, oldDumpData);
    logger_.debug("Time to scan old dump : " + (System.currentTimeMillis() - start)/1000 + " s.");
    
    List<MappedResult> results = ResultGenerator.generate(oldDumpData, newDumpData);    
    return results;
  }

  @SuppressWarnings("static-access")
  private static Options buildCommandLineOptions() throws ParseException {
    Options options = new Options();
    options
    .addOption(OptionBuilder
        .withLongOpt("source")
        .withDescription(
            "Old dump to be mapped")
            .hasArg()
            .isRequired()
            .withArgName("SOURCE_DUMP")
            .create("s"));
    options
    .addOption(OptionBuilder
        .withLongOpt("target")
        .withDescription(
            "New dump to check against")
            .hasArg()
            .isRequired()
            .withArgName("TARGET_DUMP")
            .create("t"));
    options
    .addOption(OptionBuilder
        .withLongOpt("ignore-unchanged")
        .withDescription(
            "Ignore unchanged mapped entries")
            .create("i"));
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
    formatter.printHelp("WikiToolExecutor", header,
        commandLineOptions, "", true);
    System.exit(0);
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

    String srcDump = cmd.getOptionValue('s');
    String tgtDump = cmd.getOptionValue('t');
    
    // boolean ignoreUnchanged = cmd.hasOption('i');

    if(cmd.hasOption('w')) {
      String outputFile = cmd.getOptionValue('w');
      mapToFile(new File(srcDump), new File(tgtDump), new File(outputFile));
    } else {
      List<MappedResult> results = mapImpl(new File(srcDump), new File(tgtDump));
      FileUtils.writeFileContent(null, results);          
    }
    // done with the map - print stats
    // ingored source redirects
    // ignored source disambiguations
    // final count of Page->Disambiguation entries.
  }
}