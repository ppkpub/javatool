import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class APoverPTTP {
  static Logger logger = LoggerFactory.getLogger(APoverPTTP.class);

  public static String fetchInterest(String ap_url, String interest) {
    if(ap_url==null)
        return null;
    
    String str_ap_resp_json=null;
    String ap_fetch_url=ap_url;
    
    try{
      if( ap_fetch_url.endsWith("/")){
        ap_fetch_url += "pttp("+Util.bytesToHexString(interest.getBytes(Config.PPK_TEXT_CHARSET))+")"+Config.PPK_URI_RESOURCE_MARK;
      }
      logger.info("fetchInterest("+ap_fetch_url+") ...");
    
      //存在循环锁的可能，需进一步完善处理
      str_ap_resp_json = Util.fetchUriContent(ap_fetch_url);
    }catch(Exception e){
      logger.error("fetchInterest("+ap_fetch_url+") error: "+e.toString());
    }
    
    //System.out.println("APoverPTTP.fetchInterest() str_ap_resp_json:"+str_ap_resp_json);
    return str_ap_resp_json;
  }
}