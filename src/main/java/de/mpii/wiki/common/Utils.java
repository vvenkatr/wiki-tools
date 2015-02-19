package de.mpii.wiki.common;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Utils {
  
  //Wikipedia page revision text will be matched against this pattern to retrieve name links.
  private static final Pattern pattern = Pattern.compile("\\[\\[(.*?)\\]\\]");

  // Maximum length of wiki page text to use in output
  private static final int MAX_TEXT_LENGTH = 1000;
 
  public static boolean containsAny(String text, String[] words) {
    for(String ele : words) {
      if(text.contains(ele) || text.contains(ele.toLowerCase())) {
        return true;
      }
    }
    return false;
  }
  
  public static List<String> extractLinks(String content) {
    List<String> lstLinks = new ArrayList<>();
    if(content == null || content.equals("")) {
      return lstLinks;
    }
    
    Matcher redirectMatcher = pattern.matcher(content);
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
  
  public static String cleanAndCompressText(String text) {
    if(text == null) return text;
    text = text.replaceAll("\n", "");
    text = text.replaceAll("\\s+", " ");
    int maxLimit = (text.length() < MAX_TEXT_LENGTH) ? text.length() : MAX_TEXT_LENGTH;
    return text.substring(0, maxLimit);
  }
  
  public static List<String> verifyList(List<String> list) {
    return (list != null) ? list : new ArrayList<String>();
  }

}
