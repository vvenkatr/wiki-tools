package de.mpii.wiki.common;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;

import de.mpii.wiki.dump.DumpSettings.MappedType;
import de.mpii.wiki.result.MappedResult;

public class FileUtils {
  public static void writeFileContent(File file, List<MappedResult> results) throws IOException {
    BufferedWriter writer = getBufferedWriter(file);

    for(MappedResult result : results) {
      String srcTitle = result.getSourceTitle();
      String tgtTitle = result.getTargetTitle();
      MappedType mapType = result.getMappingType();
      writer.append(srcTitle + "\t" + tgtTitle + "\t" + mapType.toString());
      
      if(mapType.equals(MappedType.DISAMBIGUATED)) {
        writer.append("\t").append(result.getSourceText()).append("\t").append(result.getTargetText());
      }
      writer.append("\n");
    }

    writer.flush();
    writer.close();
  }

  private static BufferedWriter getBufferedWriter(File file) throws IOException {
    if(file == null) {
      // write to standard output
      return new BufferedWriter(new OutputStreamWriter(System.out));
    }
    // need to append entries to the file
    return new BufferedWriter(new FileWriter(file, true));
  }
}
