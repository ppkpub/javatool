import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.security.Security;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.text.ParsePosition;
import java.util.TimeZone;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.OutputStream; 
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONArray; 
import org.json.JSONObject;

import org.ipfs.api.IPFS;
import org.ipfs.api.NamedStreamable;
import org.ipfs.api.Multihash;
import org.ipfs.api.Base58;

public class PPkURI {

  
  static Logger logger = LoggerFactory.getLogger(PPkURI.class);
    
  public static JSONObject  fetchPPkURI(String uri){
    JSONObject  obj_newest_ap_resp=null;
    try{
      String[] uri_chunks=uri.split("/");
      if(!uri_chunks[0].toLowerCase().startsWith(Config.PPK_URI_PREFIX)){
        logger.error("PPkURI.fetchPPkURI() meet invalid ppk-uri:"+uri);
        return null;
      }

      String root_odin=uri_chunks[0].substring(Config.PPK_URI_PREFIX.length(),uri_chunks[0].length());

      OdinInfo odinInfo=Odin.getOdinInfo(root_odin);
      if(odinInfo==null){
        logger.error("PPkURI.fetchPPkURI("+uri+") meet invalid root odin:"+root_odin);
        return null;
      }
      
      uri=Config.PPK_URI_PREFIX+odinInfo.fullOdin+"/";
      for(int tt=1;tt<uri_chunks.length;tt++){
        uri += "/"+uri_chunks[tt];
      }      

      JSONObject odin_set = odinInfo.odinSet; 

      if( !odin_set.isNull("ap_set")  ){
        JSONObject  ap_set = odin_set.getJSONObject("ap_set");
        
        JSONObject  obj_ap_resp=null;
        for(Iterator it = ap_set.keys(); it!=null && it.hasNext(); ) { 
          String ap_id=(String)it.next();
          JSONObject ap_record=ap_set.getJSONObject(ap_id);
          
          obj_ap_resp=fetchAndValidationAP(uri,root_odin,ap_record,odin_set.optJSONObject("vd_set"));

          if(obj_ap_resp!=null){
            if( obj_ap_resp.optBoolean(Config.JSON_KEY_PPK_VALIDATION,false) ){
               String ap_resp_ppk_uri = obj_ap_resp.getString(Config.JSON_KEY_PPK_URI);
               if(obj_newest_ap_resp==null)
                 obj_newest_ap_resp=obj_ap_resp;
               else if(comparePPkResourceVer(
                          getPPkResourceVer(ap_resp_ppk_uri),
                          getPPkResourceVer(obj_newest_ap_resp.getString(Config.JSON_KEY_PPK_URI))
                      )>0 ){
                 //比较数据块版本
                 obj_newest_ap_resp=obj_ap_resp;
                 System.out.println("Found newer chunk:"+getPPkResourceVer(ap_resp_ppk_uri));
               }else{
                 System.out.println("Cancel older chunk:"+getPPkResourceVer(ap_resp_ppk_uri));
               }
            }
          }
        }
      }
      
      if(obj_newest_ap_resp!=null && obj_newest_ap_resp.optBoolean(Config.JSON_KEY_PPK_VALIDATION))
        System.out.println("Found newst valid chunk:"+obj_newest_ap_resp.optString(Config.JSON_KEY_PPK_URI));
      else
        System.out.println("Received invalid chunk");
    
    }catch(Exception e){
      logger.error("PPkURI.fetchPPkURI("+uri+") error:"+e.toString());
    }

    return obj_newest_ap_resp;
  }
  
  public static JSONObject  fetchAndValidationAP(String uri, String root_odin, JSONObject ap_record,JSONObject  vd_set){
    JSONObject obj_ap_resp=null;
    String ap_url=ap_record.optString("url","");
          
    if( ap_url.toLowerCase().startsWith("http")){
      String ap_check_url=ap_url+"?ppk-uri="+java.net.URLEncoder.encode(uri);
      System.out.println("Checking AP:"+ap_check_url);
      
      obj_ap_resp = fetchApByHTTP(ap_check_url);
    }else{
      logger.error("PPkURI.fetchPPkURI("+uri+") meet not supported ap url:"+ap_url);
    }
    
     //检查签名
    if(obj_ap_resp!=null && vd_set!=null){
      try{
        String vd_set_algo=vd_set.optString(Config.JSON_KEY_PPK_ALGO,"");
        String vd_set_pubkey=vd_set.optString(Config.JSON_KEY_PPK_PUBKEY,"");

        JSONObject obj_ppk_sign=obj_ap_resp.getJSONObject(Config.JSON_KEY_PPK_SIGN);

        String ap_resp_ppk_uri = obj_ppk_sign.optString(Config.JSON_KEY_PPK_URI,"");
        String ap_resp_sign_base64=obj_ppk_sign.optString(Config.JSON_KEY_PPK_SIGN_BASE64,"");
        System.out.println("ap_resp_sign_base64="+ap_resp_sign_base64);
        
        obj_ap_resp.put(Config.JSON_KEY_PPK_URI,ap_resp_ppk_uri);
        
        byte[] content_data=(byte[])obj_ap_resp.opt(Config.JSON_KEY_PPK_CHUNK);
        byte[] uri_data=ap_resp_ppk_uri.getBytes();

        if(content_data!=null){
          ByteBuffer byteBuffer = ByteBuffer.allocate(content_data.length+uri_data.length);
          byteBuffer.put(content_data,0,content_data.length);
          byteBuffer.put(uri_data,0,uri_data.length);
          
          //System.out.println("byteBuffer.array()="+Util.bytesToHexString(byteBuffer.array()));
          //System.out.println("vd_set_pubkey="+vd_set_pubkey + ", vd_set_algo="+vd_set_algo);
          
          if(vd_set_algo.length()>0 && vd_set_pubkey.length()==0){
            //动态获取和更新公钥
            String vd_set_cert_uri=vd_set.optString(Config.JSON_KEY_PPK_CERT_URI,"");
            if(vd_set_cert_uri.length()>0){
              try{
                logger.info("Auto fetch vd_set_cert_uri:"+vd_set_cert_uri);
                
                String tmp_str=Util.fetchURI(vd_set_cert_uri);
                
                vd_set_pubkey=RSACoder.parseValidPubKey(vd_set_algo,tmp_str);
                vd_set.put(Config.JSON_KEY_PPK_PUBKEY, vd_set_pubkey);
                
                OdinInfo odinInfo=Odin.getOdinInfo(root_odin);
                JSONObject  new_odin_set=odinInfo.odinSet;
                new_odin_set.put("vd_set",vd_set);
                
                Database db = Database.getInstance();
                PreparedStatement ps;
                ps = db.connection.prepareStatement("UPDATE odins SET odin_set=?,validity='valid' WHERE full_odin=?;");

                ps.setString(1, new_odin_set.toString());
                ps.setString(2, odinInfo.fullOdin);
                ps.execute();
                
              }catch(Exception e){
                logger.error("Meet invalid vd_set_cert_uri:"+vd_set_cert_uri);
              }
            }
          }

          if(RSACoder.verify(byteBuffer.array(), vd_set_pubkey,ap_resp_sign_base64,vd_set_algo )){
             obj_ap_resp.put(Config.JSON_KEY_PPK_VALIDATION,true);
             System.out.println("Found valid chunk");
          }
        }
      }catch(Exception e){
        logger.error("PPkURI.fetchPPkURI("+uri+") meet invalid ppk sign:"+obj_ap_resp.optString(Config.JSON_KEY_PPK_SIGN));
      }
    }
    
    return obj_ap_resp;
  }
  
  public static JSONObject  fetchApByHTTP(String ap_url){
    JSONObject  obj_ap_resp=new JSONObject();
    logger.info("PPkURI.fetchApByHTTP("+ap_url+") ...");
    
    try{
      URL url = new URL(ap_url);
      HttpURLConnection.setFollowRedirects(true);  
      HttpURLConnection hc = (HttpURLConnection) url.openConnection();  
      hc.setRequestMethod("GET");  
      hc.addRequestProperty("User-Agent", Config.appName+" "+Config.version); 
      //hc.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.154 Safari/537.36");  
      //hc.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");  
      //hc.setRequestProperty("Content-Language", "zh-cn");  
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
          byte[] data = readInputStream(inStream);
          
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
            obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_URL,ap_url);
            
          }
      }
    }catch(Exception e){
      logger.error("PPkURI.fetchApByHTTP("+ap_url+") error: "+e.toString());
    }
    
    //System.out.println("PPkURI.fetchApByHTTP() obj_ap_resp:"+obj_ap_resp.toString());
    return obj_ap_resp;
  }
  
  public static byte[] readInputStream(InputStream inStream) throws Exception{
    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    //创建一个Buffer字符串
    byte[] buffer = new byte[1024];
    //每次读取的字符串长度，如果为-1，代表全部读取完毕
    int len = 0;
    //使用一个输入流从buffer里把数据读取出来
    while( (len=inStream.read(buffer)) != -1 ){
        //用输出流往buffer里写入数据，中间参数代表从哪个位置开始读，len代表读取的长度
        outStream.write(buffer, 0, len);
    }
    //关闭输入流
    inStream.close();
    //把outStream里的数据写入内存
    return outStream.toByteArray();
  }
  
  //获得PPk URI对应资源版本号，即结尾类似“#1.0”这样的描述，如果没有则缺省认为是“#1.0”
  public static String getPPkResourceVer(String ppk_uri){
    String rv="1.0";
    int resource_start=ppk_uri.lastIndexOf(Config.PPK_URI_RESOURCE_MARK);

    if(resource_start>0){
      rv=ppk_uri.substring(resource_start+1);
      String[] pieces = rv.split("\\.");
      
      int major=0;
      int minor=0;
      if(pieces.length>=1){
        major=Integer.parseInt(pieces[0].trim());
      }
      if(major<=0)  major=1; 
      
      if(pieces.length>=2){
        minor=Integer.parseInt(pieces[1].trim());
      }
      if(minor<=0)  minor=0;
      
      rv=""+major+"."+minor;
    }
    
    return rv;
  }
  
  //比较两个URI资源版本
  // rv1=rv2 return 0
  // rv1<rv2 return -1
  // rv1>rv2 return 1
  public static int comparePPkResourceVer(String rv1,String rv2){
    String[] pieces1 = rv1.split("\\.");
    String[] pieces2 = rv2.split("\\.");
    
    int major1=Integer.parseInt(pieces1[0].trim());
    int minor1=Integer.parseInt(pieces1[1].trim());
    int major2=Integer.parseInt(pieces2[0].trim());
    int minor2=Integer.parseInt(pieces2[1].trim());
    
    if(major1==major2 && minor1==minor2)
      return 0;
    else if(major1<major2 || ( major1==major2 && minor1<minor2 ) )
      return -1;
    else
      return 1;
  }
}

