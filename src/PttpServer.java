import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.setPort;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Calendar;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;
import spark.template.freemarker.FreeMarkerRoute;

import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import com.google.common.collect.Lists;

import freemarker.template.Configuration;

public class PttpServer implements Runnable {
  public Logger logger = LoggerFactory.getLogger(PttpServer.class);

  public void run() { 
    init(); 
  } 
   
  public void init() {
    boolean inJar = false;
    try {
      CodeSource cs = this.getClass().getProtectionDomain().getCodeSource();
      inJar = cs.getLocation().toURI().getPath().endsWith(".jar");
    }
    catch (URISyntaxException e) {
      e.printStackTrace();
    }
    
    setPort(Config.ppkPttpServicePort);    
    
    final Configuration configuration = new Configuration();
    try {
      if (inJar) {
        Spark.externalStaticFileLocation("resources/static/");
        configuration.setClassForTemplateLoading(this.getClass(), "resources/templates/");
      } else {
        Spark.externalStaticFileLocation("./resources/static/");
        configuration.setDirectoryForTemplateLoading(new File("./resources/templates/"));  
      }
    } catch (Exception e) {
    }

    get(new FreeMarkerRoute("/") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        
        String pttp_interest=request.queryParams(Config.PTTP_INTEREST);
        
        if( pttp_interest != null  ){
          String ppk_uri;
          try{
            //Request like http://127.0.0.1:8088/?pttp_interest={"ver":1,"hop_limit":6,"interest":{"uri":"ppk:0/"}}
            //URLEncode as http://127.0.0.1:8088/?pttp_interest=%7b%22ver%22%3a1%2c%22hop_limit%22%3a6%2c%22interest%22%3a%7b%22uri%22%3a%22ppk%3a0%23%22%7d%7d
            JSONObject objInterest=new JSONObject(pttp_interest);
            ppk_uri=objInterest.getJSONObject("interest").getString("uri");
          }catch(Exception e){
            logger.error("PttpServer.get() meet misformated pttp_interest:"+pttp_interest);
            if(pttp_interest.startsWith("ppk:"))
              ppk_uri=pttp_interest; 
            else{
              Map<String, Object> attributes = new HashMap<String, Object>();
              attributes.put("pttp_data", "ERROR:400");
              return modelAndView(attributes, "pttp-data.html");
            }
          }
          
          JSONObject obj_ap_resp=PPkURI.fetchPPkURI( ppk_uri );
          
          String original_resp="ERROR: Invalid pttp data!";
          if(obj_ap_resp!=null)
            original_resp=obj_ap_resp.optString(Config.JSON_KEY_ORIGINAL_RESP,original_resp);
          
          Map<String, Object> attributes = new HashMap<String, Object>();
          attributes.put("pttp_data", original_resp);
          return modelAndView(attributes, "pttp-data.html");
        }else{
          String ppk_uri=request.queryParams(Config.JSON_KEY_PPK_URI);
          if( ppk_uri==null || ppk_uri.length( ) == 0 )
            ppk_uri = Config.ppkDefaultHomepage;
   
          Map<String, Object> attributes = handlePttpBrowserRequest(request,ppk_uri);
          return modelAndView(attributes, "pttp-browser.html");
        }
      }
    });

      
    get(new FreeMarkerRoute("/error") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("title", "Error");
        return modelAndView(attributes, "error.html");
      }
    });
  }
 
  public Map<String, Object> updateCommonStatus(Request request, Map<String, Object> attributes) {
    attributes.put("version", Config.version);
    attributes.put("version_major", Config.majorVersion);
    attributes.put("version_minor", Config.minorVersion);
    attributes.put("system_charset", java.nio.charset.Charset.defaultCharset().toString());
    
    String str_ipfs_status = Util.isIpfsRuning() ? "IPFS:OK":"IPFS:<font color='#F00'>Not running</font>";
    
    attributes.put("ipfs_status", str_ipfs_status);
    
    attributes.put("LANG_PPKPUB", Language.getLangLabel("PPkPub"));
    attributes.put("LANG_PPK_BROWSER", Language.getLangLabel("PPkBrowser"));
    attributes.put("LANG_ODIN", Language.getLangLabel("ODIN"));
    attributes.put("LANG_BROWSER", Language.getLangLabel("Browser"));
    attributes.put("LANG_WALLET", Language.getLangLabel("Wallet"));
    attributes.put("LANG_TECHNICAL", Language.getLangLabel("Technical"));
    attributes.put("LANG_COMMUNITY", Language.getLangLabel("Community"));

    attributes.put("LANG_BLOCKS", Language.getLangLabel("blocks"));
    attributes.put("LANG_VERSION", Language.getLangLabel("Version"));

    attributes.put("LANG_VIEWING_OTHER_ADDRESS", Language.getLangLabel("Viewing other address"));
    attributes.put("LANG_VIEWING_OTHER_ADDRESS_NOTICE", Language.getLangLabel("Notice: Click your address listed on the right side to go back your wallet."));
    attributes.put("LANG_REPARSE_TRANSACTIONS", Language.getLangLabel("Reparse transactions"));
    attributes.put("LANG_VERSION_OUT_OF_DATE", Language.getLangLabel("You must update to the latest version. Your version is out of date."));
    attributes.put("LANG_PARSING_TRANSACTIONS", Language.getLangLabel("The software is parsing transactions. You can still use the software, but the information you see will be out of date."));

    attributes.put("LANG_ERROR", Language.getLangLabel("Error"));
        
    return attributes;
  }

  public Map<String, Object> handlePttpBrowserRequest(Request request,String ppk_uri) {
    Map<String, Object> attributes = new HashMap<String, Object>();
    request.session(true);
    
    attributes = updateCommonStatus(request, attributes);

    if(ppk_uri==null){
        attributes.put("error", "no ppk-uri.");
        return attributes;
    } 
    attributes.put("title", ppk_uri);
    attributes.put("ppk_uri", ppk_uri);
    
    try{
      JSONObject obj_ap_resp=PPkURI.fetchPPkURI( ppk_uri );
      
      String ap_resp_content="";
      String ap_resp_ppk_uri="";
      String ap_resp_url="";
      String ap_resp_sign="";
      String ap_resp_validate_result="";
      
      if(obj_ap_resp!=null){
        ap_resp_url=obj_ap_resp.optString(Config.JSON_KEY_PPK_CHUNK_URL,"");
        String str_chunk_type = obj_ap_resp.optString(Config.JSON_KEY_PPK_CHUNK_TYPE,"").toLowerCase();
        if( str_chunk_type.startsWith("text/html") ){ //网页
          ap_resp_content = new String( (byte[])obj_ap_resp.opt(Config.JSON_KEY_PPK_CHUNK) );
          
          //处理页面内容中的图片
          ap_resp_content = processPPkImagesInPage(ap_resp_content); 
          
          //将页面内容中以ppk:起始的href链接替换为适合本地浏览的链接格式
          String tmp_href_ap_url=Config.ppkDefaultHrefApUrl+"?"+Config.JSON_KEY_PPK_URI+"="+Config.PPK_URI_PREFIX;

          ap_resp_content = ap_resp_content.replaceAll("'"+Config.PPK_URI_PREFIX,"'"+tmp_href_ap_url)
                                           .replaceAll("\""+Config.PPK_URI_PREFIX,"\""+tmp_href_ap_url);
          
          
        }else if(str_chunk_type.startsWith("text")){ //其他文本
          ap_resp_content = new String( (byte[])obj_ap_resp.opt(Config.JSON_KEY_PPK_CHUNK) );
        }else if(str_chunk_type.startsWith("image")){
          ap_resp_content = "<img src='"+Util.imageToBase64DataURL(str_chunk_type,(byte[])obj_ap_resp.opt(Config.JSON_KEY_PPK_CHUNK))+"'>";
        }else{
          //Not supported chunk_type now
          ap_resp_content =  obj_ap_resp.optString(Config.JSON_KEY_PPK_CHUNK_TYPE,"");
        }
        
        ap_resp_ppk_uri = obj_ap_resp.optString(Config.JSON_KEY_PPK_URI);
        ap_resp_sign = obj_ap_resp.optString(Config.JSON_KEY_PPK_SIGN,"");
        
        int validcode=obj_ap_resp.optInt(Config.JSON_KEY_PPK_VALIDATION,Config.PPK_VALIDATION_ERROR);
        if( validcode == Config.PPK_VALIDATION_IGNORED )
           ap_resp_validate_result="<font color='#F72'>Valiade ignored! The content unable to be identified. </font>";
        else if( validcode == Config.PPK_VALIDATION_OK )
           ap_resp_validate_result="<font color='#0F0'>Valiade OK</font>";
        else
           ap_resp_validate_result="<font color='#F00'>Valiade failed!</font>";
      }else{
        ap_resp_validate_result="<font color='#F00'>No valid data</font>";
      }
      
      attributes.put("ap_resp_content", ap_resp_content);   
      attributes.put("ap_resp_ppk_uri", ap_resp_ppk_uri);   
      attributes.put("ap_resp_url", ap_resp_url);   
      attributes.put("ap_resp_sign", ap_resp_sign);   
      //attributes.put("vd_set_pubkey", vd_set_pubkey);   
      attributes.put("ap_resp_validate_result", ap_resp_validate_result);     
    }catch (Exception e) {
      logger.error(e.toString());
    }

    attributes.put("LANG_BROWSE_PPK_NETWORK", Language.getLangLabel("Browse PPk network"));
    attributes.put("LANG_RESPONSE_URI", Language.getLangLabel("Response URI"));
    attributes.put("LANG_RESPONSE_URL", Language.getLangLabel("Response URL"));
    attributes.put("LANG_VALIDATE_RESULT", Language.getLangLabel("Validate result"));
    attributes.put("LANG_CLICKED_WAITING", Language.getLangLabel("Waiting"));  
    
    return attributes;
  }
  
  //处理页面中包含的"ppk:"起始的图片
  protected String processPPkImagesInPage(String str_content){
    List<String> srcList = new ArrayList<String>(); //用来存储获取到的图片地址  
    Pattern p = Pattern.compile("<(img|IMG)(.*?)(>|></img>|/>)");//匹配字符串中的img标签  
    Matcher matcher = p.matcher(str_content);  
    boolean hasPic = matcher.find();  
    if(hasPic == true)//判断是否含有图片  
    {  
      while(hasPic) //如果含有图片，那么持续进行查找，直到匹配不到  
      {  
        String group = matcher.group(2);//获取第二个分组的内容，也就是 (.*?)匹配到的  
        Pattern srcText = Pattern.compile("(src|SRC)=(\"|\')(.*?)(\"|\')");//匹配图片的地址  
        Matcher matcher2 = srcText.matcher(group);  
        if( matcher2.find() )   
        {  
            String tmp_img_src=matcher2.group(3);
            if(tmp_img_src.startsWith(Config.PPK_URI_PREFIX)){
              if(!srcList.contains(tmp_img_src)){
                srcList.add( tmp_img_src  );//把获取到的以"ppk:"起始的图片地址添加到列表中  
              }
            }
        }  
        hasPic = matcher.find();//判断是否还有img标签  
      }  
    }  

    for(int kk=0; kk<srcList.size(); kk++){
      String old_img_src = (String) srcList.get(kk);
      JSONObject obj_ap_resp = PPkURI.fetchPPkURI(old_img_src);
      if(obj_ap_resp!=null){
        String str_chunk_type = obj_ap_resp.optString(Config.JSON_KEY_PPK_CHUNK_TYPE,"").toLowerCase();
        
        String new_img_src = Util.imageToBase64DataURL(str_chunk_type,(byte[])obj_ap_resp.opt(Config.JSON_KEY_PPK_CHUNK));
        
        str_content = str_content.replaceAll("src='"+old_img_src,"src='"+new_img_src)
                                 .replaceAll("src=\""+old_img_src,"src=\""+new_img_src);
      }
    }
    
    return str_content;
  }
}