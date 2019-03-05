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
import java.util.Calendar;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import spark.Request;
import org.bitcoinj.core.Address;

public class GenOdinDataByRequest {
  public static OdinInfo getExistedOdinInfo(Request request) throws Exception{
    String odin=request.queryParams("odin");
    if(odin==null){
        throw new Exception("no odin.");
    } 
    
    OdinInfo odinInfo=Odin.getOdinInfo(odin);
    if(odinInfo==null){
        throw new Exception("Invalid odin.");
    } 
    
    return odinInfo;
  }   
  
  public static OdinTransctionData genOdinDataOfAdd(Request request,Map<String, Object> attributes) throws Exception{
      System.out.println("************* do genOdinDataOfAdd **************");
    
      OdinTransctionData odin_data = null;
      
      String register = request.queryParams("register");
      String admin = request.queryParams("admin_address");
      String titleStr=request.queryParams("title");
      String emailStr=request.queryParams("email");
      String authSet=request.queryParams("auth");

      if(admin.length()==0 || authSet.length()==0 ){
        throw new Exception("invalid admin or authority");
      }
      
      Map mapOdinSet = new HashMap(); 
                
      mapOdinSet.put("ver", Config.ODIN_PROTOCOL_VER); 
      //mapOdinSet.put("x-tool", Config.appName+Config.version); //加上自定义的扩展信息：客户端和版本信息，用于临时调试,20181222
      mapOdinSet.put("auth", authSet); 

      if(register.equals(admin))
         admin="";
    
      if(titleStr.length()>0)
         mapOdinSet.put("title", titleStr); 
     
      if(emailStr.length()>0)
         mapOdinSet.put("email", emailStr); 
       
      JSONObject odin_set = new JSONObject(mapOdinSet); 
      
      odin_data = Odin.createOdin(register, admin,odin_set);

      
      return odin_data;
  }
    
  public static OdinTransctionData genOdinDataOfUpdateBaseInfo(Request request,Map<String, Object> attributes) throws Exception{
    System.out.println("************* do genOdinDataOfUpdateBaseInfo **************");
    OdinTransctionData odin_data = null;  

    OdinInfo odinInfo=getExistedOdinInfo(request);
    String address=(String)attributes.get("address");
    
    String admin = request.queryParams("admin");
    String titleStr=request.queryParams("title");
    String emailStr=request.queryParams("email");
    String authSet=request.queryParams("auth");

    if(admin.length()==0 || authSet.length()==0 ){
        throw new Exception("invalid admin or authority");
    }

    HashMap<String,Object> map = Odin.parseOdinSet(odinInfo,address,odinInfo.register,odinInfo.admin);

    if(!map.containsKey("me_updatable")){
        throw new Exception(Language.getLangLabel("No permission."));
    }
    
    Map mapNewOdinSet = new HashMap(); 
    boolean  needBroadcast=false;

    mapNewOdinSet.put("ver", Config.ODIN_PROTOCOL_VER); 
    mapNewOdinSet.put("cmd", Config.ODIN_CMD_UPDATE_BASE_INFO); 
    
    if(!admin.equals(odinInfo.admin) ){ //Need update while admin was changed
      needBroadcast=true;
    }else{
      admin=""; //Not need update admin while not changed
    }
    
    if(!titleStr.equals(map.get("title").toString()) ){//Only value that changed need to be updated
      mapNewOdinSet.put("title", titleStr); 
      needBroadcast=true;
    }
    
    if(!emailStr.equals(map.get("email").toString()) ) {
      mapNewOdinSet.put("email", emailStr); 
      needBroadcast=true;
    }
    
    if(!authSet.equals(map.get("auth").toString()) ) {
      mapNewOdinSet.put("auth", authSet); 
      needBroadcast=true;
    }
    
    if(!needBroadcast){
        throw new Exception(Language.getLangLabel("Please make sure that you have changed some values then submit."));
    }
    
    JSONObject new_odin_set = new JSONObject(mapNewOdinSet); 
    odin_data = OdinUpdate.updateOdinBaseInfo(odinInfo.fullOdin,address,admin,new_odin_set);
   
    return odin_data;
  }
  
  public static OdinTransctionData genOdinDataOfUpdateAP(Request request,Map<String, Object> attributes) throws Exception{
    System.out.println("************* do  genOdinDataOfUpdateAP **************");
    OdinTransctionData odin_data = null;  
    
    OdinInfo odinInfo=getExistedOdinInfo(request);
    String address=(String)attributes.get("address");
    
    HashMap<String,Object> map = Odin.parseOdinSet(odinInfo,address,odinInfo.register,odinInfo.admin);

    JSONObject apUpdate = new JSONObject(); 
    for(int tt=0;tt<5;tt++){
      String tmp_ap_url_str = request.queryParams("ap"+tt+"_url");
      if(tmp_ap_url_str==null)
        tmp_ap_url_str="";
      else
        tmp_ap_url_str.trim();
      
      String old_ap_url_str=(String)map.get("ap"+tt+"_url");
      if(old_ap_url_str==null)
        old_ap_url_str="";

      System.out.println("tmp_ap_url_str="+tmp_ap_url_str+"  oldurl="+old_ap_url_str);
      if( !tmp_ap_url_str.equals(old_ap_url_str) ){
          Map map_new_ap_record = new HashMap(); 
          map_new_ap_record.put("url", tmp_ap_url_str); 
          apUpdate.put(""+tt,new JSONObject(map_new_ap_record));
      } 
      map.put("ap"+tt+"_url", tmp_ap_url_str);
    }
    
    if(!map.containsKey("me_updatable")){
        throw new Exception(Language.getLangLabel("No permission."));
    }

    if( apUpdate.length()==0 ){
        throw new Exception(Language.getLangLabel("Please update at least one valid access point."));
    } 
      
    odin_data = OdinUpdate.updateOdinApSet(odinInfo.fullOdin,address,apUpdate);
    
    return odin_data;
  }
  
  public static OdinTransctionData genOdinDataOfUpdateVdSet(Request request,Map<String, Object> attributes) throws Exception{
    System.out.println("************* do genOdinDataOfUpdateVdSet **************");
    OdinTransctionData odin_data = null;  
    
    OdinInfo odinInfo=getExistedOdinInfo(request);
    String address=(String)attributes.get("address");
    
    HashMap<String,Object> map = Odin.parseOdinSet(odinInfo,address,odinInfo.register,odinInfo.admin);

    String new_vd_set_cert_uri=request.queryParams("new_vd_set_cert_uri");
    String new_vd_set_algo=request.queryParams("new_vd_set_algo");
    //String new_vd_set_format=request.queryParams("new_vd_set_format");
    String new_vd_set_pubkey=request.queryParams("new_vd_set_pubkey");

    if(new_vd_set_cert_uri!=null){
     new_vd_set_cert_uri=new_vd_set_cert_uri.trim();
     attributes.put("new_vd_set_cert_uri", new_vd_set_cert_uri);
     if( new_vd_set_cert_uri.length()==0 ){
          attributes.put("error", Language.getLangLabel("Please input valid URI of certificate."));
     }
    }else if(new_vd_set_pubkey!=null && new_vd_set_pubkey.trim().length()>0 ){
    String tmp_ap_type = request.queryParams("ap_type");
    new_vd_set_cert_uri=Util.uploadToAP(tmp_ap_type,new_vd_set_pubkey.getBytes(),odinInfo.shortOdin.toString()+"K");
    if( new_vd_set_cert_uri==null){
      attributes.put("error", "["+tmp_ap_type+"]"+Language.getLangLabel(" is invalid. Please retry the other storage service."));
      //new_vd_set_cert_uri="data:,"+new_vd_set_pubkey;
    }

    attributes.put("new_vd_set_algo", new_vd_set_algo);
    //attributes.put("new_vd_set_format", new_vd_set_format);
    attributes.put("new_vd_set_pubkey", new_vd_set_pubkey);
    }

    if(!map.containsKey("me_updatable")){
        throw new Exception(Language.getLangLabel("No permission."));
    }
    if( new_vd_set_cert_uri!=null && new_vd_set_cert_uri.length()>0 ){
      JSONObject new_vd_set=new JSONObject();
      new_vd_set.put(Config.JSON_KEY_PPK_ALGO,new_vd_set_algo);
      new_vd_set.put(Config.JSON_KEY_PPK_CERT_URI,new_vd_set_cert_uri);
      odin_data = OdinUpdate.updateOdinVdSet(odinInfo.fullOdin,address,new_vd_set);
    }

    return odin_data;
  }
  
  public static OdinTransctionData genOdinDataOfTransRegister(Request request,Map<String, Object> attributes) throws Exception{
    System.out.println("************* do genOdinDataOfTransRegister **************");
    OdinTransctionData odin_data = null;  
    
    OdinInfo odinInfo=getExistedOdinInfo(request);
    String address=(String)attributes.get("address");
    
    String new_register = request.queryParams("new_register");

    if( new_register.length()==0 || new_register.equals(address) ){
        throw new Exception(Language.getLangLabel("Please input another valid register address."));
    }
      
    HashMap<String,Object> map = new HashMap<String,Object>();
    map.put("full_odin", odinInfo.fullOdin);
    map.put("short_odin", odinInfo.shortOdin.toString());
    map.put("register", odinInfo.register);
    map.put("validity",odinInfo.validity);

    JSONObject odin_set = odinInfo.odinSet; 
    map=Odin.parseOdinSet(map,odin_set,address,odinInfo.register,odinInfo.admin);

    if(!map.containsKey("me_updatable")){
        throw new Exception(Language.getLangLabel("No permission."));
    }
    
    odin_data = OdinUpdate.transOdinRegister(odinInfo.fullOdin,address,new_register);


    return odin_data;
  }
  
  public static OdinTransctionData genOdinDataOfCreateFirstAP(Request request,Map<String, Object> attributes) throws Exception{
    System.out.println("************* do genOdinDataOfCreateFirstAP **************");
    OdinTransctionData odin_data = null;  
    
    OdinInfo odinInfo=getExistedOdinInfo(request);
    String address=(String)attributes.get("address");
    String tmp_apid = request.queryParams("apid");
    if(tmp_apid==null || tmp_apid.length()==0){
        tmp_apid="0";
    } 
    
    HashMap<String,Object> map = Odin.parseOdinSet(odinInfo,address,odinInfo.register,odinInfo.admin);

    if(!map.containsKey("me_updatable")){
        throw new Exception(Language.getLangLabel("No permission."));
    }
    
    //Generate PTTP data package of the sample page 
    //String tmp_page_title = request.queryParams("ap_page_title");
    String tmp_page_content_encoded = request.queryParams("ap_page_content_encoded");
    String tmp_ap_type = request.queryParams("ap_type");

    String tmp_private_key=request.queryParams("sign_prvkey");
    String tmp_sign_algo=request.queryParams("sign_algo");

    tmp_page_content_encoded=new String(Util.hexStringToBytes(tmp_page_content_encoded),Config.PPK_TEXT_CHARSET);
    String chunk_content=java.net.URLDecoder.decode(tmp_page_content_encoded, "UTF-8");
    String content_type="text/html";
    int status_code=200;
    String status_info="OK";
    
    if(chunk_content.startsWith("302 ")){
        status_code=302;
        content_type="x-ppk/link";
        chunk_content=chunk_content.substring(4);
        status_info="Moved Temporarily";
    }
    /*
    if( tmp_page_content.contains("<head>") || tmp_page_content.contains("<HEAD>") )
        chunk_content=tmp_page_content;
    else //自动补上HTML头部
        chunk_content="<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>AP["+tmp_apid+"] sample</title><meta content=\"IE=edge\" http-equiv=\"X-UA-Compatible\"><meta content=\"width=device-width, initial-scale=1\" name=\"viewport\"></head><body>"+tmp_page_content+"</body></html>";
    */
    
    String sample_ppk_uri =  Config.PPK_URI_PREFIX + odinInfo.fullOdin +"/#" + Util.getNowTimestamp();
    System.out.println("sample_ppk_uri="+sample_ppk_uri);

    JSONObject obj_chunk_metainfo=new JSONObject();
    obj_chunk_metainfo.put("chunk_index", 0 );
    obj_chunk_metainfo.put("chunk_count", 1 );
    obj_chunk_metainfo.put("content_type", content_type  );
    obj_chunk_metainfo.put("content_length", chunk_content.length()  );

    JSONObject obj_newest_ap_chunk=new JSONObject();
    obj_newest_ap_chunk.put("uri",sample_ppk_uri);
    obj_newest_ap_chunk.put("status_code",status_code);
    obj_newest_ap_chunk.put("status_info","OK");

    obj_newest_ap_chunk.put("metainfo",obj_chunk_metainfo);
    obj_newest_ap_chunk.put("content",chunk_content);

    String obj_newest_ap_chunk_str=obj_newest_ap_chunk.toString();
    String tmp_sign="";

    if( tmp_private_key!=null && tmp_private_key.length()>0
        && tmp_sign_algo!=null && tmp_sign_algo.length()>0
     ){
      tmp_sign=tmp_sign_algo+":"+RSACoder.sign(
            obj_newest_ap_chunk_str.getBytes() , tmp_private_key,tmp_sign_algo
         );
    }

    JSONObject obj_newest_ap_data=new JSONObject();
    obj_newest_ap_data.put("ver",1);
    obj_newest_ap_data.put("data",obj_newest_ap_chunk_str);
    obj_newest_ap_data.put("sign", tmp_sign );

    //Upload to th AP
    String tmp_ap_url_str=Util.uploadToAP(tmp_ap_type,obj_newest_ap_data.toString().getBytes(Config.PPK_TEXT_CHARSET),odinInfo.shortOdin.toString()+"P");
    if(tmp_ap_url_str==null || tmp_ap_url_str.length()==0 ){
        throw new Exception(Language.getLangLabel("Invalid Inputs"));
    }
    System.out.println("tmp_ap_url_str="+tmp_ap_url_str);

    JSONObject apUpdate = new JSONObject(); 

    //检查AP地址是否发生有效变更
    String old_ap_url_str=(String)map.get("ap"+tmp_apid+"_url");
    if(old_ap_url_str==null)
    old_ap_url_str="";

    System.out.println("tmp_ap_url_str="+tmp_ap_url_str+"  oldurl="+old_ap_url_str);
    if( !tmp_ap_url_str.equals(old_ap_url_str) ){
      Map map_new_ap_record = new HashMap(); 
      map_new_ap_record.put("url", tmp_ap_url_str); 
      apUpdate.put(tmp_apid,new JSONObject(map_new_ap_record));
    } 

    if( apUpdate.length()>0 ){
      odin_data = OdinUpdate.updateOdinApSet(odinInfo.fullOdin,address,apUpdate);
    }
    
    return odin_data;
  }
  
  public static OdinTransctionData genOdinDataOfConfirmUpdateLog(Request request,Map<String, Object> attributes) throws Exception{
    System.out.println("************* do genOdinDataOfConfirmUpdateLog **************");
    OdinTransctionData odin_data = null;
    
    OdinInfo odinInfo=getExistedOdinInfo(request);
    String address=(String)attributes.get("address");
    
    JSONArray confirm_update_list=new JSONArray();
    int awaiting_update_log_num=Integer.parseInt(request.queryParams("awaiting_update_log_num"));
    
    for(int sn=0; sn<awaiting_update_log_num;sn++ ){
      String awaiting_update_log_id=request.queryParams("update_log_id"+sn);

      if(awaiting_update_log_id!=null){
          confirm_update_list.put(awaiting_update_log_id);
      }
    }
    if(confirm_update_list.length()>0){
        odin_data = OdinUpdate.confirmUpdate(odinInfo.fullOdin,address,confirm_update_list);
    }else{
        throw new Exception("genOdinDataOfConfirmUpdateLog : invalid awaiting_update_log_id.");
    }
    
    return odin_data;
  }
  
  public static OdinTransctionData genNormalSendTX(Request request,Map<String, Object> attributes) throws Exception{
    System.out.println("************* do genNormalSendTX **************\n");
    OdinTransctionData odin_data = null;
    
    String source = request.queryParams("source");
    String destination = request.queryParams("destination");
    String quantityStr=request.queryParams("quantity");

    try{
        Address.getParametersFromAddress(destination);
    } catch(Exception e){
        destination="";
    }

    if(destination.length()==0){
        throw new Exception(Language.getLangLabel("Please input a valid destination address that you want to send."));
    }else if(quantityStr.length()==0){
        throw new Exception(Language.getLangLabel("Please input the BTC amount that you want to send."));
    } 
    
    try {
      Double rawQuantity = Double.parseDouble(quantityStr);
      BigInteger quantity = new BigDecimal(rawQuantity*Config.btc_unit).toBigInteger();

      odin_data = Send.create(source, destination, "BTC", quantity);
    } catch (Exception e) {
      throw new Exception("genNormalSendTX:"+e.getMessage());
    }
    
    return odin_data;
  }
  
}
