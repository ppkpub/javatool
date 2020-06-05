import java.io.File;

import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Language {
  //uses https://bitbucket.org/xerial/sqlite-jdbc
  static Logger logger = LoggerFactory.getLogger(Language.class);
  private static JSONObject objLang = null;
  private static String currentLang = "";


  public static void setLang(String lang) {
    if(lang.equals("EN"))
      return;
    
    String  langFilename="./resources/static/lang/"+lang+".txt";
          
    if (!(new File(langFilename)).exists() ){
            logger.error("Not found language file : "+langFilename);
            return;
    }
        
    try{
        String  tmpKeyStr=Util.readTextFile(langFilename,Config.PPK_TEXT_CHARSET);
        if(tmpKeyStr==null){
            logger.error("Failed to read language data from "+langFilename);
            System.exit(0);        
        }
        
        objLang=new JSONObject(tmpKeyStr);
        
        currentLang=lang;
    }catch(Exception ex){
        logger.error("Failed to set language :"+ex.toString());
        System.exit(0);    
    }
  }
    
    public static String getCurrentLang(){
       return currentLang;
    }
    
    public static String getLangLabel(String enStr) {
        try{
            if(objLang !=null && objLang.has(enStr))
        return objLang.getString(enStr);
        }catch(Exception ex){
            logger.error("Failed to get language string :"+ex.toString());
        }
        
        return enStr;
    }
  
}
