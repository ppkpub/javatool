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


  public static JSONObject fetchInterest(String ap_url, String interest) {
    JSONObject  obj_ap_resp=new JSONObject();
    String ap_fetch_url=ap_url+"?ppk_ap_interest="+java.net.URLEncoder.encode(interest);
    logger.info("PPkURI.fetchInterest("+ap_fetch_url+") ...");
    
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
      
      int code = hc.getResponseCode();
      if (code == HttpURLConnection.HTTP_OK) {
          String tmp_str=hc.getHeaderField("Content-Type"); 
          if(tmp_str!=null)
            obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_TYPE,tmp_str);
          
          //tmp_str=hc.getHeaderField("Content-Length"); 
          //if(tmp_str!=null)
          //  obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_LENGTH,tmp_str);

          tmp_str=hc.getHeaderField(Config.JSON_KEY_PPK_SIGN); 
          if(tmp_str!=null){
            obj_ap_resp.put(Config.JSON_KEY_PPK_SIGN,new JSONObject(tmp_str) );
          }else{
            String cookieskey = "Set-Cookie";  
            Map<String, List<String>> maps = hc.getHeaderFields(); 

            List<String> tmp_list = maps.get(cookieskey);  
            if(tmp_list!=null){
              Iterator<String> it = tmp_list.iterator();  
              while(it.hasNext()){  
                String tmp_cookie=it.next();
                //System.out.println("tmp_cookie: "+tmp_cookie);

                if(tmp_cookie.substring(0,Config.JSON_KEY_PPK_SIGN.length()+1).equalsIgnoreCase(Config.JSON_KEY_PPK_SIGN+"=")){
                  obj_ap_resp.put( 
                      Config.JSON_KEY_PPK_SIGN , 
                      new JSONObject( java.net.URLDecoder.decode(tmp_cookie.substring(Config.JSON_KEY_PPK_SIGN.length()+1)) )
                    );
                }
              } 
            }
          }
          
          //通过输入流获取二进制数据
          InputStream inStream = hc.getInputStream();
          //得到二进制数据，以二进制封装得到数据，具有通用性
          byte[] data = Util.readInputStream(inStream);
          
          if(data!=null){
            if(!obj_ap_resp.has(Config.JSON_KEY_PPK_SIGN) && "text/html".equalsIgnoreCase(obj_ap_resp.optString(Config.JSON_KEY_PPK_CHUNK_TYPE,"")) ){
              //如果header和cookie里都没有ppk特征签名字段，则尝试从网页正文里提取
              String str_resp=new String(data);
              String ppk_sign_mark="<!--"+Config.JSON_KEY_PPK_SIGN+":";
              int sign_start=str_resp.lastIndexOf(ppk_sign_mark);
              //System.out.println("sign_start="+sign_start);
              if(sign_start>0){
                data=str_resp.substring(0,sign_start).getBytes();
                 
                sign_start += ppk_sign_mark.length();
                int sign_end=str_resp.indexOf("-->",sign_start);
                if(sign_end>sign_start){
                  obj_ap_resp.put(Config.JSON_KEY_PPK_SIGN, new JSONObject(str_resp.substring(sign_start,sign_end)) );
                }
              }
            }
            
            obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK,data);
            obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_LENGTH,data.length);
            obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_URL,ap_fetch_url);
            
          }
      }
    }catch(Exception e){
      logger.error("PPkURI.fetchInterest("+ap_fetch_url+") error: "+e.toString());
    }
    
    //System.out.println("PPkURI.fetchInterest() obj_ap_resp:"+obj_ap_resp.toString());
    return obj_ap_resp;
  }
}