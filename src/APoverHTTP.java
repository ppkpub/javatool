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
    String ap_fetch_url=ap_url+"?pttp_interest="+java.net.URLEncoder.encode(interest);
    logger.info("APoverHTTP.fetchInterest("+ap_fetch_url+") ...");
    
    try{
      URL url = new URL(ap_fetch_url);
      HttpURLConnection.setFollowRedirects(true);  
      HttpURLConnection hc = (HttpURLConnection) url.openConnection();  
      hc.setRequestMethod("GET");  
      hc.addRequestProperty("User-Agent", Config.appName+" "+Config.version); 
      hc.setRequestProperty("Connection", "keep-alive");  
      hc.setRequestProperty("Cache-Control", "no-cache");  
      hc.setDoOutput(true);
      hc.setReadTimeout(5*1000);
      hc.connect();
      
      int httpStatusCode = hc.getResponseCode();
      if (httpStatusCode == HttpURLConnection.HTTP_OK) {
        //通过输入流获取二进制数据
        InputStream inStream = hc.getInputStream();
        //得到二进制数据，以二进制封装得到数据，具有通用性
        byte[] data = Util.readInputStream(inStream);
        
        if(data!=null){
          str_ap_resp_json=new String(data);
        }
      }
    }catch(Exception e){
      logger.error("APoverHTTP.fetchInterest("+ap_fetch_url+") error: "+e.toString());
    }
    
    //System.out.println("APoverHTTP.fetchInterest() str_ap_resp_json:"+str_ap_resp_json);
    return str_ap_resp_json;
  }
}