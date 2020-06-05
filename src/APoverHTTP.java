import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONArray; 
import org.json.JSONObject;
import org.json.JSONException;

import com.googlecode.jsonrpc4j.JsonRpcHttpClient;

public class APoverHTTP {
  static Logger logger = LoggerFactory.getLogger(APoverHTTP.class);

  public static String fetchInterest(String ap_url, String interest) {
    String str_ap_resp_json=null;
    String ap_fetch_url=ap_url+"?"+Config.PTTP_INTEREST+"="+java.net.URLEncoder.encode(interest);
    logger.info("fetchInterest("+ap_fetch_url+") ...");
    
    try{
      str_ap_resp_json=new String(CommonHttpUtil.getInstance().getContentFromUrl(ap_fetch_url));
    }catch(Exception e){
      logger.error("fetchInterest("+ap_fetch_url+") error: "+e.toString());
    }
    
    //System.out.println("APoverHTTP.fetchInterest() str_ap_resp_json:"+str_ap_resp_json);
    return str_ap_resp_json;
  }
}