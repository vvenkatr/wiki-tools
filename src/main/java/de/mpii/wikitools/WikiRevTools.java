package de.mpii.wikitools;

import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

public class WikiRevTools {

  private static final String PAGE_TAG = "page";

  private static final String PAGE_ID_TAG = "id";

  private static final String PAGE_TITLE_TAG = "title";
  
  private static final String PAGE_REVISION_TAG = "revision";

  private static final String PAGE_REDIRECT_TAG = "redirect";

  private static final String PAGE_REVISION_TEXT_TAG = "text";
  
  private static Pattern pattern = Pattern.compile("\\[\\[(.*?)\\]\\]");
    
  public static Map<String, String> diff(File source, File target) throws IOException, XMLStreamException {
    return diff(source, target, true);
  }

  public static Map<String, String> diff(File source, File target, boolean includeUnchangedEntries) throws IOException, XMLStreamException {
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
        }else if(strEndElement.equals(PAGE_REVISION_TAG)) {
          processingRevisionTag = false;
        }
      }
    }

    // iterate over target again and resolve REDIRECTS ( and DISAMBIGUATION )
    targetReader = factory.createXMLEventReader(new FileReader(target));
    boolean extractRedirectText = false;
    Matcher redirectMatcher;
    int redirectId = -1;
    String redirectTitle = null;
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
        }else if(endElement.getName().getLocalPart().equals(PAGE_REVISION_TAG)) {
          processingRevisionTag = false;          
        }
      }
    }

    // finally iterate over source dump file and construct the final change map
    Map<String, String> finalMap = new HashMap<String, String>();
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
        redirectId = pageId;
        while(redirectIds.contains(redirectId)) {
          redirectId = redirectIds.get(redirectId);
        }
        String targetTitle;
        if(redirectId != pageId) {
          targetTitle = idTitleMap.get(redirectId);
        } else {
          targetTitle = idTitleMap.get(pageId);  
        }
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
        }
      }
    }
    return finalMap;
  }

  public static void diffToFile(File source, File target, File output) throws IOException, XMLStreamException {
    diffToFile(source, target, output, false);
  }

  public static void diffToFile(File source, File target, File output, boolean includeUnchangedEntries) throws IOException, XMLStreamException {
    diff(source, target, includeUnchangedEntries);
  }
}