package de.mpii.wiki;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

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

import de.mpii.wiki.dump.DumpData;
import de.mpii.wiki.dump.DumpReader;
import de.mpii.wiki.dump.DumpSettings.DumpType;

public class WikiPageFinder {

  private static Options commandLineOptions;

  @SuppressWarnings("static-access")
  private static Options buildCommandLineOptions() throws ParseException {
    Options options = new Options();
    options
    .addOption(OptionBuilder
        .withLongOpt("title")
        .withDescription(
            "Wiki Page title to search for")
            .hasArg()
            .isRequired()
            .withArgName("PAGE_TITLE")
            .create("t"));
    options
    .addOption(OptionBuilder
        .withLongOpt("dump")
        .withDescription(
            "Dump to be searched")
            .hasArg()
            .isRequired()
            .withArgName("DUMP_FILE")
            .create("d"));
    options.addOption(OptionBuilder.withLongOpt("help").create('h'));
    return options;
  }

  private static void printHelp(Options commandLineOptions) {
    String header = "\n\nWikipedia Revision Tools:\n\n";
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("WikiPageFinder", header,
        commandLineOptions, "", true);
    System.exit(0);
  }

  private static void search(String title, String dump) throws IOException, XMLStreamException {
    File file = new File(dump);
    XMLInputFactory factory = XMLInputFactory.newInstance();

    XMLEventReader dumpReader = factory.createXMLEventReader(new FileReader(file));
    
    DumpData data = new DumpData(DumpType.TARGET);
    
    DumpReader.search(dumpReader, data, title);        
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

    String title = cmd.getOptionValue('t');
    String dump = cmd.getOptionValue('d');
    
    search(title, dump);
    
  }
}
