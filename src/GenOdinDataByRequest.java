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
import java.nio.ByteBuffer;

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
    
    OdinInfo odinInfo=ODIN.getOdinInfo(odin);
    if(odinInfo==null){
        throw new Exception("Invalid odin.");
    } 
    
    return odinInfo;
  }   
  
  public static OdinTransctionData genOdinDataOfAdd(Request request,Map<String, Object> attributes) throws Exception{
      System.out.println("************* do genOdinDataOfAdd **************");
      
      String register = request.queryParams("register");
      String admin = request.queryParams("admin_address");
      String titleStr=request.queryParams("title");
      String emailStr=request.queryParams("email");
      String pnsUrlStr=request.queryParams("pns_url");
      String authSet=request.queryParams("auth");

      
      
      return genOdinDataOfAdd(register,admin,titleStr,emailStr,pnsUrlStr,authSet);
  }
  
  public static OdinTransctionData genOdinDataOfAdd(String register,String admin,String titleStr,String emailStr,String pnsUrlStr,String authSet) throws Exception{
      try{
        //检查admin地址格式，如果是奥丁号则相应解析提取对应的BTC地址
        if( admin.length()>0 && admin.length()<30 ){
            admin = getBtcAddressOfODIN(admin);
        }
      } catch(Exception e){
        admin="";
      }
    
      if(register.length()==0 || admin.length()==0 || authSet.length()==0 ){
        throw new Exception("invalid register, admin or authority");
      }
      
      OdinTransctionData odin_data = null;
      
      Map mapOdinSet = new HashMap(); 
                
      mapOdinSet.put("ver", Config.ODIN_PROTOCOL_VER); 
      //mapOdinSet.put("x-tool", Config.appName+Config.version); //加上自定义的扩展信息：客户端和版本信息，用于临时调试,20181222
      mapOdinSet.put(Config.ODIN_BASE_SET_AUTH, authSet); 

      if( register.equals(admin))
         admin="";
    
      if( titleStr!=null && titleStr.length()>0)
         mapOdinSet.put("title", titleStr); 
     
      if( emailStr!=null && emailStr.length()>0)
         mapOdinSet.put("email", emailStr); 
     
      if( pnsUrlStr !=null && pnsUrlStr.length()>0)
         mapOdinSet.put(Config.ODIN_BASE_SET_PNS_URL, pnsUrlStr); 
       
      JSONObject odin_set = new JSONObject(mapOdinSet); 
      
      odin_data = ODIN.createOdin(register, admin,odin_set);

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
    String pnsUrlStr=request.queryParams("pns_url");
    String authSet=request.queryParams("auth");
    
    try{
        //检查admin地址格式，如果是奥丁号则相应解析提取对应的BTC地址
        if( admin.length()>0 && admin.length()<30 ){
            admin = getBtcAddressOfODIN(admin);
        }
    } catch(Exception e){
        admin="";
    }

    if(admin.length()==0 || authSet.length()==0 ){
        throw new Exception("invalid admin or authority");
    }

    HashMap<String,Object> map = ODIN.parseOdinSet(odinInfo,address,odinInfo.register,odinInfo.admin);

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
    
    if(!pnsUrlStr.equals(map.get(Config.ODIN_BASE_SET_PNS_URL).toString()) ) {
      mapNewOdinSet.put(Config.ODIN_BASE_SET_PNS_URL, pnsUrlStr); 
      needBroadcast=true;
    }
    
    if(!authSet.equals(map.get(Config.ODIN_BASE_SET_AUTH).toString()) ) {
      mapNewOdinSet.put(Config.ODIN_BASE_SET_AUTH, authSet); 
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
    
    HashMap<String,Object> map = ODIN.parseOdinSet(odinInfo,address,odinInfo.register,odinInfo.admin);

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
    
    HashMap<String,Object> map = ODIN.parseOdinSet(odinInfo,address,odinInfo.register,odinInfo.admin);

    String new_vd_set_cert_uri=request.queryParams("new_vd_set_cert_uri");
    String new_vd_set_type=request.queryParams("new_vd_set_type");
    String new_vd_set_pubkey=request.queryParams("new_vd_set_pubkey");

    if(new_vd_set_cert_uri!=null){ //提供URI形式
         new_vd_set_cert_uri=new_vd_set_cert_uri.trim();
         
         attributes.put("new_vd_set_cert_uri", new_vd_set_cert_uri);
         attributes.put("new_vd_set_type", new_vd_set_type);

         /*
         if( Util.isURI(new_vd_set_cert_uri) ){
            //尝试按URI取值来获取实际的公钥
            //如果出错则忽略，保存原值
            new_vd_set_pubkey=Util.fetchUriContent(new_vd_set_cert_uri);
            
            if( new_vd_set_pubkey==null || new_vd_set_pubkey.trim().length()==0 ){
              attributes.put("error", Language.getLangLabel("Please input valid URI of certificate."));
            }
         }
         */
        
        new_vd_set_pubkey = new_vd_set_cert_uri;  //将URI直接存到链上
         
    }else if(new_vd_set_pubkey!=null  ){ 
        if(new_vd_set_pubkey.trim().length()>64)
        {
            //对于超长公钥需先存到分布式存储
            String tmp_ap_type = request.queryParams("ap_type"); 
            new_vd_set_cert_uri=Util.uploadToAP(tmp_ap_type,new_vd_set_pubkey.getBytes(),odinInfo.shortOdin.toString()+"K");
            if( new_vd_set_cert_uri==null || new_vd_set_cert_uri.length()==0 ){
                  throw new Exception("["+tmp_ap_type+"]"+Language.getLangLabel(" is invalid. Please retry the other storage service."));    
            }

            attributes.put("new_vd_set_type", new_vd_set_type);
            attributes.put("new_vd_set_pubkey", new_vd_set_pubkey);
            
            new_vd_set_pubkey = new_vd_set_cert_uri;  //将URI直接存到链上
        }
    }else{
        throw new Exception(Language.getLangLabel("Invalid request."));        
    }

    if(!map.containsKey("me_updatable")){
        throw new Exception(Language.getLangLabel("No permission."));
    }
    
    if( new_vd_set_pubkey!=null ){
      JSONObject new_vd_set=new JSONObject();
      new_vd_set.put(Config.ODIN_SET_VD_TYPE,new_vd_set_type);
      new_vd_set.put(Config.ODIN_SET_VD_PUBKEY,new_vd_set_pubkey);
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
    
    try{
        //检查admin地址格式，如果是奥丁号则相应解析提取对应的BTC地址
        if( new_register.length()>0 && new_register.length()<30 ){
            new_register = getBtcAddressOfODIN(new_register);
        }
    } catch(Exception e){
        new_register="";
    }

    if( new_register.length()==0 || new_register.equals(address) ){
        throw new Exception(Language.getLangLabel("Please input another valid register address."));
    }
      
    HashMap<String,Object> map = new HashMap<String,Object>();
    map.put("full_odin", odinInfo.fullOdin);
    map.put("short_odin", odinInfo.shortOdin.toString());
    map.put(Config.ODIN_BASE_SET_REGISTER, odinInfo.register);
    map.put("validity",odinInfo.validity);

    JSONObject odin_set = odinInfo.odinSet; 
    map=ODIN.parseOdinSet(map,odin_set,address,odinInfo.register,odinInfo.admin);

    if(!map.containsKey("me_updatable")){
        throw new Exception(Language.getLangLabel("No permission."));
    }
    
    odin_data = OdinUpdate.transOdinRegister(odinInfo.fullOdin,address,new_register);


    return odin_data;
  }
  
  public static OdinTransctionData genOdinDataOfSignAP(Request request,Map<String, Object> attributes) throws Exception{
    System.out.println("************* do genOdinDataOfSignAP **************");
    OdinTransctionData odin_data = null;  
    
    OdinInfo odinInfo=getExistedOdinInfo(request);
    String address=(String)attributes.get("address");
    String tmp_apid = request.queryParams("apid");
    if(tmp_apid==null || tmp_apid.length()==0){
        tmp_apid="0";
    } 
    
    HashMap<String,Object> map = ODIN.parseOdinSet(odinInfo,address,odinInfo.register,odinInfo.admin);

    if(!map.containsKey("me_updatable")){
        throw new Exception(Language.getLangLabel("No permission."));
    }
    
    String tmp_ap_type = request.queryParams("ap_type");
    
    String tmp_sign_spec = request.queryParams("sign_spec");
    String tmp_signature = request.queryParams("signature");
    String tmp_ap_data_no_sign_encoded = request.queryParams("ap_data_no_sign_encoded");
    String tmp_ap_data_signed=null;
    
    if(tmp_sign_spec==null || tmp_sign_spec.trim().length()==0)
        tmp_sign_spec = Config.PTTP_KEY_SPEC_NONE;
    
    try{
        tmp_ap_data_no_sign_encoded=new String(Util.hexStringToBytes(tmp_ap_data_no_sign_encoded),Config.PPK_TEXT_CHARSET);
        String tmp_ap_data_no_sign=java.net.URLDecoder.decode(tmp_ap_data_no_sign_encoded, "UTF-8");
        
        JSONObject obj_ap_data_no_sign=new JSONObject(tmp_ap_data_no_sign);
    
        System.out.println("tmp_signature="+tmp_signature);
        if( tmp_signature.length()==0 && !tmp_sign_spec.equals(Config.PTTP_KEY_SPEC_NONE)  ){
            //需要本地生成签名
            String tmp_private_key=request.queryParams("sign_prvkey");
            if( tmp_private_key==null || tmp_private_key.length()==0 )
                throw new Exception(Language.getLangLabel("Please input private key"));
            
            //采用PAST规范v1.public生成带签名的数据报文
            //if( str_spec.equals(Config.PTTP_KEY_SPEC_PAST+Config.PTTP_KEY_SPEC_PAST_HEADER_V1_PUBLIC) ){
            byte[] key_bytes =  RSACoder.decryptBASE64( RSACoder.parseValidPubKey(tmp_private_key) )  ;

            String payload = Config.PTTP_SIGN_MARK_DATA
                          + obj_ap_data_no_sign.getString(Config.PTTP_KEY_URI)
                          + obj_ap_data_no_sign.getString(Config.PTTP_KEY_METAINFO)
                          + obj_ap_data_no_sign.getString(Config.PTTP_KEY_CONTENT);

            tmp_signature = PasetoPublic.sign(key_bytes, payload, "",true);


        }
        
        obj_ap_data_no_sign.put(Config.PTTP_KEY_SPEC, tmp_sign_spec );
        obj_ap_data_no_sign.put(Config.PTTP_KEY_SIGNATURE, tmp_signature );
        
        tmp_ap_data_signed=obj_ap_data_no_sign.toString();

        System.out.println("tmp_ap_data_signed="+tmp_ap_data_signed);
    }catch(Exception e){
        System.out.println("genOdinDataOfSignAP() error: "+e.toString());
        e.printStackTrace();
    }
    

    if( tmp_ap_data_signed == null || tmp_ap_data_signed.length()==0){
        throw new Exception(Language.getLangLabel(Config.ODIN_STATUS_INVALID));
    }
    
    /*
    String sign_pubkey = request.queryParams("sign_pubkey");

    JSONObject exist_vd_set=odinInfo.odinSet.optJSONObject("vd_set");
    if(exist_vd_set!=null){
        String vd_set_pubkey=exist_vd_set.optString(Config.ODIN_SET_VD_PUBKEY,"");
        System.out.println("vd_set_pubkey="+vd_set_pubkey);
        if(!sign_pubkey.equals(vd_set_pubkey)){
            throw new Exception(Language.getLangLabel("Mismatched pubkey"));
        }
    }
    
    try{
        JSONObject obj_ap_data=new JSONObject(tmp_ap_data_signed);

        //检查内容签名
        String str_original_data_json = obj_ap_data.getString("data");
        byte[] original_data = str_original_data_json.getBytes();
        //ByteBuffer byteBuffer = ByteBuffer.allocate(original_data.length);
        //byteBuffer.put(original_data,0,original_data.length);
        
        String str_ppk_sign=obj_ap_data.getString(Config.PTTP_KEY_SIGNATURE);
        String[] sign_pieces = str_ppk_sign.split("\\:");
        String ap_resp_sign_algo=sign_pieces[0].trim();
        String ap_resp_sign_base64=sign_pieces[1].trim();
        //String ap_resp_sign_pubkey=sign_pieces[2].trim(); //just for test
        System.out.println("ap_resp_sign_algo="+ap_resp_sign_algo+",ap_resp_sign_base64="+ap_resp_sign_base64);
        
        if(!RSACoder.verify(original_data, sign_pubkey,ap_resp_sign_base64,ap_resp_sign_algo )){
           System.out.println("Found invalid sign");
           throw new Exception(Language.getLangLabel("invalid sign"));
        }
    }catch(Exception e){
        e.printStackTrace();
        throw new Exception(Language.getLangLabel(Config.ODIN_STATUS_INVALID)+" "+e.toString());
    }
    */
    
    //Upload to th AP
    String tmp_ap_url_str=Util.uploadToAP(tmp_ap_type,tmp_ap_data_signed.getBytes(Config.PPK_TEXT_CHARSET),odinInfo.shortOdin.toString()+"P");
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
    String feeStr=request.queryParams("fee");

    try{
        //检查地址格式，如果是奥丁号则相应解析提取对应的BTC地址
        if( destination.length()>0 && destination.length()<30 ){
            destination = getBtcAddressOfODIN(destination);
        }
    } catch(Exception e){
        destination="";
    }

    if(destination.length()==0){
        throw new Exception(Language.getLangLabel("Please input a valid destination address that you want to send."));
    }else if(quantityStr.length()==0){
        throw new Exception(Language.getLangLabel("Please input the BTC amount that you want to send."));
    }else if(feeStr.length()==0){
        throw new Exception(Language.getLangLabel("Please input the fee amount that you want to pay miner."));
    }  
    
    try {
      BigDecimal btc_unit=new BigDecimal(Config.btc_unit);
      
      BigDecimal rawQuantity = new BigDecimal(quantityStr);
      BigInteger quantity = rawQuantity.multiply( btc_unit ).toBigInteger();
      BigDecimal rawFee = new BigDecimal(feeStr);
      BigInteger fee = rawFee.multiply(btc_unit).toBigInteger();

      odin_data = Send.create(source, destination, "BTC", quantity,fee);
    } catch (Exception e) {
      throw new Exception("genNormalSendTX:"+e.getMessage());
    }
    
    return odin_data;
  }
  
  //尝试按奥丁号关联获取对应的实际钱包地址
  protected static String getBtcAddressOfODIN(String destination) {
  	  try{
  	      if(destination!=null && destination.length()>0){
                destination = ODIN.formatPPkURI(destination,true);
                if(destination!=null){
                    String ap_resp_content = Util.fetchUriContent(destination);
                    System.out.println("************* getBtcAddressOfODIN() ap_resp_content=");
                    System.out.println(ap_resp_content);
                    JSONObject tmp_dest_info = new JSONObject(ap_resp_content);
                    String register=tmp_dest_info.optString("register","");
                    JSONObject x_wallet_list = tmp_dest_info.optJSONObject("x_wallets");
                    if(x_wallet_list!=null){
                        JSONObject tmp_address_set = x_wallet_list.optJSONObject("ppk:btc/");
                        if(tmp_address_set!=null)
                            destination = tmp_address_set.optString("address","");
                        else
                            destination = register;
                    }else{
                        destination = register;
                    }
                    
                    System.out.println("getBtcAddressOfODIN() formated_destination="+destination);
                }
  	      }
          Address.getParametersFromAddress(destination);
  	  } catch(Exception e){
  	      destination="";
  	  }
      
      return destination;
  }
}
