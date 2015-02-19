package de.mpii.wiki.dump;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DumpReader {
  //Xml markups used in Wikipedia dump file.
  private static final String PAGE_TAG = "page";
  private static final String PAGE_ID_TAG = "id";
  private static final String PAGE_TITLE_TAG = "title";
  private static final String PAGE_REVISION_TAG = "revision";
  private static final String PAGE_REVISION_TEXT_TAG = "text";

  private static Logger logger_ = LoggerFactory.getLogger(DumpReader.class);

  public static void read(XMLEventReader reader, DumpData data) throws XMLStreamException {
    // basic page info
    int pageId = -1;
    String title = null;
    String pageText = null;

    boolean withinRevisionTag = false;

    while (reader.hasNext()) {
      XMLEvent event = reader.nextEvent();
      if (event.isStartElement()) {
        StartElement startElement = event.asStartElement();
        String strStartElement = startElement.getName().getLocalPart();

        if(strStartElement.equals(PAGE_REVISION_TAG)) {
          withinRevisionTag = true;
        }

        if(!withinRevisionTag) {
          switch (strStartElement) {
            case PAGE_ID_TAG:
              pageId = Integer.parseInt(reader.nextEvent().asCharacters().getData());
              break;
            case PAGE_TITLE_TAG:
              title = reader.nextEvent().asCharacters().getData();
              break;
            default:
              break;
          }
        } else {
          if(strStartElement.equals(PAGE_REVISION_TEXT_TAG)) {
            pageText = reader.getElementText();            
          }
        }
      }

      if (event.isEndElement()) {
        EndElement endElement = event.asEndElement();
        String strEndElement = endElement.getName().getLocalPart();
        switch (strEndElement) {
          case PAGE_REVISION_TAG:
            withinRevisionTag = false;
            break;
          case PAGE_TAG:
            // process retrieved page related information depending on the dump.
            if(pageId == -1 || title == null) {
              //  || pageText == null || pageText.equals("")
              logger_.warn("Invalid Page Entry");
            } else {
              logger_.debug("Extracted page : " + title + "(id : " + pageId + ")");
              data.addPageEntry(pageId, title, pageText);
            }
            
            // reset
            pageId = -1;
            title = null;
            pageText = null;            
            break;
          default:
            break;
        }
      }      
    }
//    data.resolveAdditionalInfo();
  } 
}