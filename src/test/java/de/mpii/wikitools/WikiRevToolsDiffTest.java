package de.mpii.wikitools;

import static org.junit.Assert.assertEquals;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.junit.Test;

public class WikiRevToolsDiffTest {

  @Test
  public void testUnchangedPageEntries() throws IOException, XMLStreamException {
    // source dump
    File tmpSrcDump = File.createTempFile("wiki-src-dump", "xml");
    File tmpTargetDump = File.createTempFile("wiki-target-dump", "xml");

    // base structure - "<mediawiki><page><title></title><id><id></page></mediawiki>"
    BufferedWriter bw = new BufferedWriter(new FileWriter(tmpSrcDump));
    bw.write("<mediawiki><page><title>Test1</title><id>1</id></page><page><title>Test2</title><id>2</id></page></mediawiki>");
    bw.close();

    bw = new BufferedWriter(new FileWriter(tmpTargetDump));
    bw.write("<mediawiki><page><title>Test1</title><id>1</id></page><page><title>Test2</title><id>2</id></page></mediawiki>");
    bw.close();

    // default diff will also include all unchanged entries
    Map<String, String> hshResults = WikiRevTools.map(tmpSrcDump, tmpTargetDump);
    assertEquals(2, hshResults.size());

    // setting the flag to false will include unchanged entries
    hshResults = WikiRevTools.map(tmpSrcDump, tmpTargetDump, false);
    assertEquals(0, hshResults.size());
    // WikiRevTools.diffToFile(tmpSrcDump, tmpTargetDump, opFile);
    // WikiRevTools.diffToFile(tmpSrcDump, tmpTargetDump, opFile, opFormat);
    // remove tmp files
    tmpSrcDump.delete();
    tmpTargetDump.delete();
  }

  @Test
  public void testRenamedPageEntries() throws IOException, XMLStreamException {
    // source dump
    File tmpSrcDump = File.createTempFile("wiki-src-dump", "xml");
    File tmpTargetDump = File.createTempFile("wiki-target-dump", "xml");

    // base structure - "<mediawiki><page><title></title><id><id></page></mediawiki>"
    BufferedWriter bw = new BufferedWriter(new FileWriter(tmpSrcDump));
    bw.write("<mediawiki>"
        + "<page>"
        + "<title>Test1</title>"
        + "<id>1</id>"
        + "</page>"
        + "<page>"
        + "<title>Test2</title>"
        + "<id>2</id>"
        + "</page>"
        + "</mediawiki>");
    bw.close();

    bw = new BufferedWriter(new FileWriter(tmpTargetDump));
    bw.write("<mediawiki>"
        + "<page>"
        + "<title>Test1</title>"
        + "<id>1</id>"
        + "</page>"
        + "<page>"
        + "<title>NEW_Test2</title>"
        + "<id>2</id>"
        + "</page>"
        + "</mediawiki>");
    bw.close();

    // default diff will also include all unchanged entries
    Map<String, String> hshResults = WikiRevTools.map(tmpSrcDump, tmpTargetDump);
    assertEquals(2, hshResults.size());

    // setting the flag to false will include unchanged entries
    hshResults = WikiRevTools.map(tmpSrcDump, tmpTargetDump, false);
    assertEquals(1, hshResults.size());
    assertEquals(true, hshResults.containsKey("Test2"));
    assertEquals("NEW_Test2", hshResults.get("Test2"));
  }

  @Test
  public void testHandlingDeletedPageInTarget() throws IOException, XMLStreamException {
    // source dump
    File tmpSrcDump = File.createTempFile("wiki-src-dump", "xml");
    File tmpTargetDump = File.createTempFile("wiki-target-dump", "xml");

    // base structure - "<mediawiki><page><title></title><id><id></page></mediawiki>"
    BufferedWriter bw = new BufferedWriter(new FileWriter(tmpSrcDump));
    bw.write("<mediawiki>"
        + "<page>"
        + "<title>Test1</title>"
        + "<id>1</id>"
        + "</page>"
        + "<page>"
        + "<title>Test2</title>"
        + "<id>2</id>"
        + "</page>"
        + "<page>"
        + "<title>Test3</title>"
        + "<id>3</id>"
        + "</page>"
        + "</mediawiki>");
    bw.close();

    bw = new BufferedWriter(new FileWriter(tmpTargetDump));
    // deleted page with id 2 in the new dump -> mappig from id 2 in src shd be NULL
    bw.write("<mediawiki>"
        + "<page>"
        + "<title>Test1</title>"
        + "<id>1</id>"
        + "</page>"
        + "<page>"
        + "<title>Test3</title>"
        + "<id>3</id>"
        + "</page>"
        + "</mediawiki>");
    bw.close();
    // setting the flag to false will include unchanged entries
    Map<String, String> hshResults = WikiRevTools.map(tmpSrcDump, tmpTargetDump);
    assertEquals(true, hshResults.containsKey("Test2"));
    assertEquals(null, hshResults.get("Test2"));
  }

  @Test
  public void testSinglePageRedirection() throws IOException, XMLStreamException {
    // source dump
    File tmpSrcDump = File.createTempFile("wiki-src-dump", "xml");
    File tmpTargetDump = File.createTempFile("wiki-target-dump", "xml");

    // base structure - "<mediawiki><page><title></title><id><id></page></mediawiki>"
    BufferedWriter bw = new BufferedWriter(new FileWriter(tmpSrcDump));
    bw.write("<mediawiki>"
        + "<page>"
        + "<title>Test1</title>"
        + "<id>1</id>"
        + "</page>"
        + "<page>"
        + "<title>Test2</title>"
        + "<id>2</id>"
        + "</page>"
        + "<page>"
        + "<title>Test3</title>"
        + "<id>3</id>"
        + "</page>"
        + "</mediawiki>");
    bw.close();

    bw = new BufferedWriter(new FileWriter(tmpTargetDump));
    bw.write("<mediawiki>"
        + "<page>"
        + "<title>Test1</title>"
        + "<id>1</id>"
        + "<redirect/>"
        + "<revision>"
        + "<id>1234556</id>"
        + "<text xml:space=\"preserve\">#REDIRECT [[Test3]] {{R from CamelCase}}</text>"
        + "</revision>"
        + "</page>"
        + "<page>"
        + "<title>NEW_Test2</title>"
        + "<id>2</id>"
        + "</page>"
        + "<page>"
        + "<title>Test3</title>"
        + "<id>3</id>"
        + "</page>"
        + "</mediawiki>");
    bw.close();
    Map<String, String> hshResults = WikiRevTools.map(tmpSrcDump, tmpTargetDump);
    assertEquals(3, hshResults.size());
    assertEquals("Test3", hshResults.get("Test1"));
  }

  @Test
  public void testMultiplePageRedirection() throws IOException, XMLStreamException {
    // source dump
    File tmpSrcDump = File.createTempFile("wiki-src-dump", "xml");
    File tmpTargetDump = File.createTempFile("wiki-target-dump", "xml");

    // base structure - "<mediawiki><page><title></title><id><id></page></mediawiki>"
    BufferedWriter bw = new BufferedWriter(new FileWriter(tmpSrcDump));
    bw.write("<mediawiki>"
        + "<page>"
        + "<title>Test1</title>"
        + "<id>1</id>"
        + "</page>"
        + "<page>"
        + "<title>Test2</title>"
        + "<id>2</id>"
        + "</page>"
        + "<page>"
        + "<title>Test3</title>"
        + "<id>3</id>"
        + "</page>"
        + "<page>"
        + "<title>Test4</title>"
        + "<id>4</id>"
        + "</page>"
        + "<page>"
        + "<title>Test5</title>"
        + "<id>5</id>"
        + "</page>"
        + "<page>"
        + "<title>Test6</title>"
        + "<id>6</id>"
        + "</page>"
        + "</mediawiki>");
    bw.close();

    bw = new BufferedWriter(new FileWriter(tmpTargetDump));
    bw.write("<mediawiki>"
        + "<page>"
        + "<title>Test1</title>"
        + "<id>1</id>"
        + "<redirect/>"
        + "<revision>"
        +  "<id>1234556</id>"
        + "<text xml:space=\"preserve\">#REDIRECT [[Test3]] {{R from CamelCase}}</text>"
        + "</revision>"
        + "</page>"
        + "<page>"
        + "<title>NEW_Test2</title>"
        + "<id>2</id>"
        + "</page>"
        + "<page>"
        + "<title>Test3</title>"
        + "<id>3</id>"
        + "<redirect/>"
        + "<revision>"
        + "<id>1234556</id>"
        + "<text xml:space=\"preserve\">#REDIRECT [[Test4]] {{R from CamelCase}}</text>"
        + "</revision>"
        + "</page>"
        + "<page>"
        + "<title>Test4</title>"
        + "<id>4</id>"
        + "<redirect/>"
        + "<revision>"
        + "<id>1234556</id>"
        + "<text xml:space=\"preserve\">#REDIRECT [[Test5]] {{R from CamelCase}}</text>"
        + "</revision>"
        + "</page>"
        + "<page>"
        + "<title>Test5</title>"
        + "<id>5</id>"
        + "<redirect/>"
        + "<revision>"
        + "<id>1234556</id>"
        + "<text xml:space=\"preserve\">#REDIRECT [[Test6]] {{R from CamelCase}}</text>"
        + "</revision>"
        + "</page>"
        + "<page>"
        + "<title>Test6</title>"
        + "<id>6</id>"
        + "</page>"
        + "</mediawiki>");
    bw.close();
    Map<String, String> hshResults = WikiRevTools.map(tmpSrcDump, tmpTargetDump);
    assertEquals(6, hshResults.size());

    // 1 -> 3 -> 4 -> 5 -> 6
    assertEquals("Test6", hshResults.get("Test1"));
    assertEquals("Test6", hshResults.get("Test3"));

    assertEquals("NEW_Test2", hshResults.get("Test2"));
  }

  @Test
  public void testRedirectionCycle() throws IOException, XMLStreamException {
    System.out.println("Redirection Cycle");
    // source dump
    File tmpSrcDump = File.createTempFile("wiki-src-dump", "xml");
    File tmpTargetDump = File.createTempFile("wiki-target-dump", "xml");

    // base structure - "<mediawiki><page><title></title><id><id></page></mediawiki>"
    BufferedWriter bw = new BufferedWriter(new FileWriter(tmpSrcDump));
    bw.write("<mediawiki>"
        + "<page>"
        + "<title>Test1</title>"
        + "<id>1</id>"
        + "</page>"
        + "<page>"
        + "<title>Test2</title>"
        + "<id>2</id>"
        + "</page>"
        + "<page>"
        + "<title>Test3</title>"
        + "<id>3</id>"
        + "</page>"
        + "</mediawiki>");
    bw.close();

    bw = new BufferedWriter(new FileWriter(tmpTargetDump));
    bw.write("<mediawiki>"
        + "<page>"
        + "<title>Test1</title>"
        + "<id>1</id>"
        + "<redirect/>"
        + "<revision>"
        + "<id>1234556</id>"
        + "<text xml:space=\"preserve\">#REDIRECT [[Test3]] {{R from CamelCase}}</text>"
        + "</revision>"
        + "</page>"
        + "<page>"
        + "<title>NEW_Test2</title>"
        + "<id>2</id>"
        + "</page>"
        + "<page>"
        + "<title>Test3</title>"
        + "<id>3</id>"
        + "<redirect/>"
        + "<revision>"
        + "<id>1234556</id>"
        + "<text xml:space=\"preserve\">#REDIRECT [[Test1]] {{R from CamelCase}}</text>"
        + "</revision>"
        + "</page>"
        + "</mediawiki>");
    bw.close();
    Map<String, String> hshResults = WikiRevTools.map(tmpSrcDump, tmpTargetDump);
    assertEquals(3, hshResults.size());
    // Test1 REDIRECTS TO Test3, but since it forms a cycle, Test1 is mapped to itself
    assertEquals(true, !hshResults.get("Test1").equals("Test3"));
  }

}