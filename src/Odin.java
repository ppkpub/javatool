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
import java.util.HashMap;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bitcoinj.core.Transaction;

import org.json.JSONArray;
import org.json.JSONObject;

public class Odin {
  static Logger logger = LoggerFactory.getLogger(Odin.class);
  public static Byte id = Config.FUNC_ID_ODIN_REGIST; //for registing new ODIN 
  
  //public static HashMap<String , String> teamMap = null;
  
  public static void init(){
    createTables(null);    
  }
  
  public static void createTables(Database db){
    if(db==null) 
      db = Database.getInstance();
    try {
      db.executeUpdate("CREATE TABLE IF NOT EXISTS odins (tx_index INTEGER PRIMARY KEY, tx_hash TEXT UNIQUE,block_index INTEGER,full_odin TEXT UNIQUE,short_odin INTEGER UNIQUE , register TEXT, admin TEXT,odin_set TEXT, validity TEXT)");
      db.executeUpdate("CREATE INDEX IF NOT EXISTS block_index_idx ON odins (block_index)");
      db.executeUpdate("CREATE INDEX IF NOT EXISTS tx_index ON odins (tx_index)");
      db.executeUpdate("CREATE INDEX IF NOT EXISTS full_odin ON odins (full_odin)");
      db.executeUpdate("CREATE INDEX IF NOT EXISTS short_odin ON odins (short_odin)");

      db.executeUpdate("CREATE TABLE IF NOT EXISTS odin_update_logs (log_id TEXT, tx_index INTEGER PRIMARY KEY,block_index INTEGER,full_odin TEXT, updater TEXT,destination TEXT,update_set TEXT, validity TEXT,required_confirmer TEXT);");
      db.executeUpdate("CREATE INDEX IF NOT EXISTS logid_idx ON odin_update_logs (log_id);");
      db.executeUpdate("CREATE INDEX IF NOT EXISTS odin_idx ON odin_update_logs (full_odin);");
      
      //db.executeUpdate("CREATE TABLE IF NOT EXISTS ppk_uri_caches (ppk_uri TEXT PRIMARY KEY, resp_json TEXT, validity TEXT)");
      //db.executeUpdate("CREATE INDEX IF NOT EXISTS ppk_uri_idx ON ppk_uri_caches (ppk_uri)");

      /*
      //test chinese encode
      String oldStr="EncodeTest编码测试";
      String newStr=Util.uncompress(Util.compress(oldStr));
      db.executeUpdate("CREATE TABLE IF NOT EXISTS encodetest (old_str TEXT,new_str TEXT);");

      PreparedStatement ps = db.connection.prepareStatement("insert into encodetest(old_str, new_str) values(?,?);");

      ps.setString(1, oldStr);
      ps.setString(2, newStr);

      ps.execute();
      //test end
      */
    } catch (Exception e) {
      logger.error(e.toString());
    }
  }
  
  public static void parse(Integer txIndex, List<Byte> message) {
    logger.info( "\n=============================\n Parsing ODIN txIndex="+txIndex.toString()+"\n=====================\n");
    
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
        Integer txSnInBlock=rs.getInt("sn_in_block");
        String full_odin=blockIndex+"."+txSnInBlock;
        Integer short_odin=getLastShortOdin()+1;

        ResultSet rsCheck = db.executeQuery("select * from odins where tx_index='"+txIndex.toString()+"'");
        if (rsCheck.next()) return;

        String validity = "invalid";
        JSONObject odin_set=new JSONObject( );
        String odin_set_admin= destination.length()==0 ? source:destination;
                
        if (message.size() >2) {
          ByteBuffer byteBuffer = ByteBuffer.allocate(message.size());
          for (byte b : message) {
            byteBuffer.put(b);
          }      

          Byte odin_set_data_type=byteBuffer.get(0); 
          BitcoinVarint odin_set_len_varint=BitcoinVarint.getFromBuffer(byteBuffer,1);
          int odin_set_length = odin_set_len_varint.intValue();
          int odin_set_start = 1+odin_set_len_varint.size();
          logger.info( "\n=============================\n message.size()="+message.size()+",odin_set_start="+odin_set_start+",odin_set_length="+odin_set_length+"\n=====================\n");
          
          if( !source.equals("") && message.size()==odin_set_start+odin_set_length )
          {
            validity = "valid";
            
            byte[] odin_set_byte_array=new byte[odin_set_length];
            
            for(int off=0;off<odin_set_length;off++)
              odin_set_byte_array[off]=byteBuffer.get(odin_set_start+off);
            
            
            try{ 
              if(odin_set_data_type==Config.DATA_BIN_GZIP)
                  odin_set=new JSONObject(Util.uncompress(new String(odin_set_byte_array,Config.BINARY_DATA_CHARSET)));
              else
                  odin_set=new JSONObject(new String(odin_set_byte_array,Config.PPK_TEXT_CHARSET));
                            
              logger.info( "\n=============================\n odin_set="+odin_set.toString()+"\n=====================\n");
            } catch (Exception e) {  
              odin_set=new JSONObject();
              logger.error(e.toString());
            }  

            
          }
        }    

        PreparedStatement ps = db.connection.prepareStatement("insert into odins(tx_index, tx_hash, block_index, full_odin,short_odin,admin, register,odin_set,validity) values('"+txIndex.toString()+"','"+txHash+"','"+blockIndex.toString()+"','"+full_odin+"','"+short_odin.toString()+"',?,'"+source+"',?,'"+validity+"');");

        ps.setString(1, odin_set_admin);
        ps.setString(2, odin_set.toString());
        ps.execute();
      }
    } catch (SQLException e) {  
      logger.error(e.toString());
    }
  }

  public static List<OdinInfo> getPending(String register) {
    Database db = Database.getInstance();
    ResultSet rs = db.executeQuery("select * from transactions where block_index<0 and source='"+register+"' and prefix_type=1 order by tx_index desc;");
    List<OdinInfo> odins = new ArrayList<OdinInfo>();
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
        String dataString = rs.getString("data");

        ResultSet rsCheck = db.executeQuery("select * from odins where tx_index='"+txIndex.toString()+"'");
        if (!rsCheck.next()) {
          Byte messageType = blocks.getPPkMessageTypeFromTransaction(dataString);
          List<Byte> message = blocks.getPPkMessageFromTransaction(dataString);
          
          logger.info("messageType="+messageType.toString()+"  message.size="+message.size());

          if (messageType==Odin.id && message.size()>2) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(message.size());
            for (byte b : message) {
              byteBuffer.put(b);
            }      
            Byte odin_set_data_type=byteBuffer.get(0); 
            BitcoinVarint odin_set_len_varint=BitcoinVarint.getFromBuffer(byteBuffer,1);
            int odin_set_length = odin_set_len_varint.intValue();
            int odin_set_start = 1+odin_set_len_varint.size();
          
            if( !register.equals("") && message.size()==odin_set_start+odin_set_length )
            {
                byte[] odin_set_byte_array=new byte[odin_set_length];
                for(int off=0;off<odin_set_length;off++)
                  odin_set_byte_array[off]=byteBuffer.get(odin_set_start+off);
              
                JSONObject odin_set;
                String odin_set_admin = destination; //HtmlRegexpUtil.filterHtml( destination.length()==0 ? register : destination );
                                    
                try{
                  if(odin_set_data_type==Config.DATA_BIN_GZIP)
                    odin_set=new JSONObject(Util.uncompress(new String(odin_set_byte_array,Config.BINARY_DATA_CHARSET)));
                  else
                    odin_set=new JSONObject(new String(odin_set_byte_array,Config.PPK_TEXT_CHARSET));
                  
                } catch (Exception e) {  
                  logger.error(e.toString());
                  return odins;
                }  
                OdinInfo odinInfo = new OdinInfo();

                odinInfo.register = register;
                
                odinInfo.fullOdin="";
                odinInfo.shortOdin=-1;
                odinInfo.txSnInBlock=-1;
                odinInfo.admin=odin_set_admin;
                odinInfo.odinSet = odin_set;
                odinInfo.txIndex = txIndex;
                odinInfo.txHash = txHash;
                odinInfo.blockIndex = blockIndex;
                odinInfo.blockTime = blockTime;
                odinInfo.validity="pending";
                odins.add(odinInfo);
            }
          }  
        }
      }
    } catch (SQLException e) {  
      logger.error(e.toString());
    }  
    return odins;
  }
  
  public static OdinInfo getOdinInfo(String odin) {
    Database db = Database.getInstance();
    
    ResultSet rs = db.executeQuery("select cp.full_odin,cp.short_odin,cp.register,cp.admin ,cp.tx_hash ,cp.tx_index ,cp.block_index,transactions.block_time,cp.odin_set, cp.validity from odins cp,transactions where (cp.full_odin='"+odin+"' or cp.short_odin='"+odin+"') and cp.tx_index=transactions.tx_index;");

    try {
      
      if(rs.next()) {
        OdinInfo odinInfo = new OdinInfo();
        odinInfo.fullOdin = rs.getString("full_odin");
        odinInfo.shortOdin = rs.getInt("short_odin");
        odinInfo.register = rs.getString("register");
        odinInfo.admin = rs.getString("admin");
        odinInfo.txIndex = rs.getInt("tx_index");
        odinInfo.txHash = rs.getString("tx_hash");
        odinInfo.blockIndex = rs.getInt("block_index");
        odinInfo.blockTime = rs.getInt("block_time");
        odinInfo.validity = rs.getString("validity");
        
        try{
          odinInfo.odinSet = new JSONObject(rs.getString("odin_set"));
        }catch (Exception e) {
          odinInfo.odinSet = new JSONObject();
          logger.error(e.toString());
        }

        return odinInfo;
      }
    } catch (SQLException e) {
    }  

    return null;    
  }
    
    public static Integer getShortOdin(String full_odin) {
    Database db = Database.getInstance();
    
    ResultSet rs = db.executeQuery("select full_odin,short_odin from odins  where full_odin='"+full_odin+"';");

    try {
      if(rs.next()) {
        return rs.getInt("short_odin");
      }
    } catch (SQLException e) {
    }  

    return -1;    
  }
    
  public static Transaction createOdin(String register,String admin,JSONObject odin_set) throws Exception {
    if (register.equals("")) throw new Exception("Please specify a register address.");
    if (odin_set==null) throw new Exception("Please specify valid odin_set.");
    
    logger.info("createOdin odin_set="+odin_set.toString());
    
    Byte odin_set_data_type=Config.DATA_TEXT_UTF8; 
    byte[] odin_set_byte_array=odin_set.toString().getBytes(Config.PPK_TEXT_CHARSET);
    byte[] odin_set_byte_array_gzip=Util.compress(odin_set.toString()).getBytes(Config.BINARY_DATA_CHARSET);
    
    if(odin_set_byte_array.length>odin_set_byte_array_gzip.length){ //need compress the long data
       odin_set_byte_array=odin_set_byte_array_gzip;
       odin_set_data_type=Config.DATA_BIN_GZIP;
    }    

    int odin_set_length=odin_set_byte_array.length;
    byte[] len_bytes=BitcoinVarint.toBytes(odin_set_length);
    int total_data_len=1+1+len_bytes.length+odin_set_length;
    
    if (total_data_len>Config.MAX_ODIN_DATA_LENGTH) throw new Exception("Too big setting data.(Should be less than "+Config.MAX_ODIN_DATA_LENGTH+" bytes)");

    Blocks blocks = Blocks.getInstance();
    ByteBuffer byteBuffer = ByteBuffer.allocate(total_data_len);
    byteBuffer.put(id);
    byteBuffer.put(odin_set_data_type);
    byteBuffer.put(len_bytes);
    byteBuffer.put(odin_set_byte_array,0,odin_set_length);
    
    byte[] data = byteBuffer.array();

    String dataString = "";
    try {
      dataString = new String(data,Config.BINARY_DATA_CHARSET);
    } catch (UnsupportedEncodingException e) {
    }
    Transaction tx = blocks.transaction(
          register, 
          admin, 
          BigInteger.valueOf(Config.dustSize), 
          BigInteger.valueOf(Config.ppkStandardDataFee),
          Config.PPK_ODIN_MARK_PUBKEY_HEX ,
          dataString
       );

    /*
    //just for debug
    logger.info("Test:createOdin register="+register+", MAKR_PUBKEY_HEX="+Config.PPK_ODIN_MARK_PUBKEY_HEX+", dataString.length="+dataString.length()+" dataString="+dataString);
    blocks.importPPkTransaction(tx, null, null,null);
    System.exit(0);
    */
    
    return tx;
  }

  public static HashMap<String,Object> parseOdinSet(HashMap<String,Object> map,JSONObject odin_set,String myaddress,String register,String admin) throws Exception {
    String authSet="";
    try{
        authSet=odin_set.getString("auth");
    }catch(Exception e){
    }
    
    if( checkUpdatable(authSet,myaddress,register,admin)){
        map.put("me_updatable", true);
    }
    
    //if( myaddress.equals(register) ){
    //    map.put("me_transable",true);
    //}

    if(odin_set.isNull("title"))
      map.put("title", "");
    else
      map.put("title", HtmlRegexpUtil.filterHtml(odin_set.getString("title")));
    
    if(odin_set.isNull("email"))
      map.put("email", "");
    else
      map.put("email", HtmlRegexpUtil.filterHtml(odin_set.getString("email")));

    map.put("auth", authSet);
    
    String ap_set_debug="<ul>";
    if(!odin_set.isNull("ap_set")){
        JSONObject  ap_set = odin_set.getJSONObject("ap_set");
        
        for(Iterator it = ap_set.keys(); it!=null && it.hasNext(); ) { 
          String ap_id=(String)it.next();
          JSONObject ap_record=ap_set.getJSONObject(ap_id);
          String ap_url=ap_record.optString("url","");
          map.put("ap" + ap_id + "_url", ap_url);
          ap_set_debug+="<li>"+HtmlRegexpUtil.filterHtml(ap_url)+"</li>\n";
        }
    }
    ap_set_debug+="</ul>";
    map.put("ap_set_debug", ap_set_debug);
    
    String vd_set_debug="";
    if(!odin_set.isNull("vd_set")){
        vd_set_debug += HtmlRegexpUtil.filterHtml(odin_set.optString("vd_set",""));
        
        try{
          JSONObject  vd_set = odin_set.getJSONObject("vd_set");
          map.put("vd_set_algo", vd_set.optString(Config.JSON_KEY_PPK_ALGO,""));
          map.put("vd_set_cert_uri", vd_set.optString(Config.JSON_KEY_PPK_CERT_URI,""));
          map.put("vd_set_pubkey", vd_set.optString(Config.JSON_KEY_PPK_PUBKEY,""));
        }catch(Exception e){
          
        }
    }
    map.put("vd_set_debug", vd_set_debug);
    

    return map;
  }
    
  public static boolean checkUpdatable(String authSet,String updater,String register,String admin  ) {
      if(
          ( ( authSet==null || authSet.length()==0 || authSet.equals("1") ) && updater.equals(admin) )
        ||( ( authSet.equals("0")||authSet.equals("2") ) && ( updater.equals(register) || updater.equals(admin) ) )
      ){
          return true;
      } else {
          return false;
      }
  }

    
  public static Integer getLastShortOdin() {
    Database db = Database.getInstance();
    ResultSet rs = db.executeQuery("SELECT short_odin from odins order by short_odin DESC LIMIT 1;");
    try {
      while(rs.next()) {
        return rs.getInt("short_odin");
      }
    } catch (SQLException e) {
    }  
    return -1;
  }  
}

class OdinInfo {
  public String fullOdin;
  public Integer shortOdin;
  public Integer txIndex;
  public String register;
  public String admin;
  public String txHash;
  public Integer blockIndex;
  public Integer blockTime;
  public Integer txSnInBlock;
  public String  validity;

  public JSONObject odinSet;
}