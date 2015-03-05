package de.mpii.wiki.page;

import de.mpii.wiki.page.handlers.ContentDisambiguationHandler;
import de.mpii.wiki.page.handlers.NormalHandler;
import de.mpii.wiki.page.handlers.Handler;
import de.mpii.wiki.page.handlers.RedirectsHandler;
import de.mpii.wiki.page.handlers.TitleDisambiguationHandler;


public class PageIdentifier {
  
  
  private static final Handler redirectHandler = new RedirectsHandler();
  
  private static final Handler contentDisambigHandler = new ContentDisambiguationHandler();
  
  private static final Handler titleDisambigHandler = new TitleDisambiguationHandler();
  
  public static Handler getHandler(String title, String content) {
    
    if(redirectHandler.canHandle(content)) {
      return redirectHandler;
    }
    
    if(contentDisambigHandler.canHandle(content)) {
      return contentDisambigHandler;
    }
    
    
    if(titleDisambigHandler.canHandle(title)) {
      return titleDisambigHandler;
    }
    
    return new NormalHandler();
  }
}
