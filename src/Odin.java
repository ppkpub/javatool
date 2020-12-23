import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bitcoinj.core.Transaction;

import org.json.JSONObject;

public class ODIN {
  static Logger logger = LoggerFactory.getLogger(ODIN.class);
  public static Byte id = Config.FUNC_ID_ODIN_REGIST; //for registing new ODIN 
  static String[] LetterEscapeNumSet={"O","AIL","BCZ","DEF","GH","JKS","MN","PQR","TUV","WXY"};
  
  //格式化输入URI参数，使之符合ODIN标识定义规范，无效返回null
  //参数prior_add_resource_mark取值true时 优先追加资源标识符（主要用于ID使用时）， 否则根据常用网址规则自动判断添加缺少的"/"字符和资源标志
  //public static String formatPPkURI(String ppk_uri){
  //	  return formatPPkURI(ppk_uri,true);
  //}
  public static String formatPPkURI(String ppk_uri,boolean prior_add_resource_mark_for_id){
    if(ppk_uri==null)
        return null;
    
    if( ppk_uri.indexOf("//") >0 ){
        //存在连续的/字符
        return null;
    }
    
    //替换可能的中文易输错字符
    ppk_uri=ppk_uri.replaceFirst("：", ":");
    
    int scheme_posn=ppk_uri.indexOf(":");
    String main_part=null;
    
    if( scheme_posn<0){ //输入地址没有任何类型前缀时
        main_part = ppk_uri.trim();
    }else{//检查前缀是否以ppk:起始
        String prefix = ppk_uri.substring(0,scheme_posn+1);
        
        if( !prefix.equalsIgnoreCase(Config.PPK_URI_PREFIX) )
            return null;
        
        main_part = ppk_uri.substring(scheme_posn+1).trim();
    }
    
    if(main_part.length()==0){ //没有指定实际网址时，使用默认主页
        ppk_uri = Config.ppkDefaultHomepage;
    }else{
        ppk_uri = Config.PPK_URI_PREFIX+main_part;
    }
    
    int old_resoure_mark_posn=ppk_uri.lastIndexOf("#");
    if(old_resoure_mark_posn==ppk_uri.length()-1) {//自动替换旧版URI中的后缀标志符#
    	ppk_uri = ppk_uri.substring(0, old_resoure_mark_posn)+Config.PPK_URI_RESOURCE_MARK;;
    }
    
    int resoure_mark_posn=ppk_uri.lastIndexOf(Config.PPK_URI_RESOURCE_MARK);
    
    if( resoure_mark_posn<0){
    	if(!prior_add_resource_mark_for_id) {
    		//自动判断先添加缺少的"/"字符
	        int fisrt_slash_posn=ppk_uri.indexOf("/");
	        if(fisrt_slash_posn<0){ //是根标识
	            ppk_uri += "/";
	        }else{ //是扩展标识
	            //判断尾部的内容资源名是否有文件扩展名或者方法标志符
	            int last_slash_posn=ppk_uri.lastIndexOf("/");
	            
	            if( last_slash_posn!=ppk_uri.length()-1){ //不是以"/"字符结尾
	                int last_point_posn=ppk_uri.lastIndexOf(".");
	                int function_mark_posn=ppk_uri.lastIndexOf(")");
	                if(last_point_posn<last_slash_posn && function_mark_posn<0){
	                    //没有文件扩展名或者是方法标识，默认为目录，需要补上"/"
	                    ppk_uri += "/";
	                }
	            }
	        }
    	}
    	
    	ppk_uri += Config.PPK_URI_RESOURCE_MARK;
    }

    return ppk_uri;
  }
  
  //解构PPK资源地址
  public static PPkUriParts splitPPkURI(String in_uri)
  {
    try{
      //检查URI格式符合要求
      String format_ppk_uri = formatPPkURI(in_uri,true);
      
      if( format_ppk_uri==null ){
        logger.info("splitPPkURI() meet invalid ppk-uri:"+in_uri);
        return null;
      }
      
      int resoure_mark_posn=format_ppk_uri.indexOf(Config.PPK_URI_RESOURCE_MARK);
      
      PPkUriParts obj_uri_parts = new PPkUriParts();
      obj_uri_parts.resource_versoin = format_ppk_uri.substring(resoure_mark_posn+1,format_ppk_uri.length());
      String str_path_segment = format_ppk_uri.substring(Config.PPK_URI_PREFIX.length(),resoure_mark_posn);

      //System.out.println("str_path_segment="+str_path_segment);
      if(str_path_segment.endsWith("/")){ //类似"ppk:123/"或"ppk:123/abc/"指向默认内容主页 
        obj_uri_parts.resource_id="";
        obj_uri_parts.parent_odin_path=str_path_segment.substring(0,str_path_segment.length()-1);
      }else{
        int tmp_posn=str_path_segment.lastIndexOf('/');
        if(tmp_posn>0){
          obj_uri_parts.resource_id=str_path_segment.substring(tmp_posn+1,str_path_segment.length());
          obj_uri_parts.parent_odin_path=str_path_segment.substring(0,tmp_posn);
        }else{
          obj_uri_parts.parent_odin_path="";
          obj_uri_parts.resource_id=str_path_segment;
        }
      }

      obj_uri_parts.format_uri = format_ppk_uri;
          
      //System.out.println("ODIN.splitPPkURI()\n format_uri="+obj_uri_parts.format_uri+" , parent_odin_path="+obj_uri_parts.parent_odin_path+", resource_id="+obj_uri_parts.resource_id+", resource_versoin="+obj_uri_parts.resource_versoin+"\n");
      
      return obj_uri_parts;
    }catch(Exception e){
      logger.info("splitPPkURI() meet invalid ppk-uri:"+in_uri + " ,"+e.toString() );
      return null;
    }
  }
  
  //获得PPk URI对应资源版本号，即结尾类似“*1.0”这样的描述，如果没有则返回空字符串
  public static String getPPkResourceVer(String ppk_uri){
    if(ppk_uri==null)
        return null;

    int resoure_mark_posn=ppk_uri.indexOf(Config.PPK_URI_RESOURCE_MARK);

    return resoure_mark_posn>0 ? ppk_uri.substring(resoure_mark_posn+1).trim():"";

  }
  
  //获得表示最新版本的URI（去掉可能的版本号）
  public static String getLastestPPkURI(String ppk_uri){
    if(ppk_uri==null)
        return null;

    int resoure_mark_posn=ppk_uri.indexOf(Config.PPK_URI_RESOURCE_MARK);
    if( resoure_mark_posn<0){ 
        ppk_uri += Config.PPK_URI_RESOURCE_MARK;
    }else{
        ppk_uri = ppk_uri.substring(0,resoure_mark_posn+1);
    }

    return ppk_uri;
  }
  
//将根标识中的英文字母按奥丁号规范转换成对应数字
  public static String convertLetterToNumberInRootODIN(String  original_odin){  
     String converted_odin="";
     original_odin=original_odin.toUpperCase();
     for(int kk=0;kk<original_odin.length();kk++){  
        int chr=original_odin.charAt(kk);  
        switch(chr){
            case 'O':
              chr='0';
              break;
            case 'I':
            case 'L':
            case 'A':
              chr='1';
              break;
            case 'B':
            case 'C':
            case 'Z':
              chr='2';
              break;
            case 'D':
            case 'E':
            case 'F':
              chr='3';
              break;
            case 'G':
            case 'H':
              chr='4';
              break;
            case 'J':
            case 'K':
            case 'S':
              chr='5';
              break;
            case 'M':
            case 'N':
              chr='6';
              break;
            case 'P':
            case 'Q':
            case 'R':
              chr='7';
              break;
            case 'T':
            case 'U':
            case 'V':
              chr='8';
              break;
            case 'W':
            case 'X':
            case 'Y':
              chr='9';
              break;
            default:
              break;
        }
        converted_odin=converted_odin+(char)chr;
     }  
     return Util.isNumeric(converted_odin)?converted_odin:null;  
  }   
  
  //获得指定数字短标识的对应字母转义名称组合
  public static List getEscapedListOfShortODIN(Integer  short_odin){ 
    List<String> listEscaped = new ArrayList<String>();
    
    
    String strTmp=short_odin.toString();
    listEscaped=getEscapedLettersOfShortODIN(listEscaped,strTmp,0,"");
    
    System.out.println("listEscaped:"+listEscaped.toString());
    
    return listEscaped;
  }
  
  public static List getEscapedLettersOfShortODIN(List listEscaped,String  original,int posn,String pref){ 
    int tmpNum=Integer.parseInt(String.valueOf(original.charAt(posn)));
    System.out.println("original["+posn+"]:"+tmpNum);
    
    String tmpLetters=LetterEscapeNumSet[tmpNum];
    for(int tt=0;tt<tmpLetters.length();tt++){
      String new_str=pref+String.valueOf(tmpLetters.charAt(tt));
      
      if(posn<original.length()-1){
        listEscaped=getEscapedLettersOfShortODIN(listEscaped,original,posn+1,new_str);
      }else{
        listEscaped.add(new_str);
      }
    }
    
    return listEscaped;
  }
  
  public static void init(){
    createTables(null);    
  }
  
  public static void createTables(Database db){
    if(db==null) 
      db = Database.getInstance();
    try {
      db.executeUpdate("CREATE TABLE IF NOT EXISTS odins (tx_index INTEGER PRIMARY KEY, tx_hash CHAR(64) UNIQUE,block_index INTEGER,full_odin CHAR(32) UNIQUE,short_odin INTEGER UNIQUE , register TEXT, admin TEXT,odin_set TEXT, validity TEXT)");
      //db.executeUpdate("ALTER TABLE  odins ADD INDEX block_idx(block_index)");  //for test mysql 
      db.executeUpdate("CREATE INDEX IF NOT EXISTS block_index_idx ON odins (block_index)");
      db.executeUpdate("CREATE INDEX IF NOT EXISTS tx_index ON odins (tx_index)");
      db.executeUpdate("CREATE INDEX IF NOT EXISTS full_odin ON odins (full_odin)");
      db.executeUpdate("CREATE INDEX IF NOT EXISTS short_odin ON odins (short_odin)");

      db.executeUpdate("CREATE TABLE IF NOT EXISTS odin_update_logs (log_id CHAR(32) PRIMARY KEY , tx_index INTEGER UNIQUE,block_index INTEGER,full_odin CHAR(32), updater TEXT,destination TEXT,update_set TEXT, validity TEXT,required_confirmer TEXT)");
      //db.executeUpdate("ALTER TABLE  odin_update_logs ADD INDEX full_odin_idx(full_odin)"); //for test mysql 
      db.executeUpdate("CREATE INDEX IF NOT EXISTS logid_idx ON odin_update_logs (log_id);");
      db.executeUpdate("CREATE INDEX IF NOT EXISTS odin_idx ON odin_update_logs (full_odin);");
      db.executeUpdate("CREATE INDEX IF NOT EXISTS update_tx_idx ON odin_update_logs (tx_index);");
      
      //db.executeUpdate("CREATE TABLE IF NOT EXISTS ppk_uri_caches (ppk_uri TEXT PRIMARY KEY, resp_json TEXT, validity TEXT)");
      //db.executeUpdate("CREATE INDEX IF NOT EXISTS ppk_uri_idx ON ppk_uri_caches (ppk_uri)");

      /*
      //test chinese encode
      String oldStr="EncodeTest编码测试";
      String newStr=Util.uncompress(Util.compress(oldStr.getBytes(Config.PPK_TEXT_CHARSET),Config.DATA_BIN_DEFLATE),Config.DATA_BIN_DEFLATE);
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

        String validity = Config.ODIN_STATUS_INVALID;
        JSONObject odin_set=new JSONObject( );
        String ODIN_BASE_SET_ADMIN= destination.length()==0 ? source:destination;
                
        if (message.size() >2) {
          ByteBuffer byteBuffer = ByteBuffer.allocate(message.size());
          for (byte b : message) {
            byteBuffer.put(b);
          }      

          Byte odin_set_data_type=byteBuffer.get(0); 
          BitcoinVarint odin_set_len_varint=BitcoinVarint.getFromBuffer(byteBuffer,1);
          int odin_set_length = odin_set_len_varint.intValue();
          int odin_set_start = 1+odin_set_len_varint.size();
          logger.info( "\n=============================\n message.size()="+message.size()+",odin_set_data_type="+odin_set_data_type+",odin_set_start="+odin_set_start+",odin_set_length="+odin_set_length+"\n=====================\n");
          
          if( !source.equals("") && message.size()==odin_set_start+odin_set_length )
          {
            validity = Config.ODIN_STATUS_VALID;
            
            byte[] odin_set_byte_array=new byte[odin_set_length];
            
            for(int off=0;off<odin_set_length;off++)
              odin_set_byte_array[off]=byteBuffer.get(odin_set_start+off);
            
            
            try{ 
              if(odin_set_data_type!=Config.DATA_TEXT_UTF8)
                  odin_set_byte_array=Util.uncompress(odin_set_byte_array,odin_set_data_type);
                  
              odin_set=new JSONObject(new String(odin_set_byte_array,Config.PPK_TEXT_CHARSET));
                            
              logger.info( "\n=============================\n odin_set="+odin_set.toString()+"\n=====================\n");
            } catch (Exception e) {  
              odin_set=new JSONObject();
              logger.error(e.toString());
            }  

            
          }
        }    

        PreparedStatement ps = db.connection.prepareStatement("insert into odins(tx_index, tx_hash, block_index, full_odin,short_odin,admin, register,odin_set,validity) values('"+txIndex.toString()+"','"+txHash+"','"+blockIndex.toString()+"','"+full_odin+"','"+short_odin.toString()+"',?,'"+source+"',?,'"+validity+"');");

        ps.setString(1, ODIN_BASE_SET_ADMIN);
        ps.setString(2, odin_set.toString());
        ps.execute();
      }
    } catch (SQLException e) {  
      logger.error(e.toString());
    }
  }

  public static List<OdinInfo> getPending() {
      return getPending(null);
  }
  
  public static List<OdinInfo> getPending(String register) {
    Database db = Database.getInstance();
    ResultSet rs = db.executeQuery("select * from transactions where block_index<0 "+( register==null? "":"and source='"+register+"'") +" and prefix_type=1 order by tx_index desc;");
    List<OdinInfo> odins = new ArrayList<OdinInfo>();
    Blocks blocks = Blocks.getInstance();
    try {
      while (rs.next()) {
        String source = rs.getString("source");
        String destination = rs.getString("destination");
        //BigInteger btcAmount = BigInteger.valueOf(rs.getLong("btc_amount"));
        //BigInteger fee = BigInteger.valueOf(rs.getLong("fee"));
        Integer blockIndex = rs.getInt("block_index");
        Integer blockTime = rs.getInt("block_time");
        String txHash = rs.getString("tx_hash");
        Integer txIndex = rs.getInt("tx_index");
        byte[] odin_data = Util.hexStringToBytes(rs.getString("data"));

        ResultSet rsCheck = db.executeQuery("select * from odins where tx_index='"+txIndex.toString()+"'");
        if (!rsCheck.next()) {
          Byte messageType = blocks.getPPkMessageTypeFromTransaction(odin_data);
          List<Byte> message = blocks.getPPkMessageFromTransaction(odin_data);
          
          logger.info("messageType="+messageType.toString()+"  message.size="+message.size());

          if (messageType==ODIN.id && message.size()>2) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(message.size());
            for (byte b : message) {
              byteBuffer.put(b);
            }      
            Byte odin_set_data_type=byteBuffer.get(0); 
            BitcoinVarint odin_set_len_varint=BitcoinVarint.getFromBuffer(byteBuffer,1);
            int odin_set_length = odin_set_len_varint.intValue();
            int odin_set_start = 1+odin_set_len_varint.size();
          
            if( !( source.equals("")) && message.size()==odin_set_start+odin_set_length )
            {
                byte[] odin_set_byte_array=new byte[odin_set_length];
                for(int off=0;off<odin_set_length;off++)
                  odin_set_byte_array[off]=byteBuffer.get(odin_set_start+off);
              
                JSONObject odin_set;
                String ODIN_BASE_SET_ADMIN = destination; //HtmlRegexpUtil.filterHtml( destination.length()==0 ? source : destination );
                                    
                try{
                  if(odin_set_data_type!=Config.DATA_TEXT_UTF8)
                      odin_set_byte_array=Util.uncompress(odin_set_byte_array,odin_set_data_type);
                  
                  odin_set=new JSONObject(new String(odin_set_byte_array,Config.PPK_TEXT_CHARSET));
                  
                } catch (Exception e) {  
                  logger.error(e.toString());
                  return odins;
                }  
                OdinInfo odinInfo = new OdinInfo();

                odinInfo.register = source;
                
                odinInfo.fullOdin="";
                odinInfo.shortOdin=-1;
                odinInfo.txSnInBlock=-1;
                odinInfo.admin=ODIN_BASE_SET_ADMIN;
                odinInfo.odinSet = odin_set;
                odinInfo.txIndex = txIndex;
                odinInfo.txHash = txHash;
                odinInfo.blockIndex = blockIndex;
                odinInfo.blockTime = blockTime;
                odinInfo.validity = Config.ODIN_STATUS_PENDING;
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
    
    odin=ODIN.convertLetterToNumberInRootODIN(odin);
    
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
          
          odinInfo.odinSet.put(Config.ODIN_BASE_SET_REGISTER,odinInfo.register);
          odinInfo.odinSet.put(Config.ODIN_BASE_SET_ADMIN,odinInfo.admin);
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
    
  public static OdinTransctionData createOdin(String register,String admin,JSONObject odin_set) throws Exception {
    if (register.equals("")) throw new Exception("Please specify a register address.");
    if (odin_set==null) throw new Exception("Please specify valid odin_set.");
    
    logger.info("createOdin odin_set="+odin_set.toString());
    
    Byte odin_set_data_type=Config.DATA_TEXT_UTF8; 
    byte[] odin_set_byte_array=odin_set.toString().getBytes(Config.PPK_TEXT_CHARSET);
    byte[] odin_set_byte_array_compressed=Util.compress(odin_set.toString().getBytes(Config.PPK_TEXT_CHARSET),Config.DATA_BIN_DEFLATE);
    
    logger.info("odin_set_byte_array.length="+odin_set_byte_array.length);
    logger.info("odin_set_byte_array_compressed.length="+odin_set_byte_array_compressed.length);
    
    if(odin_set_byte_array.length>odin_set_byte_array_compressed.length){ //need compress the long data
       odin_set_byte_array=odin_set_byte_array_compressed;
       odin_set_data_type=Config.DATA_BIN_DEFLATE;
    }    

    int odin_set_length=odin_set_byte_array.length;
    byte[] len_bytes=BitcoinVarint.toBytes(odin_set_length);
    int total_data_len=1+1+len_bytes.length+odin_set_length;
    
    if (total_data_len>Config.MAX_ODIN_DATA_LENGTH) throw new Exception("Too big setting data.(Should be less than "+Config.MAX_ODIN_DATA_LENGTH+" bytes)");

    ByteBuffer byteBuffer = ByteBuffer.allocate(total_data_len);
    byteBuffer.put(id);
    byteBuffer.put(odin_set_data_type);
    byteBuffer.put(len_bytes);
    byteBuffer.put(odin_set_byte_array,0,odin_set_length);
    
    byte[] data = byteBuffer.array();

    OdinTransctionData tx = new OdinTransctionData(
          register, 
          admin, 
          BigInteger.valueOf(Config.dustSize), 
          BigInteger.valueOf(Config.ppkStandardDataFee),
          Config.PPK_ODIN_MARK_PUBKEY_HEX ,
          Util.bytesToHexString(data)
       );

    /*
    //just for debug
    Blocks blocks = Blocks.getInstance();
    logger.info("Test:createOdin register="+register+", MAKR_PUBKEY_HEX="+Config.PPK_ODIN_MARK_PUBKEY_HEX+" dataString="+(new String(data)));
    blocks.importPPkTransaction(blocks.transaction(tx), null, null, null);
    System.exit(0);
    */
    
    return tx;
  }
  
  public static HashMap<String,Object> parseOdinSet(OdinInfo odinInfo,String myaddress,String register,String admin)  {
    HashMap<String,Object> map = new HashMap<String,Object>();
    map.put("full_odin", odinInfo.fullOdin);
    map.put("short_odin", odinInfo.shortOdin.toString());
    map.put("register", register);
    map.put("admin", admin);
    map.put("tx_index",odinInfo.txIndex.toString());
    map.put("tx_hash", odinInfo.txHash);
    map.put("block_index", odinInfo.blockIndex.toString());
    map.put("block_time", Util.timeFormat(odinInfo.blockTime));
    map.put("validity",odinInfo.validity);
    map.put("validity_label", Language.getLangLabel( odinInfo.validity ));
    
    try{
        JSONObject odin_set = odinInfo.odinSet; 
        map=parseOdinSet(map,odin_set,myaddress,register,admin);
    }catch(Exception e){
        e.printStackTrace();
    }
    return map;
  }

  public static HashMap<String,Object> parseOdinSet(HashMap<String,Object> map,JSONObject odin_set,String myaddress,String register,String admin) throws Exception {
    String authSet=""+odin_set.optInt(Config.ODIN_BASE_SET_AUTH,0); //兼容处理填写非数字的取值
    
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
  
    if(odin_set.isNull(Config.ODIN_BASE_SET_PNS_URL))
      map.put(Config.ODIN_BASE_SET_PNS_URL, "");
    else
      map.put(Config.ODIN_BASE_SET_PNS_URL, HtmlRegexpUtil.filterHtml(odin_set.getString(Config.ODIN_BASE_SET_PNS_URL)));

    map.put(Config.ODIN_BASE_SET_AUTH, authSet);
    map.put("auth_label", Util.getOdinAuthRightLabel( authSet ));
    
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
          map.put("vd_set_type", vd_set.optString(Config.ODIN_SET_VD_TYPE,""));
          map.put("vd_set_pubkey", vd_set.optString(Config.ODIN_SET_VD_PUBKEY,""));
        }catch(Exception e){
          
        }
    }
    map.put("vd_set_debug", vd_set_debug);

    
    return map;
  }
    
  public static boolean checkUpdatable(String authSet,String updater,String register,String admin  ) {
      if(
          ( authSet.equals("1")  && updater.equals(admin) )
        ||(  
            ( authSet==null || authSet.length()==0 || authSet.equals("0")||authSet.equals("2") ) 
            && ( updater.equals(register) || updater.equals(admin) ) 
           )
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
  
 
  //查询指定地址所注册的ODIN记录数
  public static int getUserRegisteredNum(String address){
      Database db = Database.getInstance();
      ResultSet rs = db.executeQuery("SELECT count(*) AS num FROM odins WHERE register='"+address+"'");

      try {
          if ( rs.next() ) {
              return rs.getInt("num");
          }
      } catch (SQLException e) {

      }
      
      return 0;
  }
  
  //查询指定地址所注册的ODIN记录数
  public static int getUserAdminNum(String address){
      Database db = Database.getInstance();
      ResultSet rs = db.executeQuery("SELECT count(*) AS num FROM odins WHERE admin='"+address+"'");

      try {
          if ( rs.next() ) {
              return rs.getInt("num");
          }
      } catch (SQLException e) {

      }
      
      return 0;
  }
  
  //查询指定地址所注册或管理的ODIN记录
  public static ArrayList<HashMap<String, Object>> getUserRelatedODINs(String address,boolean  is_register_or_admin,int start,int size)
  {
    Database db = Database.getInstance();
    
    String str_sql = "select cp.full_odin,cp.short_odin,cp.register,cp.admin ,cp.tx_hash ,cp.tx_index ,cp.block_index,transactions.block_time,cp.odin_set,cp.validity from odins cp,transactions where cp."
                    + ( is_register_or_admin ? "register":"admin" ) 
                    + "='"+address+"' and cp.tx_index=transactions.tx_index order by cp.block_index desc, cp.tx_index desc " + " LIMIT " + start + ',' +size;

    ResultSet rs = db.executeQuery( str_sql );
    ArrayList<HashMap<String, Object>>  odins = new ArrayList<HashMap<String, Object>>();
    try {
      while ( rs.next()) {
        HashMap<String,Object> map = new HashMap<String,Object>();
        map.put("full_odin", rs.getString("full_odin"));
        map.put("short_odin", rs.getString("short_odin"));
        map.put("register", rs.getString("register"));
        map.put("register_label", Util.getFriendlyAddressLabel(rs.getString("register")));
        map.put("admin", rs.getString("admin"));
        map.put("admin_label", Util.getFriendlyAddressLabel(rs.getString("admin")));
        map.put("tx_index", rs.getString("tx_index"));
        map.put("tx_hash", rs.getString("tx_hash"));
        map.put("validity", rs.getString("validity"));
        map.put("validity_label", Language.getLangLabel( rs.getString("validity") ));
        map.put("block_index", rs.getString("block_index"));
        map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
                
        try{
          JSONObject odin_set = new JSONObject(rs.getString("odin_set")); 
          map=ODIN.parseOdinSet(map,odin_set,address,rs.getString("register"),rs.getString("admin"));

          odins.add(map);
        }catch (Exception e) {
          logger.error(e.toString());
        }
      }
    } catch (SQLException e) {

    }
    
    return odins;
  }
  
  //查询指定地址所相关的ODIN更新日志记录数
  public static int getUserUpdateNum(String address){
      Database db = Database.getInstance();
      ResultSet rs = db.executeQuery("SELECT count(*) AS num FROM odins cp,odin_update_logs l WHERE l.full_odin=cp.full_odin AND ( l.updater='"+address+"'   OR ( l.validity='"+Config.ODIN_UPDATE_STATUS_RECEIPTING+"' AND l.destination='"+address+"') OR (l.validity='"+Config.ODIN_UPDATE_STATUS_AWAITING+"' AND l.required_confirmer='"+address+"' and ( cp.register='"+address+"' or cp.admin='"+address+"' ) ) )");

      try {
          if ( rs.next() ) {
              return rs.getInt("num");
          }
      } catch (SQLException e) {

      }
      
      return 0;
  }
  
  //查询指定地址相关的ODIN更新记录
  public static ArrayList<HashMap<String, Object>> 
                    getUserUpdateLogs(String address,String log_type,int start,int size)
  {
    Database db = Database.getInstance();
    
    String str_sql;

    if( Config.ODIN_UPDATE_STATUS_RECEIPTING.equals(log_type)){
        str_sql="select l.log_id,l.tx_index, l.block_index,l.updater,l.destination,l.required_confirmer, l.update_set,l.validity,cp.full_odin,cp.short_odin,cp.register,cp.admin,transactions.block_time,transactions.tx_hash from odins cp,odin_update_logs l,transactions where l.validity='"+Config.ODIN_UPDATE_STATUS_RECEIPTING+"' and l.full_odin=cp.full_odin and ( cp.register='"+address+"' or cp.admin='"+address+"' or l.destination='"+address+"') and l.tx_index=transactions.tx_index order by l.block_index desc, cp.tx_index desc";
    }else if( Config.ODIN_UPDATE_STATUS_AWAITING.equals(log_type) ){
        str_sql="select l.log_id,l.tx_index, l.block_index,l.updater,l.destination,l.required_confirmer, l.update_set,l.validity,cp.full_odin,cp.short_odin,cp.register,cp.admin,transactions.block_time,transactions.tx_hash from odins cp,odin_update_logs l,transactions where l.validity='"+Config.ODIN_UPDATE_STATUS_AWAITING+"' and l.full_odin=cp.full_odin and (l.updater='"+address+"' or l.required_confirmer='"+address+"') and ( cp.register='"+address+"' or cp.admin='"+address+"') and l.tx_index=transactions.tx_index order by l.block_index desc, cp.tx_index desc";
    }else if( "history".equals(log_type) ){
        str_sql="select l.log_id, l.tx_index, l.block_index,l.updater,l.destination,l.required_confirmer, l.update_set,l.validity,cp.full_odin,cp.short_odin,cp.register,cp.admin,transactions.block_time,transactions.tx_hash from odins cp,odin_update_logs l,transactions where l.updater='"+address+"' and l.full_odin=cp.full_odin and (l.validity='"+Config.ODIN_STATUS_VALID+"' or l.validity='"+Config.ODIN_STATUS_INVALID+"') and l.tx_index=transactions.tx_index order by l.block_index desc, cp.tx_index desc";
    }else if( "all".equals(log_type) ){
        str_sql="select l.log_id, l.tx_index, l.block_index,l.updater,l.destination,l.required_confirmer, l.update_set,l.validity,cp.full_odin,cp.short_odin,cp.register,cp.admin,transactions.block_time,transactions.tx_hash from odins cp,odin_update_logs l,transactions where ( l.updater='"+address+"'   OR ( l.validity='"+Config.ODIN_UPDATE_STATUS_RECEIPTING+"' AND l.destination='"+address+"') OR (l.validity='"+Config.ODIN_UPDATE_STATUS_AWAITING+"' AND l.required_confirmer='"+address+"' and ( cp.register='"+address+"' or cp.admin='"+address+"') ) ) and l.full_odin=cp.full_odin and l.tx_index=transactions.tx_index order by l.block_index desc, cp.tx_index desc";
    }else{
        return null;
    }
    
    str_sql += " LIMIT " + start + ',' +size;
    
    //logger.info( "str_sql="+str_sql);

    ResultSet rs = db.executeQuery( str_sql );
    ArrayList<HashMap<String, Object>>  updates = new ArrayList<HashMap<String, Object>>();
    try {
      while ( rs.next()) {
        HashMap<String,Object> map = new HashMap<String,Object>();
        map.put("full_odin", rs.getString("full_odin"));
        map.put("short_odin", rs.getString("short_odin"));
        map.put("updater", rs.getString("updater"));
        map.put("tx_index", rs.getString("tx_index"));
        map.put("tx_hash", rs.getString("tx_hash"));
        map.put("validity", rs.getString("validity"));
        map.put("validity_label", Language.getLangLabel( rs.getString("validity") ));
        map.put("block_index", rs.getString("block_index"));
        map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
        map.put("log_id", rs.getString("log_id"));
        
        if( Config.ODIN_UPDATE_STATUS_RECEIPTING.equals(rs.getString("validity") )
            && address.equals(rs.getString("destination")) 
           ){
            map.put("awaiting_my_receipting", true);
        }else if( Config.ODIN_UPDATE_STATUS_AWAITING.equals(rs.getString("validity") )
            && address.equals(rs.getString("required_confirmer")) 
            && ( address.equals(rs.getString("register")) || address.equals(rs.getString("admin"))  )
           ){
            map.put("awaiting_my_confirm", true);
        }
        
        
        try{
          JSONObject update_set = new JSONObject(rs.getString("update_set")); 
          map=OdinUpdate.parseOdinUpdateSet(map,rs.getString("updater"),rs.getString("destination"),update_set);
          updates.add(map);
        }catch (Exception e) {
          logger.error("ODIN.getUserUpdateLogs():"+e.toString());
        }
      }
    } catch (SQLException e) {
        logger.error("ODIN.getUserUpdateLogs():"+e.toString());
    }

    return updates;
  }
  
  //从本地数据库获取根标识的设置
  public static JSONObject getRootOdinSet(String root_odin)  {
    OdinInfo odinInfo=ODIN.getOdinInfo(root_odin);
    
    if(odinInfo==null)
        return null;
    
    JSONObject odinSet = odinInfo.odinSet;
    
    //判断如果没有设置pns和ap，则自动使用本地配置的默认的pns方便体验新版本功能,20200529
    String tmp_pns_url = odinSet.optString(Config.ODIN_BASE_SET_PNS_URL,"");
    if( tmp_pns_url.length()==0 && Config.ppkDefaultPnsURI.length()>0 ){
       try{
          odinSet.put(Config.ODIN_BASE_SET_PNS_URL,Config.ppkDefaultPnsURI); 
       }catch (Exception e) {
          logger.error(e.toString());
       }
    }

    return  odinSet;
  }
  
  /*
  //比较两个URI资源版本，待完善
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
  */
}

class PPkUriParts {
  public String format_uri;
  public String parent_odin_path;
  public String resource_id;
  public String resource_versoin;
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