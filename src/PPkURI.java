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
import java.util.Base64;
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
    logger.info("==========================\nPPkURI.fetchPPkURI: "+uri);
    
    JSONObject  obj_newest_ap_resp=null;
    try{
      if(!uri.toLowerCase().startsWith(Config.PPK_URI_PREFIX)  
         || uri.indexOf("//") >0 
         || uri.indexOf("##") >0 
         ){
        logger.error("PPkURI.fetchPPkURI() meet invalid ppk-uri:"+uri);
        return null;
      }
      
      int path_mark_posn=uri.indexOf('/');
      int resoure_mark_posn=uri.indexOf('#');
      if( path_mark_posn<0 &&  resoure_mark_posn<0) //输入地址类似 ppk:100 的情况（没有加/或#），这时默认尾部加 /# 成为如 ppk:100/# 
        uri+="/#";
      else if(resoure_mark_posn<0) //其他没有带#默认追加 # 成为如 ppk:100/image# 
        uri+="#";

      //解析URI资源区段
      String[] reource_chunks   = uri.substring(Config.PPK_URI_PREFIX.length(),uri.length()).split("#");
      String   resource_versoin = reource_chunks.length>1 ? reource_chunks[1]:"";

      String parent_odin_path="";
      String resource_id ="";  
      System.out.println("reource_chunks[0]="+reource_chunks[0]);
      if(reource_chunks[0].endsWith("/")){
        resource_id="";
        parent_odin_path=reource_chunks[0].substring(0,reource_chunks[0].length()-1);
      }else{
        int tmp_posn=reource_chunks[0].lastIndexOf('/');
        if(tmp_posn>0){
          resource_id=reource_chunks[0].substring(tmp_posn+1,reource_chunks[0].length());
          parent_odin_path=reource_chunks[0].substring(0,tmp_posn);
        }else{
          parent_odin_path="";
          resource_id=reource_chunks[0];
        }
      }
      System.out.println("uri="+uri+" , parent_odin_path="+parent_odin_path+", resource_id="+resource_id+"\n");
      
      //获取ODIN标识对应访问点和签名验证参数
      String formated_ppk_uri ="";
      JSONObject odin_set ;
      if(parent_odin_path.length()==0){ //resource is root ODIN 
        OdinInfo odinInfo=Odin.getOdinInfo(resource_id);
        if(odinInfo==null){
          logger.error("PPkURI.fetchPPkURI("+uri+") meet invalid root odin:"+resource_id);
          return null;
        }
        formated_ppk_uri=Config.PPK_URI_PREFIX+odinInfo.fullOdin+"#"+resource_versoin;
        odin_set = odinInfo.odinSet; 
        
        //本地直接返回该一级ODIN配置信息
        obj_newest_ap_resp=new JSONObject();
        String chunk_content=odin_set.toString();
        byte[] chunk_content_bytes=chunk_content.getBytes();
        
        JSONObject obj_chunk_metainfo=new JSONObject();
        obj_chunk_metainfo.put("chunk_index", 0 );
        obj_chunk_metainfo.put("chunk_count", 1 );
        obj_chunk_metainfo.put("content_type", "text/json"  );
        obj_chunk_metainfo.put("content_length", chunk_content.length()  );
        
        JSONObject obj_newest_ap_chunk=new JSONObject();
        obj_newest_ap_chunk.put("uri",formated_ppk_uri);
        obj_newest_ap_chunk.put("status_code",200);
        obj_newest_ap_chunk.put("status_info","OK");
        
        obj_newest_ap_chunk.put("metainfo",obj_chunk_metainfo);
        obj_newest_ap_chunk.put("content",chunk_content);
        
        JSONObject obj_newest_ap_data=new JSONObject();
        obj_newest_ap_data.put("ver",1);
        obj_newest_ap_data.put("data",obj_newest_ap_chunk.toString());
        obj_newest_ap_data.put("sign","");
        
        obj_newest_ap_resp.put(Config.JSON_KEY_ORIGINAL_RESP, obj_newest_ap_data.toString() );
        
        obj_newest_ap_resp.put(Config.JSON_KEY_PPK_CHUNK,chunk_content_bytes);
        obj_newest_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_LENGTH,chunk_content_bytes.length);
        obj_newest_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_TYPE,"text/json");
        obj_newest_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_URL,"");

        obj_newest_ap_resp.put(Config.JSON_KEY_PPK_VALIDATION,Config.PPK_VALIDATION_OK );
        obj_newest_ap_resp.put(Config.JSON_KEY_PPK_URI,formated_ppk_uri);

        return obj_newest_ap_resp;
      }else{//sub ODIN
        JSONObject tmp_resp = fetchPPkURI( Config.PPK_URI_PREFIX+parent_odin_path+"#" ); 
        
        if(tmp_resp==null || tmp_resp.optInt(Config.JSON_KEY_PPK_VALIDATION,Config.PPK_VALIDATION_ERROR) == Config.PPK_VALIDATION_ERROR ){
          logger.error("PPkURI.fetchPPkURI("+uri+") meet invalid odin:"+parent_odin_path);
          return null;
        }
        
        formated_ppk_uri=tmp_resp.getString(Config.JSON_KEY_PPK_URI);;
        if(formated_ppk_uri.indexOf('#')>0)
          formated_ppk_uri=formated_ppk_uri.substring(0,formated_ppk_uri.lastIndexOf('#') );
        
        if(!formated_ppk_uri.endsWith("/"))
          formated_ppk_uri += "/";
      
        formated_ppk_uri += resource_id+"#"+resource_versoin;
        
        odin_set = new  JSONObject( new String( (byte[])tmp_resp.opt(Config.JSON_KEY_PPK_CHUNK) ));
        
        //检查ap_set和vd_set参数是否存在，如不存在则相应自动继承使用父级标识的ap_set和vd_set,20181017
        if( (odin_set.isNull("ap_set") || odin_set.isNull("vd_set")) 
            &&  !tmp_resp.isNull("parent_odin_set") ){
          
          
          JSONObject parent_odin_set = tmp_resp.getJSONObject("parent_odin_set");
          
          if(odin_set.isNull("ap_set")){
            System.out.println("Meet null ap_set and try use parent ap_set");
            odin_set.put("ap_set",parent_odin_set.optJSONObject("ap_set"));
          }
          
          if(odin_set.isNull("vd_set")){
            System.out.println("Meet null vd_set and try use parent vd_set");
            odin_set.put("vd_set",parent_odin_set.optJSONObject("vd_set"));
          }
        }
      }
      System.out.println(">>>>> formated_ppk_uri="+formated_ppk_uri+"  odin_set:"+odin_set.toString());

      if( !odin_set.isNull("ap_set")  ){
        JSONObject  ap_set = odin_set.getJSONObject("ap_set");
        
        JSONObject  obj_ap_resp=null;
        for(Iterator it = ap_set.keys(); it!=null && it.hasNext(); ) { 
          String ap_id=(String)it.next();
          JSONObject ap_record=ap_set.getJSONObject(ap_id);
          
          obj_ap_resp=fetchAndValidationAP(formated_ppk_uri,null,ap_record,odin_set.optJSONObject("vd_set"));

          if(obj_ap_resp!=null){
            if( obj_ap_resp.optInt(Config.JSON_KEY_PPK_VALIDATION,Config.PPK_VALIDATION_ERROR) != Config.PPK_VALIDATION_ERROR ){
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
      }else{
        System.out.println("Invalid ap_set!");
      }
      if(obj_newest_ap_resp!=null && obj_newest_ap_resp.optInt(Config.JSON_KEY_PPK_VALIDATION) != Config.PPK_VALIDATION_ERROR){
        obj_newest_ap_resp.put("parent_odin_set",odin_set);
        
        String ap_resp_ppk_uri = obj_newest_ap_resp.getString(Config.JSON_KEY_PPK_URI);
        System.out.println("Found newst valid chunk:"+ap_resp_ppk_uri);
        
        String cache_filename = Config.cachePath + ap_resp_ppk_uri.substring(Config.PPK_URI_PREFIX.length(),ap_resp_ppk_uri.length());
        Util.exportTextToFile(obj_newest_ap_resp.toString(),cache_filename);
      }else
        System.out.println("Received invalid chunk");
    
    }catch(Exception e){
      logger.error("PPkURI.fetchPPkURI("+uri+") error:"+e.toString());
    }

    return obj_newest_ap_resp;
  }
  
  public static JSONObject  fetchAndValidationAP(String uri, String root_odin, JSONObject ap_record,JSONObject  vd_set){
    JSONObject obj_ap_resp=null;
    String ap_url=ap_record.optString("url","");
    
    JSONObject objInterest=new JSONObject();
    try{
      JSONObject objUri=new JSONObject();
      objUri.put("uri",uri);
      
      objInterest.put("ver",1);
      objInterest.put("hop_limit",6);
      objInterest.put("interest",objUri);
    }catch(Exception e){
      logger.error("PPkURI.fetchAndValidationAP("+uri+") meet json exception"+e.toString());
    }
    
    String str_interest=objInterest.toString( );
    
    String str_ap_resp_json=null;
    if( ap_url.toLowerCase().startsWith("http")){
      str_ap_resp_json = APoverHTTP.fetchInterest(ap_url,str_interest);
    }else if( ap_url.toLowerCase().startsWith("ethap")){
      str_ap_resp_json = APoverETH.fetchInterest(ap_url,str_interest);
    }else {
      str_ap_resp_json = Util.fetchURI(ap_url);
    }
    
    obj_ap_resp=parseRespOfPTTP(ap_url,str_ap_resp_json,vd_set);

    return obj_ap_resp;
  }
  
  //解析AP按PTTP协议所应答的数据包(JSON格式字符串)
  protected static JSONObject parseRespOfPTTP(String ap_url,String str_ap_data_json,JSONObject  vd_set){
    JSONObject obj_ap_resp = null;
    String   str_original_data_json = null;
    
    //从应答JSON字符串解析出所需数据正文、签名等字段
    try{
      JSONObject obj_ap_data = new JSONObject(str_ap_data_json);
      JSONObject obj_data = null;
      str_original_data_json = obj_ap_data.optString("data",null);
      System.out.println("PPkURI:parseRespOfPTTP() str_original_data_json="+str_original_data_json);
      if(str_original_data_json!=null){
        obj_data = new JSONObject(str_original_data_json);
      }else{ //兼容data取值不是字符串而是JSONObject的非标准形式
        obj_data = obj_ap_data.getJSONObject("data");
        str_original_data_json = obj_data.toString();
      }
      JSONObject obj_chunk_metainfo=obj_data.optJSONObject("metainfo");
      
      String chunk_content_encoding = (obj_chunk_metainfo==null) ? "":obj_chunk_metainfo.optString("content_encoding","").toLowerCase();
      String chunk_content_type = (obj_chunk_metainfo==null) ? "text/html":obj_chunk_metainfo.optString("content_type","");
      
      int pttpStatusCode=obj_data.getInt("status_code");
      System.out.println("pttpStatusCode="+pttpStatusCode);
      obj_ap_resp = new JSONObject();
      obj_ap_resp.put(Config.JSON_KEY_ORIGINAL_RESP, str_ap_data_json );
      if (pttpStatusCode == HttpURLConnection.HTTP_OK) {
        byte[]  chunk_content = null;
        if("base64".equals(chunk_content_encoding)){
          chunk_content=Base64.getDecoder().decode(obj_data.getString("content"));
        }else if("hex".equals(chunk_content_encoding)){
          chunk_content=Util.hexStringToBytes(obj_data.getString("content"));
        }else{
          chunk_content=obj_data.getString("content").getBytes();
        }
        
        obj_ap_resp.put(Config.JSON_KEY_PPK_URI,obj_data.getString("uri"));
        obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_TYPE,chunk_content_type);
        obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK,chunk_content);
        obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_LENGTH,chunk_content.length);
        obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_URL,ap_url);
      }else{
        String str_status_detail=obj_data.optString("status_detail","");
        String str_content=obj_data.optString("content","");
        if(str_content.length()==0)
          str_content="PTTP status_code : "+pttpStatusCode + " " + str_status_detail ;
        byte[]  chunk_content = str_content.getBytes();
        obj_ap_resp.put(Config.JSON_KEY_PPK_URI,obj_data.getString("uri"));
        obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_TYPE,chunk_content_type);
        obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK,chunk_content);
        obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_LENGTH,chunk_content.length);
        obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_URL,ap_url);
      }
      
      obj_ap_resp.put(Config.JSON_KEY_PPK_SIGN, obj_ap_data.optString("sign","") );
    }catch(Exception e){
      logger.error("PPkURI.parseRespOfPTTP("+ap_url+") error: "+e.toString());
    }
    
    //验证数据包签名
    if(obj_ap_resp!=null && vd_set==null){
      //在未设置签名参数时，忽略检查签名
      try{
        obj_ap_resp.put(Config.JSON_KEY_PPK_VALIDATION,Config.PPK_VALIDATION_IGNORED);
      }catch (Exception e) {
        e.printStackTrace();
      }
    }else if(obj_ap_resp!=null && vd_set!=null){
      //检查签名
      String str_ppk_sign=obj_ap_resp.optString(Config.JSON_KEY_PPK_SIGN,"");
      
      try{
        String vd_set_algo=vd_set.optString(Config.JSON_KEY_PPK_ALGO,"");
        String vd_set_pubkey=vd_set.optString(Config.JSON_KEY_PPK_PUBKEY,"");

        byte[] original_data = str_original_data_json.getBytes();
        if(original_data!=null){
          ByteBuffer byteBuffer = ByteBuffer.allocate(original_data.length);
          byteBuffer.put(original_data,0,original_data.length);

          if(vd_set_algo.length()>0 && vd_set_pubkey.length()==0){
            //动态获取和更新公钥
            String vd_set_cert_uri=vd_set.optString(Config.JSON_KEY_PPK_CERT_URI,"");
            if(vd_set_cert_uri.length()>0){
              try{
                logger.info("Auto fetch vd_set_cert_uri:"+vd_set_cert_uri);
                
                String tmp_str=Util.fetchURI(vd_set_cert_uri);
                
                vd_set_pubkey=RSACoder.parseValidPubKey(vd_set_algo,tmp_str);
                vd_set.put(Config.JSON_KEY_PPK_PUBKEY, vd_set_pubkey);
                
                //待完善，将动态获得的公钥自动存入数据库
                /*
                OdinInfo odinInfo=Odin.getOdinInfo(root_odin);
                JSONObject  new_odin_set=odinInfo.odinSet;
                new_odin_set.put("vd_set",vd_set);
                
                Database db = Database.getInstance();
                PreparedStatement ps;
                ps = db.connection.prepareStatement("UPDATE odins SET odin_set=?,validity='valid' WHERE full_odin=?;");

                ps.setString(1, new_odin_set.toString());
                ps.setString(2, odinInfo.fullOdin);
                ps.execute(); 
                */
              }catch(Exception e){
                logger.error("Meet invalid vd_set_cert_uri:"+vd_set_cert_uri);
              }
            }
          }
          
          if( vd_set_pubkey.length()==0 ){
            obj_ap_resp.put(Config.JSON_KEY_PPK_VALIDATION,Config.PPK_VALIDATION_IGNORED);
            System.out.println("No valid pubkey.Ignored to verify the chunk.");
          }else{
            String[] sign_pieces = str_ppk_sign.split("\\:");
            String ap_resp_sign_algo=sign_pieces[0].trim();
            String ap_resp_sign_base64=sign_pieces[1].trim();
            //String ap_resp_sign_pubkey=sign_pieces[2].trim(); //just for test
            System.out.println("ap_resp_sign_algo="+ap_resp_sign_algo+",ap_resp_sign_base64="+ap_resp_sign_base64);
            
            if(RSACoder.verify(byteBuffer.array(), vd_set_pubkey,ap_resp_sign_base64,vd_set_algo )){
               obj_ap_resp.put(Config.JSON_KEY_PPK_VALIDATION,Config.PPK_VALIDATION_OK);
               System.out.println("Found valid chunk");
               //System.out.println("byteBuffer.array()="+Util.bytesToHexString(byteBuffer.array()));
               //System.out.println("vd_set_pubkey="+vd_set_pubkey + ", vd_set_algo="+vd_set_algo);
               //System.out.println("resp_pubkey="+obj_ppk_sign.optString("debug_pubkey","") + "\nresp_algo="+obj_ppk_sign.optString("algo","")+"\nresp_sign_base64="+ap_resp_sign_base64);
            }else{
               System.out.println("Found invalid chunk.Please check the vd setting.");
               System.out.println("byteBuffer.array()="+Util.bytesToHexString(byteBuffer.array()));
               System.out.println("vd_set_pubkey="+vd_set_pubkey + ", vd_set_algo="+vd_set_algo);
               System.out.println("str_ppk_sign="+str_ppk_sign);
            }
          }
        }
      }catch(Exception e){
        logger.error("PPkURI.parseRespOfPTTP("+ap_url+") meet invalid ppk sign:"+str_ppk_sign);
      }
    }
    
    return obj_ap_resp;
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

