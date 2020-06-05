import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bitcoinj.core.Transaction;

import org.json.JSONArray;
import org.json.JSONObject;

public class OdinUpdate {
  static Logger logger = LoggerFactory.getLogger(OdinUpdate.class);
  public static Byte id = Config.FUNC_ID_ODIN_UPDATE; //for updateing an exist ODIN 
  public static int UPDATE_ODIN_PREFIX_LENGTH=Config.PPK_PUBKEY_EMBED_DATA_MAX_LENGTH-1;
  
  public static void init(){
      
  }

  public static void parse(Integer txIndex, List<Byte> message) {
    logger.info( "\n=============================\n Parsing OdinUpdate txIndex="+txIndex.toString()+", message.size()="+message.size()+"\n=====================\n");
    
    Database db = Database.getInstance();
    ResultSet rs = db.executeQuery("SELECT * FROM transactions tx  WHERE tx.tx_index="+txIndex.toString());
    try {
      if (rs.next()) {
        String source = rs.getString("source");
        String destination = rs.getString("destination");
        BigInteger btcAmount = BigInteger.valueOf(rs.getLong("btc_amount"));
        BigInteger fee = BigInteger.valueOf(rs.getLong("fee"));
        Integer blockIndex = rs.getInt("block_index");
        Integer blockTime = rs.getInt("block_time");
        String txHash = rs.getString("tx_hash");
        Integer txSnInBlock = rs.getInt("sn_in_block");
        String new_log_id=blockIndex+"."+txSnInBlock;
                
        //if(destination.length()==0) 
        //  destination=source;

        ResultSet rsCheck = db.executeQuery("select * from odin_update_logs where tx_index='"+txIndex.toString()+"'");
        if (rsCheck.next())   
          return;

        if (message.size()>UPDATE_ODIN_PREFIX_LENGTH) {
          ByteBuffer byteBuffer = ByteBuffer.allocate(message.size());
          for (byte b : message) {
            byteBuffer.put(b);
          }      
          
          String validity = Config.ODIN_STATUS_INVALID;
          String required_confirmer="";
          JSONObject update_set=new JSONObject( );
          
          String full_odin=getFullOdinFromUpdateMessage(byteBuffer); 
          OdinInfo oldOdinInfo=ODIN.getOdinInfo(full_odin);
          
          //logger.info( "full_odin="+full_odin+",source="+source+",oldOdinInfo="+oldOdinInfo.toString());
          if(oldOdinInfo!=null && !source.equals("") ){
            String authSet=""+oldOdinInfo.odinSet.optInt(Config.ODIN_BASE_SET_AUTH,0); //兼容处理填写非数字的取值
            
            Byte update_set_data_type=byteBuffer.get(UPDATE_ODIN_PREFIX_LENGTH); 
            BitcoinVarint update_set_len_varint=BitcoinVarint.getFromBuffer(byteBuffer,UPDATE_ODIN_PREFIX_LENGTH+1);
            int update_set_length = update_set_len_varint.intValue();
            int update_set_start = UPDATE_ODIN_PREFIX_LENGTH+1+update_set_len_varint.size();
            
            logger.info( "\n=============================\n message.size()="+message.size()+",update_set_data_type="+update_set_data_type+",update_set_start="+update_set_start+",update_set_length="+update_set_length+"\n=====================\n");
            
            if( !source.equals("") && message.size()==update_set_start+update_set_length ) {
                byte[] update_set_byte_array=new byte[update_set_length];
                
                for(int off=0;off<update_set_length;off++)
                    update_set_byte_array[off]=byteBuffer.get(update_set_start+off);
                
                try{ 
                  if(update_set_data_type!=Config.DATA_TEXT_UTF8)
                      update_set_byte_array=Util.uncompress(update_set_byte_array,update_set_data_type);
                  
                  update_set=new JSONObject(new String(update_set_byte_array,Config.PPK_TEXT_CHARSET));
                  
                  logger.info( "\n=============================\n update_set="+update_set.toString()+"\n=====================\n");
                  
                  String cmd=update_set.getString("cmd");

                  if( cmd.equals(Config.ODIN_CMD_CONFIRM_UPDATE)){
                      JSONArray confirm_tx_list=update_set.getJSONArray("tx_list");
                      //logger.info( "  confirm_tx_list="+confirm_tx_list.toString()+"\n");
                      
                      for(int kk=0;kk<confirm_tx_list.length();kk++){
                        String confirm_log_id=confirm_tx_list.optString(kk,"");
                        logger.info( "  confirm_log_id="+confirm_log_id+"\n");
                        OdinUpdateInfo waitingUpdateInfo=OdinUpdate.getOdinUpdateInfo(confirm_log_id);

                        if(waitingUpdateInfo!=null){
                            System.out.println("Waiting: updater="+waitingUpdateInfo.updater+",destination="+waitingUpdateInfo.destination+", UpdateSet:"+waitingUpdateInfo.updateSet.toString());
                            if(waitingUpdateInfo.updateSet.optString("cmd","").equals(Config.ODIN_CMD_TRANS_REGISTER)){
                               //transfer register
                               if(source.equals(waitingUpdateInfo.destination)){
                                 //对应转移目标新注册者签收确认
                                 if(source.equals(waitingUpdateInfo.required_confirmer)){
                                   //如果是old register发起转移给old admin(即old admin=required_confirmer=new regiser)，
                                   //则required_confirmer确认将只需一步即完成确认和签收
                                   validity = Config.ODIN_STATUS_VALID;
                                 }else if(waitingUpdateInfo.validity.equals(Config.ODIN_UPDATE_STATUS_RECEIPTING)) {
                                   //否则需要在经过old register/admin的确认后new regiser再签收一次
                                   //确认为receipting状态即现有管理者/注册者确认完成
                                   validity = Config.ODIN_STATUS_VALID;
                                 }
                                 
                                 if(validity.equals(Config.ODIN_STATUS_VALID)){
                                   if(updateOdinRecordByUpdateSet( waitingUpdateInfo.updater,waitingUpdateInfo.destination,oldOdinInfo,waitingUpdateInfo.updateSet)){
                                      validity = Config.ODIN_STATUS_VALID;
                                      db.executeUpdate("UPDATE odin_update_logs SET validity='"+validity+"' WHERE tx_index='"+waitingUpdateInfo.txIndex+"';");
                                   }else{
                                     validity = Config.ODIN_STATUS_INVALID;
                                   }
                                 }
                               }else if(ODIN.checkUpdatable(authSet,source,oldOdinInfo.register,oldOdinInfo.admin)){
                                 validity = Config.ODIN_STATUS_VALID;

                                 //将该ODIN标识的未生效的状态为receipting的历史更新记录置为失效
                                 PreparedStatement ps = db.connection.prepareStatement("UPDATE odin_update_logs SET validity='"+Config.ODIN_STATUS_INVALID+"' WHERE full_odin=? and validity='"+Config.ODIN_UPDATE_STATUS_RECEIPTING+"';");
                                 ps.setString(1, full_odin);
                                 ps.execute();
                                 
                                 //更新发起update_log为receipting 表示现有管理者/注册者确认完成，等待转移目标新注册者签收
                                 db.executeUpdate("UPDATE odin_update_logs SET validity='"+Config.ODIN_UPDATE_STATUS_RECEIPTING+"' WHERE tx_index='"+waitingUpdateInfo.txIndex+"';");
                               }
                            }else if(!source.equals(waitingUpdateInfo.updater)
                              && ODIN.checkUpdatable(authSet,source,oldOdinInfo.register,oldOdinInfo.admin) ){ //confirm another's update operations
                              if(updateOdinRecordByUpdateSet( waitingUpdateInfo.updater,waitingUpdateInfo.destination,oldOdinInfo,waitingUpdateInfo.updateSet)){
                                validity = Config.ODIN_STATUS_VALID;
                                db.executeUpdate("UPDATE odin_update_logs SET validity='"+validity+"' WHERE tx_index='"+waitingUpdateInfo.txIndex+"';");
                              }
                            }
                        }
                      }
                  }else if( ODIN.checkUpdatable(authSet,source,oldOdinInfo.register,oldOdinInfo.admin) ){
                    if(authSet.equals("2") && !oldOdinInfo.register.equals(oldOdinInfo.admin)){ //need update by diffrent register and admin together
                       validity = Config.ODIN_UPDATE_STATUS_AWAITING;
                       required_confirmer = source.equals(oldOdinInfo.register) ?  oldOdinInfo.admin : oldOdinInfo.register;
                    }else if(cmd.equals(Config.ODIN_CMD_TRANS_REGISTER)){
                       //对于transfer register 更新update_log为receipting 表示等待转移目标新注册者签收
                       //updater.equals(oldOdinInfo.register) || updater.equals(oldOdinInfo.admin)
                       validity = Config.ODIN_UPDATE_STATUS_RECEIPTING;
                       
                       //将该ODIN标识的未生效的状态为receipting的历史更新记录置为失效
                       PreparedStatement ps = db.connection.prepareStatement("UPDATE odin_update_logs SET validity='"+Config.ODIN_STATUS_INVALID+"' WHERE full_odin=? and validity='"+Config.ODIN_UPDATE_STATUS_RECEIPTING+"';");
                       ps.setString(1, full_odin);
                       ps.execute();
                    }else{
                       if(updateOdinRecordByUpdateSet( source,destination,oldOdinInfo,update_set))
                          validity = Config.ODIN_STATUS_VALID;
                    }
                  }
                } catch (Exception e) {  
                    update_set=new JSONObject();
                    logger.error(e.toString());
                }  
            }
          }
          
          PreparedStatement ps = db.connection.prepareStatement("insert into odin_update_logs(log_id,tx_index, block_index,full_odin,updater,destination,required_confirmer, update_set,validity) values(?,'"+txIndex.toString()+"','"+blockIndex.toString()+"',?,'"+source+"','"+destination+"','"+required_confirmer+"',?,'"+validity+"');");

          ps.setString(1, new_log_id);
          ps.setString(2, full_odin);
          ps.setString(3, update_set.toString());
          ps.execute();
        }        
      }
    } catch (SQLException e) {  
      logger.error(e.toString());
    }
  }
  
  private static boolean updateOdinRecordByUpdateSet(String updater,String destination,OdinInfo oldOdinInfo,JSONObject update_set ){
    Database db = Database.getInstance();
    
    String full_odin = oldOdinInfo.fullOdin;
    JSONObject  new_odin_set=oldOdinInfo.odinSet;
    
    System.out.println("updateOdinRecordByUpdateSet : update_set="+update_set.toString());
    
    try{
      String cmd=update_set.getString("cmd");
      update_set.remove("cmd"); //移走不再需要的cmd字段，方便与旧设置数据进行合并处理
      if(cmd.equals(Config.ODIN_CMD_TRANS_REGISTER)){ //transfer register
        //转移注册者确认生效时，管理权限将自动重置为默认的0（即注册者和管理者都可以修改），这样方便新注册者可以根据需要自行调整
        new_odin_set.put(Config.ODIN_BASE_SET_AUTH,"0");
        PreparedStatement ps = db.connection.prepareStatement("UPDATE odins SET register='"+destination+"',odin_set=? WHERE full_odin=?;");

        ps.setString(1, new_odin_set.toString());
        ps.setString(2, full_odin);
        ps.execute();
        return true;
      }else{ 
        if(cmd.equals(Config.ODIN_CMD_UPDATE_BASE_INFO)){
          for(Iterator it = update_set.keys(); it!=null && it.hasNext(); ) { 
              // 获得新设置的字段取值进行更新
              String update_key = (String)it.next(); 
              new_odin_set.put(update_key,update_set.get(update_key));
          }
          
        }else if(cmd.equals(Config.ODIN_CMD_UPDATE_AP_SET)){
          if(!update_set.isNull("ap_set")){
            JSONObject new_ap_set=null;
            try{
              new_ap_set=new_odin_set.getJSONObject("ap_set");
            }catch(Exception e){
              new_ap_set=new JSONObject();
            }
            
            JSONObject  update_ap_set = update_set.getJSONObject("ap_set");
            for(Iterator it = update_ap_set.keys(); it!=null && it.hasNext(); ) { 
              String update_ap_id=(String)it.next();
              JSONObject update_ap_record=update_ap_set.getJSONObject(update_ap_id);
              new_ap_set.put(update_ap_id,update_ap_record);
            }

            new_odin_set.put("ap_set",new_ap_set);
          }
        }else if(cmd.equals(Config.ODIN_CMD_UPDATE_VD_SET)){
          if(!update_set.isNull("vd_set")){
            JSONObject  new_vd_set_obj = new JSONObject();
              
            JSONObject  update_vd_set = update_set.getJSONObject("vd_set");
            
            for(Iterator it = update_vd_set.keys(); it!=null && it.hasNext(); ) { 
              // 获得新设置的字段取值进行更新
              String update_key = (String)it.next(); 
              new_vd_set_obj.put(update_key,update_vd_set.get(update_key));
            }
            
            String pubkey=update_vd_set.optString(Config.ODIN_SET_VD_PUBKEY);
            if(pubkey!=null){//pubkey字段有更新
              try{
                if( Util.isURI(pubkey) ){
                  //尝试按URI取值来获取实际的公钥
                  //如果出错则忽略，保存原值
                  String tmp_str=Util.fetchUriContent(pubkey);
                  if(tmp_str!=null && tmp_str.length()>0){
                      new_vd_set_obj.put(Config.ODIN_SET_VD_CERT_URI, pubkey);  //保留字段，记录原始链接，以便测试
                      new_vd_set_obj.put(Config.ODIN_SET_VD_PUBKEY, tmp_str); 
                  }
                }
              }catch(Exception e){
                logger.error("Meet invalid pubkey:"+pubkey);
              }
            }
           
            
            new_odin_set.put("vd_set",new_vd_set_obj);
          }
        }
        
        System.out.println("new_odin_set:"+new_odin_set.toString());
        
        PreparedStatement ps;
        if(destination.length()==0)
          ps = db.connection.prepareStatement("UPDATE odins SET odin_set=?,validity='"+Config.ODIN_STATUS_VALID+"' WHERE full_odin=?;");
        else
          ps = db.connection.prepareStatement("UPDATE odins SET admin='"+destination+"',odin_set=?,validity='"+Config.ODIN_STATUS_VALID+"'  WHERE full_odin=?;");

        ps.setString(1, new_odin_set.toString());
        ps.setString(2, full_odin);
        ps.execute();
      
        return true;
      }
    }catch(Exception e){
      logger.error("OdinUpdate.updateOdinRecordByUpdateSet():"+e.toString());
    }
    return false;
  }

  public static List<OdinUpdateInfo> getPending(String updater) {
    Database db = Database.getInstance();
    ResultSet rs = db.executeQuery("select * from transactions where block_index<0 and source='"+updater+"' and prefix_type=1 order by tx_index desc;");
    List<OdinUpdateInfo> odinUpdateLogs = new ArrayList<OdinUpdateInfo>();
    Blocks blocks = Blocks.getInstance();
    try {
      while (rs.next()) {
        String destination = rs.getString("destination");
        //BigInteger btcAmount = BigInteger.valueOf(rs.getLong("btc_amount"));
        //BigInteger fee = BigInteger.valueOf(rs.getLong("fee"));
        Integer blockIndex = rs.getInt("block_index");
        Integer blockTime = rs.getInt("block_time");
        String txHash = rs.getString("tx_hash");
        Integer txIndex = rs.getInt("tx_index");
        byte[] odin_data = Util.hexStringToBytes(rs.getString("data"));
        

        ResultSet rsCheck = db.executeQuery("select * from odin_update_logs where tx_index='"+txIndex.toString()+"'");
        if (!rsCheck.next()) {
          Byte messageType = blocks.getPPkMessageTypeFromTransaction(odin_data);
          List<Byte> message = blocks.getPPkMessageFromTransaction(odin_data);
          
          logger.info("messageType="+messageType.toString()+"  message.size="+message.size());

          if (messageType==OdinUpdate.id && message.size()>UPDATE_ODIN_PREFIX_LENGTH) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(message.size());
            for (byte b : message) {
              byteBuffer.put(b);
            }      
                        
            String full_odin=getFullOdinFromUpdateMessage(byteBuffer);

            Byte update_set_data_type=byteBuffer.get(UPDATE_ODIN_PREFIX_LENGTH); 
            BitcoinVarint update_set_len_varint=BitcoinVarint.getFromBuffer(byteBuffer,UPDATE_ODIN_PREFIX_LENGTH+1);
            int update_set_length = update_set_len_varint.intValue();
            int update_set_start = UPDATE_ODIN_PREFIX_LENGTH+1+update_set_len_varint.size();
            
            if( !updater.equals("") && message.size()==update_set_start+update_set_length )
            {
                byte[] update_set_byte_array=new byte[update_set_length];
                for(int off=0;off<update_set_length;off++)
                    update_set_byte_array[off]=byteBuffer.get(update_set_start+off);
            
                JSONObject update_set;
                    
                try{
                    if(update_set_data_type!=Config.DATA_TEXT_UTF8)
                      update_set_byte_array=Util.uncompress(update_set_byte_array,update_set_data_type);
                  
                    update_set=new JSONObject(new String(update_set_byte_array,Config.PPK_TEXT_CHARSET));
                    
                } catch (Exception e) {  
                    logger.error(e.toString());
                    return odinUpdateLogs;
                }  
                OdinUpdateInfo updateOdinInfo;
                
                if(Config.ODIN_CMD_CONFIRM_UPDATE.equals(update_set.optString("cmd"))){  ////Confirm update request
                 try{
                      String confirm_tx_hash=update_set.getString("confirm_tx_hash");
                      updateOdinInfo=getOdinUpdateInfo(confirm_tx_hash);
                      
                      if(updateOdinInfo!=null){
                          updateOdinInfo.confirm_tx_hash=confirm_tx_hash;
                          odinUpdateLogs.add(updateOdinInfo);
                      }
                  }catch(Exception e){
                  }
                }else{ //Normal update request
                  updateOdinInfo = new OdinUpdateInfo();
                  updateOdinInfo.updater = updater;
                  updateOdinInfo.destination = destination;
                  updateOdinInfo.fullOdin=full_odin;
                  updateOdinInfo.shortOdin=ODIN.getShortOdin(full_odin);
                  updateOdinInfo.updateSet = update_set;
                  updateOdinInfo.txIndex = txIndex;
                  updateOdinInfo.txHash = txHash;
                  updateOdinInfo.blockIndex = blockIndex;
                  updateOdinInfo.blockTime = blockTime;
                  updateOdinInfo.validity=Config.ODIN_STATUS_PENDING;
                  
                  /*
                  try{
                    if(Config.ODIN_CMD_TRANS_REGISTER.equals(update_set.optString("cmd"))){
                        updateOdinInfo.updateSet.put(Config.ODIN_BASE_SET_REGISTER,destination);
                    }else if(!destination.equals("")){
                        updateOdinInfo.updateSet.put("admin",destination);
                    }
                  }catch(Exception e){                              
                  }
                  System.out.println("BBBB odinUpdateLogs="+odinUpdateLogs.toString());
                  */
                  odinUpdateLogs.add(updateOdinInfo);
                }
                
            }
          }  
        }
      }
    } catch (SQLException e) {  
      logger.error(e.toString());
    }  
    return odinUpdateLogs;
  }
  
  
  public static String getFullOdinFromUpdateMessage(ByteBuffer byteBufferMessage){
      byte[] full_odin_byte_array=new byte[UPDATE_ODIN_PREFIX_LENGTH];
      for(int off=0;off<UPDATE_ODIN_PREFIX_LENGTH;off++)
          full_odin_byte_array[off]=byteBufferMessage.get(off);
      String full_odin=new String(full_odin_byte_array);
      full_odin=full_odin.trim();
      
      
      if(!isValidFullOdin(full_odin)){
        logger.error("Found invalid ODIN:"+full_odin);
        return null;
      }
      return full_odin;
  }
  
  //对full_odin的取值进行检查，防sql注入等攻击
  public static boolean isValidFullOdin(String full_odin){
    try{
      int split_index=full_odin.indexOf(".");
      
      if(split_index<=0){
        return false;
      } 
      
      Long block_index=Long.valueOf(full_odin.substring(0,split_index));
      Long sn_in_block=Long.valueOf(full_odin.substring(split_index+1));
      
      String tmp_odin=block_index.toString()+"."+sn_in_block.toString();
      if(!tmp_odin.equals(full_odin)){
        return false;
      }
      
    }catch(Exception e){
      return false;
    }
    
    return true;
  }
    
  public static OdinUpdateInfo getOdinUpdateInfo(String update_tx_index_or_hash_or_logid) { 
    Database db = Database.getInstance();
    
    String str_sql="select l.log_id,l.tx_index, l.block_index,l.updater,l.destination,l.required_confirmer, l.update_set,l.validity,cp.full_odin,cp.short_odin,cp.register,cp.admin,transactions.block_time,transactions.tx_hash from odins cp,odin_update_logs l,transactions where l.full_odin=cp.full_odin and  l.tx_index=transactions.tx_index and ";

    //判断参数取值类型
    if(update_tx_index_or_hash_or_logid.indexOf(".")>0){
        //是类似xxxxx.xx的log_id
        str_sql += " l.log_id='"+update_tx_index_or_hash_or_logid+"'";
    }else if(update_tx_index_or_hash_or_logid.length()<64){
        //是有效取值数字范围内的tx_index
        str_sql += " l.tx_index='"+update_tx_index_or_hash_or_logid+"'";
    }else{
        //是tx_hash
        str_sql += " transactions.tx_hash='"+update_tx_index_or_hash_or_logid+"'";
    }
    
    ResultSet rs = db.executeQuery(str_sql);

    try {
      
      if(rs.next()) {
        OdinUpdateInfo updateOdinInfo = new OdinUpdateInfo();
        updateOdinInfo.fullOdin = rs.getString("full_odin");
        updateOdinInfo.shortOdin = rs.getInt("short_odin");
        updateOdinInfo.updater = rs.getString("updater");
        updateOdinInfo.destination = rs.getString("destination");
        updateOdinInfo.required_confirmer = rs.getString("required_confirmer");
        updateOdinInfo.txIndex = rs.getInt("tx_index");
        updateOdinInfo.logId = rs.getString("log_id");
        updateOdinInfo.txHash = rs.getString("tx_hash");
        updateOdinInfo.blockIndex = rs.getInt("block_index");
        updateOdinInfo.blockTime = rs.getInt("block_time");
        updateOdinInfo.validity = rs.getString("validity");
        
        try{
          updateOdinInfo.updateSet = new JSONObject(rs.getString("update_set"));
        }catch (Exception e) {
          logger.error(e.toString());
        }

        return updateOdinInfo;
      }
    } catch (SQLException e) {
      logger.error("OdinUpdate.getOdinUpdateInfo(): "+e.toString());
    }  

    return null;    
  }
  
  /*
  public static boolean checkTransferRegisterConfimedByRegisterAndAdmin(String full_odin,String update_tx_index_or_logid) { 
    Database db = Database.getInstance();
    
    ResultSet rs = db.executeQuery("select l.log_id,l.tx_index, l.block_index,l.updater,l.destination, l.update_set,l.validity,cp.full_odin,cp.short_odin,cp.register,cp.admin,transactions.block_time,transactions.tx_hash from odins cp,odin_update_logs l,transactions where (l.tx_index='"+update_tx_index_or_logid+"' and l.full_odin=cp.full_odin and  l.tx_index=transactions.tx_index) or (l.log_id='"+update_tx_index_or_logid+"' and l.full_odin=cp.full_odin and  l.tx_index=transactions.tx_index) ;");

    try {
      
      if(rs.next()) {
        OdinUpdateInfo updateOdinInfo = new OdinUpdateInfo();
        updateOdinInfo.fullOdin = rs.getString("full_odin");
        updateOdinInfo.shortOdin = rs.getInt("short_odin");
        updateOdinInfo.updater = rs.getString("updater");
        updateOdinInfo.destination = rs.getString("destination");
        updateOdinInfo.txIndex = rs.getInt("tx_index");
        updateOdinInfo.logId = rs.getString("log_id");
        updateOdinInfo.txHash = rs.getString("tx_hash");
        updateOdinInfo.blockIndex = rs.getInt("block_index");
        updateOdinInfo.blockTime = rs.getInt("block_time");
        updateOdinInfo.validity = rs.getString("validity");
        
        try{
          updateOdinInfo.updateSet = new JSONObject(rs.getString("update_set"));
        }catch (Exception e) {
          logger.error(e.toString());
        }

        return updateOdinInfo;
      }
    } catch (SQLException e) {
      logger.error("OdinUpdate.getOdinUpdateInfo(): "+e.toString());
    }  
  }
  */
  public static OdinTransctionData updateOdinBaseInfo(String fullOdin,String updater,String admin,JSONObject update_set) throws Exception {
      return updateOdin(fullOdin,updater,null,admin,update_set);
  }
  
  public static OdinTransctionData updateOdinApSet(String fullOdin,String updater,JSONObject update_ap_set) throws Exception {
      Map mapNewOdinSet = new HashMap(); 
      mapNewOdinSet.put("ver", Config.ODIN_PROTOCOL_VER); 
      mapNewOdinSet.put("cmd", Config.ODIN_CMD_UPDATE_AP_SET); 

      mapNewOdinSet.put("ap_set", update_ap_set); 

      JSONObject update_set = new JSONObject(mapNewOdinSet); 

      return updateOdin(fullOdin,updater,null,"",update_set);
  }
  
  public static OdinTransctionData updateOdinVdSet(String fullOdin,String updater,JSONObject update_vd_set) throws Exception {
      Map mapNewOdinSet = new HashMap(); 
      mapNewOdinSet.put("ver", Config.ODIN_PROTOCOL_VER); 
      mapNewOdinSet.put("cmd", Config.ODIN_CMD_UPDATE_VD_SET); 

      mapNewOdinSet.put("vd_set", update_vd_set); 

      JSONObject update_set = new JSONObject(mapNewOdinSet); 

      return updateOdin(fullOdin,updater,null,"",update_set);
  }
  
  public static OdinTransctionData confirmUpdate(String fullOdin,String updater,JSONArray confirm_update_list) throws Exception {
      Map mapNewOdinSet = new HashMap(); 
      mapNewOdinSet.put("ver", Config.ODIN_PROTOCOL_VER); 
      mapNewOdinSet.put("cmd", Config.ODIN_CMD_CONFIRM_UPDATE); 

      mapNewOdinSet.put("tx_list", confirm_update_list); 

      JSONObject update_set = new JSONObject(mapNewOdinSet); 

      return updateOdin(fullOdin,updater,null,"",update_set);
  }

  public static OdinTransctionData transOdinRegister(String fullOdin,String updater,String new_register) throws Exception {
      if (updater.equals(new_register)) throw new Exception("Please specify another register address which is not same to the updater.");
      
      Map mapNewOdinSet = new HashMap(); 
      mapNewOdinSet.put("ver", Config.ODIN_PROTOCOL_VER); 
      mapNewOdinSet.put("cmd", Config.ODIN_CMD_TRANS_REGISTER); 
  
      JSONObject update_set = new JSONObject(mapNewOdinSet); 
      
      return updateOdin(fullOdin,updater,new_register,null,update_set);
  }
    
  private static OdinTransctionData updateOdin(String fullOdin,String updater,String new_register,String new_admin,JSONObject update_set) throws Exception {
    if (updater.equals("")) throw new Exception("Please specify a updater address.");
        
    if(fullOdin.length()<UPDATE_ODIN_PREFIX_LENGTH){
        while(fullOdin.length()<UPDATE_ODIN_PREFIX_LENGTH){
            fullOdin=fullOdin+" "; //Append BLANK char
        }
    }
    byte[] full_odin_byte_array=fullOdin.toString().getBytes(Config.BINARY_DATA_CHARSET);
    logger.info("Test:updateOdin fullOdin="+fullOdin+", full_odin_byte_array.length="+full_odin_byte_array.length);

    String  destination;
    ByteBuffer byteBuffer;
    
    if (update_set==null) throw new Exception("Please specify valid update_set.");
    logger.info("updateOdin update_set="+update_set.toString());

    Byte update_set_data_type=Config.DATA_TEXT_UTF8; 
    byte[] update_set_byte_array=update_set.toString().getBytes(Config.PPK_TEXT_CHARSET);
    int update_set_length=update_set_byte_array.length;
    byte[] len_bytes=BitcoinVarint.toBytes(update_set_length);
    int total_data_len=UPDATE_ODIN_PREFIX_LENGTH+1+1+len_bytes.length+update_set_length;

    logger.info("update_set_byte_array.length="+update_set_byte_array.length);
    if (total_data_len>Config.MAX_ODIN_DATA_LENGTH){ //当数据长度超出限制时尝试压缩
        byte[] update_set_byte_array_compressed=Util.compress(update_set.toString().getBytes(Config.PPK_TEXT_CHARSET),Config.DATA_BIN_DEFLATE);
        logger.info("update_set_byte_array_compressed.length="+update_set_byte_array_compressed.length);
        
        update_set_byte_array=update_set_byte_array_compressed;
        update_set_data_type=Config.DATA_BIN_DEFLATE;
    }

    update_set_length=update_set_byte_array.length;
    len_bytes=BitcoinVarint.toBytes(update_set_length);
    total_data_len=UPDATE_ODIN_PREFIX_LENGTH+1+1+len_bytes.length+update_set_length;

    logger.info("total_data_len="+total_data_len);
    logger.info("MAX_ODIN_DATA_LENGTH="+Config.MAX_ODIN_DATA_LENGTH);
    if (total_data_len>Config.MAX_ODIN_DATA_LENGTH) 
        throw new Exception("Too big setting data size:"+total_data_len+" .(Should be less than "+Config.MAX_ODIN_DATA_LENGTH+" bytes)");

    byteBuffer = ByteBuffer.allocate(total_data_len);
    byteBuffer.put(id);
    byteBuffer.put(full_odin_byte_array,0,UPDATE_ODIN_PREFIX_LENGTH);
    byteBuffer.put(update_set_data_type);
    byteBuffer.put(len_bytes,0,len_bytes.length);
    byteBuffer.put(update_set_byte_array,0,update_set_length);
        
    if(new_register!=null)
      destination = new_register;
    else
      destination = new_admin ; 

    byte[] data = byteBuffer.array();

    OdinTransctionData tx = new OdinTransctionData(
          updater, 
          destination, 
          BigInteger.valueOf(Config.dustSize), 
          BigInteger.valueOf(Config.ppkStandardDataFee),
          Config.PPK_ODIN_MARK_PUBKEY_HEX ,
          Util.bytesToHexString(data)
       );
       
    /*
    //just for debug
    Blocks blocks=Blocks.getInstance();
    logger.info("Test:updateOdin tx="+tx+",updater="+updater+", data.length="+data.length+" dataString="+(new String(data)));
    blocks.importPPkTransaction(blocks.transaction(tx), null, null, null);
    System.exit(0);
    */
    return tx;
  }
// "+Language.getLangLabel("LANG_")+"
  public static HashMap<String,Object> parseOdinUpdateSet(
        HashMap<String,Object> map,
        String updater,
        String destination,
        JSONObject update_set
      )throws Exception
  {
    String update_desc="<ul>";
    String cmd=update_set.optString("cmd");

    if(Config.ODIN_CMD_TRANS_REGISTER.equals(cmd)){
      update_desc+="<li>"+Language.getLangLabel("Transfer register to")+" "+HtmlRegexpUtil.filterHtml(destination)+"</li>";
    }else if(Config.ODIN_CMD_UPDATE_AP_SET.equals(update_set.optString("cmd"))){
      update_desc+="<li>"+Language.getLangLabel("Update Access Points")+"</li>\n<ul>\n";
      JSONObject  update_ap_set = update_set.optJSONObject("ap_set");
      if(update_ap_set!=null){
        for(Iterator it = update_ap_set.keys(); it!=null && it.hasNext(); ) { 
          String update_ap_id=(String)it.next();
          JSONObject update_ap_record=update_ap_set.getJSONObject(update_ap_id);
          update_desc+="<li>"+HtmlRegexpUtil.filterHtml("["+update_ap_id+"] "+update_ap_record.optString("url",""))+"</li>\n";
        }
      }
      update_desc+="</ul>";
    }else if(Config.ODIN_CMD_UPDATE_VD_SET.equals(cmd)){
      update_desc+="<li>"+Language.getLangLabel("Update Validtion")+"</li>\n<ul>\n";
      JSONObject  update_vd_set = update_set.optJSONObject("vd_set");
      if(update_vd_set!=null){
        update_desc+="<li>"+Language.getLangLabel("Encode type")+": "+HtmlRegexpUtil.filterHtml(update_vd_set.optString(Config.ODIN_SET_VD_TYPE,""))+"</li>\n";
        update_desc+="<li>"+Language.getLangLabel("Pubkey URI")+": "+HtmlRegexpUtil.filterHtml(update_vd_set.optString(Config.ODIN_SET_VD_PUBKEY,""))+"</li>\n";
      }
      update_desc+="</ul>";
    }else if(Config.ODIN_CMD_CONFIRM_UPDATE.equals(cmd)){
       update_desc+="<li>"+Language.getLangLabel("Confirm below updates")+"<br>"+Language.getLangLabel("TX_LIST")+":"+HtmlRegexpUtil.filterHtml(update_set.toString())+"</li>\n";
    }else if(Config.ODIN_CMD_UPDATE_BASE_INFO.equals(cmd)){
      if(destination!=null && destination.length()>0)
          update_desc+="<li>"+Language.getLangLabel("Update admin to")+" "+HtmlRegexpUtil.filterHtml(destination)+"</li>\n";
      
      if(!update_set.isNull("title"))
          update_desc+="<li>"+Language.getLangLabel("Title")+":"+HtmlRegexpUtil.filterHtml(update_set.getString("title"))+"</li>\n";
      
      if(!update_set.isNull("email"))
          update_desc+="<li>"+Language.getLangLabel("Email")+":"+HtmlRegexpUtil.filterHtml(update_set.getString("email"))+"</li>\n";
      
      if(!update_set.isNull(Config.ODIN_BASE_SET_PNS_URL))
          update_desc+="<li>"+Language.getLangLabel("Peer Naming Service")+":"+HtmlRegexpUtil.filterHtml(update_set.getString(Config.ODIN_BASE_SET_PNS_URL))+"</li>\n";
      
      if(!update_set.isNull(Config.ODIN_BASE_SET_AUTH))
          update_desc+="<li>"+Language.getLangLabel("Authority")+":"
                        +HtmlRegexpUtil.filterHtml( Util.getOdinAuthRightLabel("" + update_set.optInt(Config.ODIN_BASE_SET_AUTH,0) ) )
                        +"</li>\n";
    }else{
      update_desc+="<li>"+HtmlRegexpUtil.filterHtml(update_set.toString())+"</li>\n";
    }
    update_desc+="</ul>";
    map.put("update_desc", update_desc);

    return map;
  }

}

class OdinUpdateInfo {
  public String  fullOdin;
  public Integer shortOdin;
  public Integer txIndex;
  public String  updater;
  public String  destination;
  public String  required_confirmer;
  public String  logId;
  public String  txHash;
  public Integer blockIndex;
  public Integer blockTime;
  public String  validity;

  public String  confirm_tx_hash;

  public JSONObject updateSet;
}

