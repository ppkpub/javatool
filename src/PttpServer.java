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
    Config.ppkDefaultHrefApUrl = "http://"+Config.ppkPttpServiceIP+":"+Config.ppkPttpServicePort+"/";
    
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
        
        String pttp_interest=request.queryParams("pttp");
        String go_uri=request.queryParams("go");
        
        if( pttp_interest != null ){
          //For get PTTP data
          Map<String, Object> attributes = handlePttpProxyRequest(request,pttp_interest);
          return modelAndView(attributes, "pttp-data.html");
        }else{
          //For browse content
          if( go_uri==null || go_uri.trim().length( ) == 0 )
            go_uri = Config.ppkDefaultHomepage;
   
          Map<String, Object> attributes = handlePttpBrowserRequest(request,go_uri);
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
    
    /*
    get(new FreeMarkerRoute("*") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        logger.error("PttpServer.get(*) meet misformated request:"+request.toString());
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("title", "Error");
        return modelAndView(attributes, "error.html");
      }
    });
    */
  }
 
  public static Map<String, Object> updateCommonStatus(Request request, Map<String, Object> attributes) {
    attributes.put("version", Config.version);
    attributes.put("version_major", Config.majorVersion);
    attributes.put("version_minor", Config.minorVersion);
    attributes.put("system_charset", java.nio.charset.Charset.defaultCharset().toString());
    
    attributes.put("LastParsedBlock", Util.getLastParsedBlock());
    
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
  
  public static Map<String, Object> handlePttpProxyRequest(Request request,String pttp_interest) {
    Map<String, Object> attributes = new HashMap<String, Object>();
    
    if(pttp_interest==null){
        attributes.put("error", "ERROR:no pttp_interest.");
        return attributes;
    } 
    
    pttp_interest = pttp_interest.trim();

    String ppk_uri=null;
    try{
        //Request like http://127.0.0.1:8088/?pttp={"ver":1,"hop_limit":6,"uri":"ppk:0/","option":{}}
        //or  like http://127.0.0.1:8088/?pttp=ppk:0/
        if(pttp_interest.startsWith("ppk:")){
          ppk_uri=pttp_interest; 
        }else{
          JSONObject objInterest=new JSONObject(pttp_interest);
          ppk_uri=objInterest.getString("uri");
        }

    }catch(Exception e){
        System.out.println("PttpServer.get() meet misformated pttp_interest:"+pttp_interest);
        attributes.put("pttp_data", "ERROR:400");
        return attributes;
    }
     
    JSONObject obj_decoded_chunk=PTTP.fetchPPkURI( ppk_uri );
      
    String original_resp="ERROR: Invalid pttp data!";
    if(obj_decoded_chunk!=null)
        original_resp=obj_decoded_chunk.optString(Config.JSON_KEY_ORIGINAL_RESP,original_resp);
      
    attributes.put("pttp_data", original_resp);
    
    return attributes;
  }

  public static Map<String, Object> handlePttpBrowserRequest(Request request,String go_uri) {
    Map<String, Object> attributes = new HashMap<String, Object>();
    request.session(true);
    
    attributes = updateCommonStatus(request, attributes);

    if(go_uri==null){
        attributes.put("error", "no valid uri.");
        return attributes;
    } 
    
    //检查输入网址的格式
    go_uri = go_uri.trim();
    String go_uri_as_id = ODIN.formatPPkURI(go_uri,true);
	if( go_uri_as_id!=null ){
		try{
          String ap_resp_content="";
          String ap_resp_ppk_uri="";
          String ap_resp_url="";
          String ap_resp_sign="";
          String ap_resp_validate_result="";

          String go_uri_as_page = ODIN.formatPPkURI(go_uri,false);
          if(!go_uri_as_id.equals(go_uri_as_page) ){
              //对于没有填写完整的地址，提示可选择访问的内容
              String tmp_href_ap_url_id=Config.ppkDefaultHrefApUrl+"?go="+ java.net.URLEncoder.encode(go_uri_as_id);
              String tmp_href_ap_url_page=Config.ppkDefaultHrefApUrl+"?go="+ java.net.URLEncoder.encode(go_uri_as_page);
              
              ap_resp_content="<center><h3>请选择要访问的内容</h3><li><a href='"+tmp_href_ap_url_id+"'>查看该奥丁号的设置属性 "+go_uri_as_id+"</a></li><br><li><a href='"+tmp_href_ap_url_page+"'>查看该奥丁号指向的网站主页 "+go_uri_as_page+"</a></li><font size='-2'>注：该奥丁号需先关联设置有效的主页内容才能被访问到。</font></center>";
          }else{
              go_uri = go_uri_as_page;
              JSONObject obj_decoded_chunk=PTTP.getPPkResource( go_uri );
              
              if(obj_decoded_chunk!=null){
                ap_resp_ppk_uri = obj_decoded_chunk.optString(Config.JSON_KEY_PPK_URI);
                
                int validcode=obj_decoded_chunk.optInt(Config.JSON_KEY_PPK_VALIDATION,Config.PTTP_VALIDATION_ERROR);
                if(validcode==Config.PTTP_VALIDATION_ERROR){
                    ap_resp_content="<font color='#F00'>Valiade failed!</font>";
                }else{
                    ap_resp_url=obj_decoded_chunk.optString(Config.JSON_KEY_CHUNK_URL,"");
                    
                    int status_code = obj_decoded_chunk.getInt(Config.PTTP_KEY_STATUS_CODE);
                    if(status_code==Config.PTTP_STATUS_CODE_OK){
                        String str_chunk_type = obj_decoded_chunk.optString(Config.JSON_KEY_CHUNK_TYPE,"").toLowerCase();
                        if( str_chunk_type.startsWith("text/html") ){ //网页
                          ap_resp_content = new String( (byte[])obj_decoded_chunk.opt(Config.JSON_KEY_CHUNK_BYTES) );
                          
                          //处理页面内容中的图片
                          ap_resp_content = processPPkImagesInPage(ap_resp_content); 
                          
                          //将页面内容中以ppk:起始的href链接替换为适合本地浏览的链接格式
                          String tmp_href_ap_url=Config.ppkDefaultHrefApUrl+"?go="+Config.PPK_URI_PREFIX;

                          ap_resp_content = ap_resp_content.replace("'"+Config.PPK_URI_PREFIX,"'"+tmp_href_ap_url)
                                                           .replace("\""+Config.PPK_URI_PREFIX,"\""+tmp_href_ap_url);
                          
                          
                        }else if(str_chunk_type.startsWith("text")){ //其他文本
                          ap_resp_content = new String( (byte[])obj_decoded_chunk.opt(Config.JSON_KEY_CHUNK_BYTES) );
                        }else if(str_chunk_type.startsWith("image")){
                          ap_resp_content = "<img src='"+Util.imageToBase64DataURL(str_chunk_type,(byte[])obj_decoded_chunk.opt(Config.JSON_KEY_CHUNK_BYTES))+"'>";
                        }else{
                          //Not supported chunk_type now
                          ap_resp_content =  "Not supported chunk_type: "+obj_decoded_chunk.optString(Config.JSON_KEY_CHUNK_TYPE,"");
                        }
                    }else if(status_code==301 || status_code==302){
                        String dest_url = new String( (byte[])obj_decoded_chunk.opt(Config.JSON_KEY_CHUNK_BYTES) );
                        if(dest_url.startsWith(Config.PPK_URI_PREFIX)){
                            dest_url = Config.ppkDefaultHrefApUrl+"?go="+ java.net.URLEncoder.encode(dest_url) ;
                        }
                        ap_resp_content="<html><head><meta http-equiv='refresh' content='2;url="+dest_url+"'></head>Redirecting to "+dest_url+"<html>";
                    }else{
                        ap_resp_content = status_code+" "+(new String( (byte[])obj_decoded_chunk.opt(Config.JSON_KEY_CHUNK_BYTES) ));
                    }
                    
                    
                    //ap_resp_sign = obj_decoded_chunk.optString(Config.PTTP_KEY_SIGNATURE,"");
                    
                    if( validcode == Config.PTTP_VALIDATION_OK )
                       ap_resp_validate_result="<font color='#0F0'>Valiade OK</font>";
                    else if( validcode == Config.PTTP_VALIDATION_IGNORED )
                       ap_resp_validate_result="<font color='#F72'>Valiade ignored! The content unable to be identified. </font>";
                    else
                       ap_resp_validate_result="<font color='#F72'>Valiade unknown("+validcode+"). </font>";

                    ap_resp_content += "\n<!--"+ap_resp_validate_result+"--><!--RESP_PPK_URI="+ap_resp_ppk_uri+"--><!--RESP_URL="+ap_resp_url+"-->";   

                    if(Config.debugKey){
                        long exp_utc = obj_decoded_chunk.optLong(Config.JSON_KEY_EXP_UTC);
                        ap_resp_content += "\n<hr>DEBUG: "+ap_resp_validate_result
                                            +" <br>\nREQ_URI= "+go_uri
                                            +" <br>\nRESP_URI= "+ap_resp_ppk_uri
                                            +" <br>\nRESP_AP= "+ap_resp_url
                                            +" <br>\nFROM_CACHE= "+obj_decoded_chunk.optBoolean(Config.JSON_KEY_FROM_CACHE)
                                            +" <br>\nEXP_UTC= "+ exp_utc
                                            +" <br>\nLEFT_SECONDS= "+ ( exp_utc - Util.getNowTimestamp() )
                                            +" \n<hr><center><a href='"+Config.ppkDefaultHrefApUrl+"'>返回主页 "+Config.ppkDefaultHrefApUrl+" </a></center>"; 
                    }            
                }
              }else{
                ap_resp_content="<font color='#F00'>No valid response</font>";
              }
          }
          
          attributes.put("title", go_uri);
          attributes.put("go_uri", go_uri);
          attributes.put("ap_resp_content", ap_resp_content);   
          attributes.put("ap_resp_ppk_uri", ap_resp_ppk_uri);   
          attributes.put("ap_resp_url", ap_resp_url);   
          attributes.put("ap_resp_sign", ap_resp_sign);   
          //attributes.put("vd_set_pubkey", vd_set_pubkey);   
          attributes.put("ap_resp_validate_result", ap_resp_validate_result); 
		}catch (Exception e) {
		  System.out.println("handlePttpBrowserRequest() error: "+e.toString());
		  e.printStackTrace();
		}
	}else{
		String ap_resp_content = Util.fetchUriContent(go_uri);
		attributes.put("title", go_uri);
		attributes.put("go_uri", go_uri);
		attributes.put("ap_resp_content", ap_resp_content);   
		attributes.put("ap_resp_ppk_uri", go_uri);   
		attributes.put("ap_resp_url", go_uri);   
		attributes.put("ap_resp_sign", "");   
		//attributes.put("vd_set_pubkey", vd_set_pubkey);   
		attributes.put("ap_resp_validate_result", "<font color='#F72'>Valiade ignored! The content unable to be identified. </font>");     
	}
	
    attributes.put("LANG_BROWSE_PPK_NETWORK", Language.getLangLabel("Browse PPk network"));
    attributes.put("LANG_RESPONSE_URI", Language.getLangLabel("Response URI"));
    attributes.put("LANG_RESPONSE_URL", Language.getLangLabel("Response URL"));
    attributes.put("LANG_VALIDATE_RESULT", Language.getLangLabel("Validate result"));
    attributes.put("LANG_CLICKED_WAITING", Language.getLangLabel("Waiting"));  
    
    return attributes;
  }
  
  //处理页面中包含的"ppk:"起始的图片
  protected static String processPPkImagesInPage(String str_content){
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
      JSONObject obj_decoded_chunk = PTTP.getPPkResource(old_img_src);
      if(obj_decoded_chunk!=null){
        String str_chunk_type = obj_decoded_chunk.optString(Config.JSON_KEY_CHUNK_TYPE,"").toLowerCase();
        
        String new_img_src = Util.imageToBase64DataURL(str_chunk_type,(byte[])obj_decoded_chunk.opt(Config.JSON_KEY_CHUNK_BYTES));
        //System.out.println("RRRRRRRR "+old_img_src+"  -> "+new_img_src +" size="+new_img_src.length());
        str_content = str_content.replace("src='"+old_img_src+"'","src='"+new_img_src+"'")
                                 .replace("src=\""+old_img_src+"\"","src=\""+new_img_src+"\"");
      }
    }
    
    return str_content;
  }
}