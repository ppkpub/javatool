import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.security.Security;
import java.sql.ResultSet;
import java.sql.SQLException;
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

import java.text.ParsePosition;
import java.util.TimeZone;
import java.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONArray;  
import org.json.JSONObject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.WalletTransaction;

import org.ipfs.api.IPFS;
import org.ipfs.api.NamedStreamable;
import org.ipfs.api.Multihash;
import org.ipfs.api.Base58;

public class Util {
  static Logger logger = LoggerFactory.getLogger(Util.class);
  private static String mMinVersion=null;
  private static Boolean mIpfsRunning=null;
  

  private static boolean isRedirected( Map<String, List<String>> header ) {
    for(String hv : header.get(null)) {
      if(hv.contains(" 301 ") || hv.contains(" 302 ")) return true;
    }
    return false;
  }
  public static void downloadToFile(String link, String fileName) {
    try {
      URL url  = new URL(link);
      HttpURLConnection http = (HttpURLConnection)url.openConnection();
      Map<String, List<String>> header = http.getHeaderFields();
      while(isRedirected(header)) {
        link = header.get("Location").get(0);
        url = new URL(link);
        http = (HttpURLConnection)url.openConnection();
        header = http.getHeaderFields();
      }
      InputStream input = http.getInputStream();
      byte[] buffer = new byte[4096];
      int n = -1;
      OutputStream output = new FileOutputStream( new File( fileName ));
      while ((n = input.read(buffer)) != -1) {
        output.write(buffer, 0, n);
      }
      output.close();
    } catch (Exception e) {
      logger.info(e.toString());
    }
  }
  
  //获得方便显示的地址
  public static String getFriendlyAddressLabel(String address) {
    if(address.length()>15){
        address=address.substring(0,8)+"..."+address.substring(address.length()-4);
    }
    return address;
  }

  public static String format(Double input) {
    return format(input, "#.00");
  }
  
  public static Long getNowTimestamp() {
    return (new Date()).getTime()/(long)1000;
  }

  public static String format(Double input, String format) {
    return (new DecimalFormat(format)).format(input);
  }

  public static String timeFormat(Date date) {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); // the format of your date
    sdf.setTimeZone(TimeZone.getTimeZone(Config.defaultTimeZone));
    String formattedDate = sdf.format(date);
    return formattedDate;
  }

  public static String timeFormat(Integer timestamp) {
    Date date = new Date(timestamp*1000L); // *1000 is to convert seconds to milliseconds
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); // the format of your date
    sdf.setTimeZone(TimeZone.getTimeZone(Config.defaultTimeZone));
    String formattedDate = sdf.format(date);
    return formattedDate;
  }

  static float roundOff(Double x, int position) {
    float a = x.floatValue();
    double temp = Math.pow(10.0, position);
    a *= temp;
    a = Math.round(a);
    return (a / (float)temp);
  }

  public static Integer getLastBlock() {
    Blocks blocks = Blocks.getInstance();
    Database db = Database.getInstance();
    ResultSet rs = db.executeQuery("select * from blocks order by block_index desc limit 1;");
    try {
      while(rs.next()) {
        return rs.getInt("block_index");
      }
    } catch (SQLException e) {
    }  
    return blocks.ppkBlock;
  }

  public static Integer getLastTxIndex() {
    Database db = Database.getInstance();
    ResultSet rs = db.executeQuery("SELECT * FROM transactions WHERE tx_index = (SELECT MAX(tx_index) from transactions);");
    try {
      while(rs.next()) {
        return rs.getInt("tx_index");
      }
    } catch (SQLException e) {
    }  
    return 0;
  }  
  
  public static void updateLastParsedBlock(Integer block_index) {
    Database db = Database.getInstance();
    db.executeUpdate("REPLACE INTO sys_parameters (para_name,para_value) values ('last_parsed_block','"+block_index.toString()+"');");
  }  
  
  public static Integer getLastParsedBlock() {
    Database db = Database.getInstance();
    ResultSet rs = db.executeQuery("SELECT para_value FROM sys_parameters WHERE para_name='last_parsed_block'");
    try {
      while(rs.next()) {
        return rs.getInt("para_value");
      }
    } catch (SQLException e) {
    }  
    return 0;
  }  

  public static void debit(String address, String asset, BigInteger amount_satoshi, String callingFunction, String event, Integer blockIndex) {
    Database db = Database.getInstance();
    if (hasBalance(address, asset)) {
      BigInteger existingAmount = getBalance(address,asset);
      BigInteger newAmount = existingAmount.subtract(amount_satoshi);
      if (newAmount.compareTo(BigInteger.ZERO)>=0) {
        db.executeUpdate("update balances set amount='"+newAmount.toString()+"' where address='"+address+"' and asset='"+asset+"';");
        db.executeUpdate("insert into debits(address, asset, amount, calling_function, event, block_index) values('"+address+"','"+asset+"','"+amount_satoshi.toString()+"', '"+callingFunction+"', '"+event+"', '"+blockIndex.toString()+"');");
      }
    }
  }

  public static void credit(String address, String asset, BigInteger amount_satoshi, String callingFunction, String event, Integer blockIndex) {
    Database db = Database.getInstance();
    if (hasBalance(address, asset)) {
      BigInteger existingAmount = getBalance(address,asset);
      BigInteger newAmount = existingAmount.add(amount_satoshi);
      db.executeUpdate("update balances set amount='"+newAmount.toString()+"' where address='"+address+"' and asset='"+asset+"';");
    } else {
      db.executeUpdate("insert into balances(address, asset, amount) values('"+address+"','"+asset+"','"+amount_satoshi.toString()+"');");        
    }
    db.executeUpdate("insert into credits(address, asset, amount, calling_function, event, block_index) values('"+address+"','"+asset+"','"+amount_satoshi.toString()+"', '"+callingFunction+"', '"+event+"', '"+blockIndex.toString()+"');");
  }
  
  public static BigDecimal getBalanceInBtc(int balance ) {
    BigDecimal balance_in_satoshi = new BigDecimal(balance);
    BigDecimal btc_unit = new BigDecimal(Config.btc_unit);

    return balance_in_satoshi.divide(btc_unit, 8, RoundingMode.HALF_UP);
  }
  
  public static int getRegisterOdinNum(String address) {
    Database db = Database.getInstance();
    ResultSet rs = db.executeQuery("select count(*) as odin_num from odins where register='"+address+"';");
    try {
      if (rs.next()) {
        return rs.getInt("odin_num");
      }
    } catch (SQLException e) {
    }
    return 0;
  }
  
  public static Boolean hasBalance(String address, String asset) {
    Database db = Database.getInstance();
    ResultSet rs = db.executeQuery("select amount from balances where address='"+address+"' and asset='"+asset+"';");
    try {
      if (rs.next()) {
        return true;
      }
    } catch (SQLException e) {
    }
    return false;
  }

  public static String transactionAddress(String txHash) {
    return "https://api.biteasy.com/blockchain/v1/transactions/"+txHash;
  }
  
  
  /*
  public static List<UnspentOutput> getUnspents(String address) {
    String result = CommonHttpUtil.getInstance().getContentFromUrl( "http://btc.blockr.io/api/v1/address/unspent/"+address+"?multisigs=1" );
    List<UnspentOutput> unspents = new ArrayList<UnspentOutput> ();
    try {
        JSONObject tempObject=new JSONObject(result);
        
        JSONArray tempArray=tempObject.getJSONObject("data").getJSONArray("unspent");
        
        for(int tt=0;tt<tempArray.length();tt++){
            JSONObject item_obj=(JSONObject)tempArray.get(tt);
            
            //check the script is valid
            boolean is_script_valid=true;

            //String[] chunks=item_obj.getString("script").split(" ");
            
            //for(String chunk : chunks){ 
            //  if( ( chunk.length()==65*2 && !chunk.startsWith("04") )
            //     || ( chunk.length()==33*2 && !chunk.startsWith("02") && !chunk.startsWith("03")  ) 
            //    ) 
            //    is_script_valid=false;
            //}

            if(is_script_valid){
              UnspentOutput tempUnspentObj=new UnspentOutput();
              
              tempUnspentObj.amt_satoshi=item_obj.getDouble("amount")*Config.btc_unit;
              tempUnspentObj.txid=item_obj.getString("tx");
              tempUnspentObj.vout=item_obj.getInt("n");

              tempUnspentObj.scriptPubKeyHex=item_obj.getString("script");

              unspents.add(tempUnspentObj);
            }else{
              logger.error("Ignore a invalid transction:"+item_obj.getString("script"));
            }
        }
    } catch (Exception e) {
      logger.error(e.toString());
      return getUnspentsWithoutDustTX(address);
    }
    return unspents;
   
  }
  */
  
  public static int getUnspentCount(String address) {
     try {
        String api_url="https://chain.api.btc.com/v3/address/" + address + "/unspent";
        JSONObject tempObject=fetchBtcAPI(api_url);
        
        int total_count=tempObject.getJSONObject("data").getInt("total_count");
        return total_count;
      } catch (Exception e) {
        //此API有异常
        logger.error("getUnspentCount() BTC.com API exception:"+e.toString());
        return -1;
    }
  }
  
  //缺省获取指定地址未花费交易的方法，包含多重签名类型的输出
  public static List<UnspentOutput> getUnspents(String address,boolean isOdinTransaction) {
    if(!Config.useDustTX){
      UnspentList ul = getUnspentsWithoutDustTX(address);
      return ul.unspents;
    }

    if(!isOdinTransaction){ //普通转账交易，直接返回最大允许数量的UTXO列表
        try {
            UnspentList ul=getValidUnspents(address,Config.maxUseUTXO,true);
            return ul.unspents;
        } catch (Exception e) {
            //主API有异常，自动切换到另一个备用API
            logger.error("getUnspents() BTC.com API exception:"+e.toString());
            UnspentList ul = getUnspentsWithoutDustTX(address);
            return ul.unspents;
        }
    }
    
    List<UnspentOutput> unspents=null;
    int txCounter=0;
    long valueCounter=0L;

    //对于ODIN标识注册交易，优先使用上一次发送交易的本地缓存UTXO，以减少注册等待时间；对于很少使用的普通转账交易,不使用本地缓存，以免出现余额同步问题
    unspents = Blocks.getCachedLastUnspents(address);
    String lastTxHash=null;
    
    //System.out.println("Util.getUnspents() getCachedLastUnspents:"+unspents);
    if(unspents!=null){
        for (UnspentOutput unspent : unspents) {
            lastTxHash = unspent.txid;
            txCounter++;
            valueCounter += unspent.amt_satoshi.doubleValue();
        }
        System.out.println("Found cached utxo:"+ lastTxHash +" , txCounter="+txCounter+",valueCounter="+valueCounter);
        
        if(valueCounter >  Config.ppkStandardDataFee + (Config.MAX_MULTISIG_TX_NUM+1) * Config.dustSize){
            return unspents;
        }
        
        System.out.println("The cached utxos not enough for new transaction.");
    }

    
    //调用API服务来获取可用的未花费交易列表
    unspents=new ArrayList<UnspentOutput> ();

    try {
        String result=null;
        JSONObject tempObject=null;
        
        /*
        //检查API服务是否正常更新到最新区块
        Blocks blocks = Blocks.getInstance();
        int block_height=blocks.bitcoinBlock ; // Current block height in the longest chain
        result = CommonHttpUtil.getInstance().getContentFromUrl( "https://chain.api.btc.com/v3/block/" + block_height  );
        tempObject=new JSONObject(result);
        
        //System.out.println("block_height: "+block_height +"\ntempObject="+tempObject);
        if(tempObject.getJSONObject("data").getInt("height")!=block_height){
            throw new IOException("API mismatched the block height:"+block_height);
        }
        
        //API服务正常则继续调用查询未花费交易列表
        result = CommonHttpUtil.getInstance().getContentFromUrl( "https://chain.api.btc.com/v3/address/" + address + "/unspent" );
        */
        String api_url="https://chain.api.btc.com/v3/address/" + address + "/unspent";
        tempObject=fetchBtcAPI(api_url);
        
        Integer total_count=tempObject.getJSONObject("data").getInt("total_count");
        Integer pagesize=tempObject.getJSONObject("data").getInt("pagesize");
        JSONArray utxoArray=tempObject.getJSONObject("data").getJSONArray("list");
        
        if(total_count>pagesize){
          result = CommonHttpUtil.getInstance().getContentFromUrl( "https://chain.api.btc.com/v3/address/" + address + "/unspent?page="+ 
                            Math.round( Math.ceil((double)total_count/(double)pagesize) ) );
          JSONArray lastArray=(new JSONObject(result)).getJSONObject("data").optJSONArray("list");
          if(lastArray!=null) {
	       	  for(int tt=lastArray.length()-1;tt>=0;tt--){
	       		utxoArray.put( lastArray.get(tt) ); //合并第一页和最后一页的交易列表数组
	    	  }
          }
        }
        
        
        
        txCounter=0;
        valueCounter=0L;
        for(int tt=utxoArray.length()-1;tt>=0;tt--){
            JSONObject item_obj=(JSONObject)utxoArray.get(tt);
            
            UnspentOutput tempUnspentObj=new UnspentOutput();
            
            tempUnspentObj.amt_satoshi=BigInteger.valueOf(item_obj.getLong("value"));
            tempUnspentObj.txid=item_obj.getString("tx_hash");
            tempUnspentObj.vout=item_obj.getInt("tx_output_n");
            tempUnspentObj.scriptPubKeyHex="";
            
            //System.out.println("  tempUnspentObj: "+tempUnspentObj.toString());
            
            try {
                JSONObject tempObjectTx=fetchBtcAPI("https://blockchain.info/zh-cn/rawtx/" + tempUnspentObj.txid);
                JSONArray tempArrayOutputs=tempObjectTx.getJSONArray("out");
                JSONObject item_output=(JSONObject)tempArrayOutputs.get(tempUnspentObj.vout);

                tempUnspentObj.scriptPubKeyHex=item_output.getString("script");
            }catch (Exception e1) {
              logger.error(" getUnspents() blockchain.info  : "+e1.toString());
              try {
                  JSONObject tempObjectTx=fetchBtcAPI("https://chain.api.btc.com/v3/tx/" + tempUnspentObj.txid + "?verbose=3");
                  JSONArray tempArrayOutputs=tempObjectTx.getJSONObject("data").getJSONArray("outputs");
                  JSONObject item_output=(JSONObject)tempArrayOutputs.get(tempUnspentObj.vout);

                  tempUnspentObj.scriptPubKeyHex=item_output.getString("script_hex");
              }catch (Exception e2) {
                  logger.error(" getUnspents() api.btc.com: "+e2.toString());
              }
            }
            //System.out.println(">>>>>>>>>>tempUnspentObj["+tt+"]:"+tempUnspentObj.txid+","+tempUnspentObj.amt_satoshi+","+tempUnspentObj.vout+","+tempUnspentObj.scriptPubKeyHex);
            
            if(tempUnspentObj.scriptPubKeyHex.length()>0){
              unspents.add(tempUnspentObj);
              valueCounter += item_obj.getLong("value");
              txCounter ++ ;
            }
            
            if( txCounter>Config.MAX_MULTISIG_TX_NUM+1 
               && (valueCounter > Config.maxFee || valueCounter >  Config.ppkStandardDataFee + (Config.MAX_MULTISIG_TX_NUM+1) * Config.dustSize))  {  //if enough for max ODIN fee 
              break;
            }
        }
    } catch (Exception e) {
      //此API有异常，自动切换到另一个备用API
      logger.error("getUnspents() BTC.com API exception:"+e.toString());
      UnspentList ul = getUnspentsWithoutDustTX(address);
      return ul.unspents;
    }
    
    return unspents;
  }
  /*
  public static List<UnspentOutput> getAllUnspents(String address) {
    List<UnspentOutput> unspents=null;
    int txCounter=0;
    Double valueCounter=0.0;
    
    //调用API服务来获取可用的未花费交易列表
    unspents=new ArrayList<UnspentOutput> ();

    try {
        String result=null;
        JSONObject tempObject=null;

        String api_url="https://chain.api.btc.com/v3/address/" + address + "/unspent";
        if(Config.proxyURL!=null && Config.proxyURL.length()>0)
            api_url=Config.proxyURL+"?url=" + java.net.URLEncoder.encode(api_url);
        result = CommonHttpUtil.getInstance().getContentFromUrl( api_url );
        
        tempObject=new JSONObject(result);
        
        Integer total_count=tempObject.getJSONObject("data").getInt("total_count");
        Integer pagesize=tempObject.getJSONObject("data").getInt("pagesize");
        
        int max_page=(int)(Math.round( Math.ceil((double)total_count/(double)pagesize) ));
        for(int pp=max_page;pp>0;pp--){
            System.out.println("\n pp = "+pp+"\n");
            api_url= "https://chain.api.btc.com/v3/address/" + address + "/unspent?page=" + pp ;
            if(Config.proxyURL!=null && Config.proxyURL.length()>0)
                api_url=Config.proxyURL+"?url=" + java.net.URLEncoder.encode(api_url);

            result = CommonHttpUtil.getInstance().getContentFromUrl( "https://chain.api.btc.com/v3/address/" + address + "/unspent?page=" + pp );
            tempObject=new JSONObject(result);
        
        
            JSONArray tempArray=tempObject.getJSONObject("data").getJSONArray("list");
            
            txCounter=0;
            valueCounter=0.0;
            for(int tt=tempArray.length()-1;tt>=0;tt--){
                JSONObject item_obj=(JSONObject)tempArray.get(tt);
                
                UnspentOutput tempUnspentObj=new UnspentOutput();
                
                tempUnspentObj.amt_satoshi=BigInteger.valueOf(item_obj.getLong("value"));
                tempUnspentObj.txid=item_obj.getString("tx_hash");
                tempUnspentObj.vout=item_obj.getInt("tx_output_n");
                tempUnspentObj.scriptPubKeyHex="";
                
                System.out.println("  tempUnspentObj: "+tempUnspentObj.toString());
                
                try {
                    result = CommonHttpUtil.getInstance().getContentFromUrl( "https://blockchain.info/zh-cn/rawtx/" + tempUnspentObj.txid );
                    JSONObject tempObjectTx=new JSONObject(result);
                    JSONArray tempArrayOutputs=tempObjectTx.getJSONArray("out");
                    JSONObject item_output=(JSONObject)tempArrayOutputs.get(tempUnspentObj.vout);

                    tempUnspentObj.scriptPubKeyHex=item_output.getString("script");
                }catch (Exception e1) {
                  logger.error(" getUnspents() blockchain.info  : "+e1.toString());
                  try {
                      result = CommonHttpUtil.getInstance().getContentFromUrl( "https://chain.api.btc.com/v3/tx/" + tempUnspentObj.txid + "?verbose=3" );
                      //System.out.println("Get https://chain.api.btc.com/v3/tx/" + tempUnspentObj.txid + "?verbose=3\n  result: "+result);
                      JSONObject tempObjectTx=new JSONObject(result);
                      JSONArray tempArrayOutputs=tempObjectTx.getJSONObject("data").getJSONArray("outputs");
                      JSONObject item_output=(JSONObject)tempArrayOutputs.get(tempUnspentObj.vout);

                      tempUnspentObj.scriptPubKeyHex=item_output.getString("script_hex");
                  }catch (Exception e2) {
                      logger.error(" getUnspents() api.btc.com: "+e2.toString());
                  }
                }
                //System.out.println(">>>>>>>>>>tempUnspentObj["+tt+"]:"+tempUnspentObj.txid+","+tempUnspentObj.amt_satoshi+","+tempUnspentObj.vout+","+tempUnspentObj.scriptPubKeyHex);
                
                if(tempUnspentObj.scriptPubKeyHex.length()>0){
                  unspents.add(tempUnspentObj);
                  valueCounter += item_obj.getDouble("value");
                  txCounter ++ ;
                }
            }
        }
    } catch (Exception e) {
      //此API有异常
      logger.error("getAllUnspents() BTC.com API exception:"+e.toString());
      return null;
    }
    
    return unspents;
  }
  */
  public static JSONObject fetchBtcAPI(String api_url) throws Exception {
    String result=null;
    try {
        //System.out.println("\nCall fetchBtcAPI("+api_url+")\n");
        result = CommonHttpUtil.getInstance().getContentFromUrl( api_url );
        //System.out.println("\n result = "+result+"\n");
        return new JSONObject(result);
    }catch (Exception e) {
        //System.out.println("\n Retry by proxy\n");
        if(Config.proxyURL!=null && Config.proxyURL.length()>0)
            api_url=Config.proxyURL+"?url=" + java.net.URLEncoder.encode(api_url);
        result = CommonHttpUtil.getInstance().getContentFromUrl( api_url );
        return new JSONObject(result);
    }
  }
  
  public static UnspentList getValidUnspents(String address,int max_num,boolean need_script_detail) {
    List<UnspentOutput> unspents=null;
    int txCounter=0;
    long valueCounter=0L;
    int total_count=0;
    
    //调用API服务来获取可用的未花费交易列表
    unspents=new ArrayList<UnspentOutput> ();
    String api_url="";
    try {
        String result=null;
        JSONObject tempObject=null;

        api_url="https://chain.api.btc.com/v3/address/" + address + "/unspent";       
        tempObject=fetchBtcAPI(api_url);
        
        total_count=tempObject.getJSONObject("data").getInt("total_count");
        Integer pagesize=tempObject.getJSONObject("data").getInt("pagesize");
        
        int max_page=(int)(Math.round( Math.ceil((double)total_count/(double)pagesize) ));
        for(int pp=max_page;pp>0;pp--){
            //System.out.println("\n pp = "+pp+"\n");
            api_url="https://chain.api.btc.com/v3/address/" + address + "/unspent?page=" + pp ;
            tempObject=fetchBtcAPI(api_url);
        
            JSONArray tempArray=tempObject.getJSONObject("data").getJSONArray("list");
            
            for(int tt=tempArray.length()-1;tt>=0;tt--){
                JSONObject item_obj=(JSONObject)tempArray.get(tt);
                
                UnspentOutput tempUnspentObj=new UnspentOutput();
                
                tempUnspentObj.amt_satoshi=BigInteger.valueOf(item_obj.getLong("value"));
                tempUnspentObj.txid=item_obj.getString("tx_hash");
                tempUnspentObj.vout=item_obj.getInt("tx_output_n");
                tempUnspentObj.scriptPubKeyHex="";

                if(need_script_detail){
                    try {
                        JSONObject tempObjectTx=fetchBtcAPI("https://blockchain.info/zh-cn/rawtx/" + tempUnspentObj.txid);;
                        JSONArray tempArrayOutputs=tempObjectTx.getJSONArray("out");
                        JSONObject item_output=(JSONObject)tempArrayOutputs.get(tempUnspentObj.vout);

                        tempUnspentObj.scriptPubKeyHex=item_output.getString("script");
                    }catch (Exception e1) {
                        try {
                          JSONObject tempObjectTx=fetchBtcAPI("https://chain.api.btc.com/v3/tx/" + tempUnspentObj.txid + "?verbose=3");
                          JSONArray tempArrayOutputs=tempObjectTx.getJSONObject("data").getJSONArray("outputs");
                          JSONObject item_output=(JSONObject)tempArrayOutputs.get(tempUnspentObj.vout);

                          tempUnspentObj.scriptPubKeyHex=item_output.getString("script_hex");
                        }catch (Exception e2) {
                          logger.error(" getUnspents() api.btc.com: "+e2.toString());
                        }
                    }
                    //System.out.println(">>>>>>>>>>tempUnspentObj["+tt+"]:"+tempUnspentObj.txid+","+tempUnspentObj.amt_satoshi+","+tempUnspentObj.vout+","+tempUnspentObj.scriptPubKeyHex);
                }
                
                //System.out.println("  tempUnspentObj: "+tempUnspentObj.toString());
                
                if(!need_script_detail || tempUnspentObj.scriptPubKeyHex.length()>0){
                  unspents.add(tempUnspentObj);
                  valueCounter += item_obj.getLong("value");
                  txCounter ++ ;
                }
                
                if( txCounter>=max_num )  { 
                  return new UnspentList(unspents,txCounter,valueCounter,total_count);
                }
            }
        }
    } catch (Exception e) {
      //此API有异常
      logger.error("getValidUnspents() API("+api_url+" ) exception:"+e.toString());
      return getUnspentsWithoutDustTX(address);
    }
    
    return new UnspentList(unspents,txCounter,valueCounter,total_count);
  }

  //备用的获取指定地址未花费交易的方法，不包含多重签名类型的输出
  public static UnspentList getUnspentsWithoutDustTX(String address) {
    List<UnspentOutput> unspents = new ArrayList<UnspentOutput> ();
    int txCounter=0;
    long valueCounter=0L;
    try {
        String result = CommonHttpUtil.getInstance().getContentFromUrl( "https://blockchain.info/unspent?active="+address );
        JSONObject tempResultObject=new JSONObject(result);
        JSONArray tempArray=tempResultObject.getJSONArray("unspent_outputs");
        ArrayList<HashMap<String, Object>> item_set_array = new ArrayList<HashMap<String, Object>>();
        for(int tt=0;tt<tempArray.length();tt++){
            JSONObject item_obj=(JSONObject)tempArray.get(tt);
            
            UnspentOutput tempUnspentObj=new UnspentOutput();
            tempUnspentObj.amt_satoshi=BigInteger.valueOf(item_obj.getLong("value"));
            tempUnspentObj.txid=item_obj.getString("tx_hash_big_endian");
            tempUnspentObj.vout=item_obj.getInt("tx_output_n");
            //tempUnspentObj.type=item_obj.getString("");
            //tempUnspentObj.confirmations=item_obj.getInt("confirmations");

            //tempUnspentObj.scriptPubKeyAsm="Invalid";
            tempUnspentObj.scriptPubKeyHex=item_obj.getString("script");

            unspents.add(tempUnspentObj);
            valueCounter += item_obj.getLong("value");
            txCounter ++ ;
        }
    } catch (Exception e) {
        logger.error(" getUnspentsWithoutDustTX() "+e.toString());
    }
    return new UnspentList(unspents,txCounter,valueCounter,txCounter);
  }

  public static TransactionInfo getTransaction(String txHash) {
    String result = CommonHttpUtil.getInstance().getContentFromUrl(transactionAddress(txHash));
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      TransactionInfo transactionInfo = objectMapper.readValue(result, new TypeReference<TransactionInfo>() {});
      return transactionInfo;
    } catch (Exception e) {
      logger.error("getTransaction() "+e.toString());
      return null;
    }
  }
  
  public static JSONObject getMultiBTCBalances(String address_list) {
    //if(!Config.useDustTX){
    //  return getBTCBalanceWithoutDustTX(address);
    //  https://blockchain.info/multiaddr?active=$address|$address&n=1
    //}
    
    try {
      String api_url="https://chain.api.btc.com/v3/address/"+address_list;
      if(Config.proxyURL!=null && Config.proxyURL.length()>0)
          api_url=Config.proxyURL+"?url=" + java.net.URLEncoder.encode(api_url);
      String result = CommonHttpUtil.getInstance().getContentFromUrl( api_url );
      //System.out.println("api_url="+api_url+"\nresult="+result);
      JSONObject  obj_result=new JSONObject();
      
      JSONObject tempResultObject=new JSONObject(result);
      JSONArray temp_list=tempResultObject.getJSONArray("data");
      for(int kk=0;kk<temp_list.length();kk++){
          if( ! temp_list.isNull(kk) ){
              JSONObject tmp_info=temp_list.getJSONObject(kk);
              //System.out.println("address="+tmp_info.getString("address")+",balance="+tmp_info.getInt("balance"));
              obj_result.put(tmp_info.getString("address"),tmp_info);
          }
      }
      
      return  obj_result;
    } catch (Exception e) {
      e.printStackTrace();
      //return getBTCBalanceWithoutDustTX(address);
      return null;
    }
  }

  public static BigInteger getBTCBalance(String address) {
    if(!Config.useDustTX){
      return getBTCBalanceWithoutDustTX(address);
    }
    
    try {
      String api_url="https://chain.api.btc.com/v3/address/"+address;
      if(Config.proxyURL!=null && Config.proxyURL.length()>0)
          api_url=Config.proxyURL+"?url=" + java.net.URLEncoder.encode(api_url);
      String result = CommonHttpUtil.getInstance().getContentFromUrl( api_url );
        
      JSONObject tempResultObject=new JSONObject(result);
      tempResultObject=tempResultObject.getJSONObject("data");

      return  BigInteger.valueOf(tempResultObject.getLong("balance"));
    } catch (Exception e) {
      //return getBTCBalanceWithoutDustTX(address);
      return null;
    }
  }

  public static BigInteger getBTCBalanceWithoutDustTX(String address) {
    String result = CommonHttpUtil.getInstance().getContentFromUrl( "https://blockchain.info/zh-cn/address/"+address+"?format=json&limit=0" );
    try {
      JSONObject addressInfo=new JSONObject(result);
      return BigInteger.valueOf(addressInfo.getLong("final_balance"));
    } catch (Exception e) {
      logger.error("getBTCBalanceWithoutDustTX() "+e.toString());
      return BigInteger.ZERO;
    }
  }
 
  public static BigInteger getBalance(String address, String asset) {
    Database db = Database.getInstance();
    Blocks blocks = Blocks.getInstance();
    if (asset.equals("BTC")) {
      /*
      BigInteger totalBalance = BigInteger.ZERO;
      LinkedList<TransactionOutput> unspentOutputs = blocks.wallet.calculateAllSpendCandidates(true);
      Set<Transaction> txs = blocks.wallet.getTransactions(true);
      for (TransactionOutput out : unspentOutputs) {
        Script script = out.getScriptPubKey();
        if (script.getToAddress(blocks.params).toString().equals(address) && out.isAvailableForSpending()) {
          totalBalance = totalBalance.add(out.getValue());
        }
      }
      return totalBalance;
       */
      return getBTCBalance(address);
    } else {
      ResultSet rs = db.executeQuery("select sum(amount) as amount from balances where address='"+address+"' and asset='"+asset+"';");
      try {
        if (rs.next()) {
          return BigInteger.valueOf(rs.getLong("amount"));
        }
      } catch (SQLException e) {
      }
    }
    return BigInteger.ZERO;
  }
  
  /*
  public static List<String> getAddresses() {
    Blocks blocks = Blocks.getInstance();
    List<ECKey> keys = blocks.wallet.getImportedKeys();
    List<String> addresses = new ArrayList<String>();
    for(ECKey key : keys) {
      addresses.add(key.toAddress(blocks.params).toString());
    }
    return addresses;
  }
  */
  public static List<String> getAddresses() { //2019-01-15
    Blocks blocks = Blocks.getInstance();
    return blocks.getAddresses();
  }

  public static Integer getAssetId(String asset) {
    if (asset.equals("BTC")) {
      return 0;
    } else {
      return null;
    }
  }
  public static String getAssetName(Integer assetId) {
    if (assetId==0) {
      return "BTC";
    } else {
      return null;
    }
  }

  public static byte[] toByteArray(List<Byte> in) {
    final int n = in.size();
    byte ret[] = new byte[n];
    for (int i = 0; i < n; i++) {
      ret[i] = in.get(i);
    }
    return ret;
  }  
  public static List<Byte> toByteArrayList(byte[] in) {
    List<Byte> arrayList = new ArrayList<Byte>();

    for (byte b : in) {
      arrayList.add(b);
    }
    return arrayList;
  }  

  public static String getMinVersion() {
    if(mMinVersion!=null && mMinVersion.length()>0)
      return mMinVersion;
    
    mMinVersion = CommonHttpUtil.getInstance().getContentFromUrl(Config.minVersionPage);
    if( mMinVersion == null || mMinVersion.trim().length()==0 ) {
      mMinVersion="0.0";
    }else
      mMinVersion = mMinVersion.trim();
    
    return mMinVersion;
  }
  
  public static Integer getMinMajorVersion() {
    String minVersion = getMinVersion();
    String[] pieces = minVersion.split("\\.");
    return Integer.parseInt(pieces[0].trim());
  }
  
  public static Integer getMinMinorVersion() {
    String minVersion = getMinVersion();
    String[] pieces = minVersion.split("\\.");
    return Integer.parseInt(pieces[1].trim());
  }
  
  public static String getBlockHash(Integer blockIndex) {
    Database db = Database.getInstance();
    ResultSet rs = db.executeQuery("select block_hash from blocks where block_index='"+blockIndex.toString()+"';");
    try {
      if(rs.next()) {
        return rs.getString("block_hash");
      }
    } catch (SQLException e) {
    }  
    return null;
  }
  
  public static Integer getLastBlockTimestamp() {
    Database db = Database.getInstance();
    ResultSet rs = db.executeQuery("select * from blocks order by block_index desc limit 1;");
    try {
      while(rs.next()) {
        return rs.getInt("block_time");
      }
    } catch (SQLException e) {
    }  
    return 0;
  }
  
  //the_date format is YYYY-MM-DD
  //timeZone=null for local time,  timeZone="GMT+0:00" for UTC
  public static Integer getDateTimestamp(String the_date, String timeZone){
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    if(timeZone!=null){
      TimeZone tz = TimeZone.getTimeZone(timeZone); 
      formatter.setTimeZone(tz);
    }
    
    ParsePosition pos = new ParsePosition(0);
    Date strtodate = formatter.parse(the_date+" 00:00:00", pos);
    
    Long tsInSecond=strtodate.getTime()/1000L;
    
    return Integer.parseInt(tsInSecond.toString());
  }
  
  public static String getLeftTimeDescStr(Integer expireUTC){
    Calendar nowtime=Calendar.getInstance();
    
    Long leftSeconds=expireUTC-nowtime.getTimeInMillis()/1000L;
    
    if(leftSeconds<0L)
      return null;
    
    String   aboutLeftTimeDesc="";
    Integer  aboutDays=new Double(java.lang.Math.floor(leftSeconds.doubleValue()/new Double(60*60*24).doubleValue())).intValue();
    if(aboutDays>0)
      aboutLeftTimeDesc=aboutDays.toString() + " " + Language.getLangLabel("days");
    
    leftSeconds=leftSeconds%(60*60*24);
    Integer  aboutHours=new Double( java.lang.Math.floor(leftSeconds.doubleValue() /new Double(60*60).doubleValue())).intValue();
    if(aboutHours>0)
      aboutLeftTimeDesc=aboutLeftTimeDesc+" "+aboutHours.toString() + " " + Language.getLangLabel("hours");
    else {
      leftSeconds=leftSeconds%(60*60);
      
      Integer  aboutMins=new Double( java.lang.Math.floor(leftSeconds.doubleValue() /new Double(60).doubleValue())).intValue();
      if(aboutMins>0)
        aboutLeftTimeDesc=aboutLeftTimeDesc+" "+aboutMins.toString() + " " + Language.getLangLabel("minutes");
    }
    
    if(aboutLeftTimeDesc.equals(""))
      aboutLeftTimeDesc=" < 1 "+Language.getLangLabel("minute");
    
    return aboutLeftTimeDesc;//+" (DEBUG: now="+nowtime.getTimeInMillis()+"  dest="+strtodate.getTime()+")  ";
  }
  
  //Compress string
  //type: Config.DATA_BIN_DEFLATE,Config.DATA_BIN_GZIP
  public static byte[] compress(byte input[],Byte type) throws Exception {
    if (input == null || input.length == 0) {
      return input;
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    
    if(Config.DATA_BIN_GZIP==type){
        GZIPOutputStream gzip = new GZIPOutputStream(out);
        gzip.write(input);
        gzip.close();
    }else if(Config.DATA_BIN_DEFLATE==type){
        Deflater compressor = new Deflater(9);
        try {
            compressor.setInput(input);
            compressor.finish();
            final byte[] buf = new byte[2048];
            while (!compressor.finished()) {
                int count = compressor.deflate(buf);
                out.write(buf, 0, count);
            }
        } finally {
            compressor.end();
        }
    }
    return out.toByteArray();
  }

  //Uncompress string
  //type: Config.DATA_BIN_DEFLATE,Config.DATA_BIN_GZIP
  public static byte[] uncompress(byte[] input,Byte type) throws Exception {
    if (input == null || input.length == 0) {
      return input;
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    
    if(Config.DATA_BIN_GZIP==type){
        ByteArrayInputStream in = new ByteArrayInputStream(input);
        GZIPInputStream gunzip = new GZIPInputStream(in);
        byte[] buffer = new byte[256];
        int n;
        while ((n = gunzip.read(buffer)) >= 0) {
          out.write(buffer, 0, n);
        }
    }else if(Config.DATA_BIN_DEFLATE==type){
        Inflater decompressor = new Inflater();
        try {
            decompressor.setInput(input);
            final byte[] buf = new byte[2048];
            while (!decompressor.finished()) {
                int count = decompressor.inflate(buf);
                out.write(buf, 0, count);
            }
        } finally {
            decompressor.end();
        }
    }
    System.out.println("uncompress result"+ new String(out.toByteArray()));
    return out.toByteArray();
  }
  
  public static boolean exportTextToFile(String text, String fileName) {
    try {
      String file_path = fileName.substring(0,fileName.lastIndexOf('/'));
      File fp = new File(file_path);    
      // 创建目录    
      if (!fp.exists()) {    
          fp.mkdirs();// 目录不存在的情况下，创建目录。    
      } 
      
      FileWriter fw = new FileWriter(fileName);  
      fw.write(text,0,text.length());  
      fw.flush();  
      return true;
    } catch (Exception e) {
      logger.info(e.toString());
      return false;
    }
  }
  
  public static String readTextFile(String fileName){  
    return readTextFile(fileName,Config.BINARY_DATA_CHARSET);
  } 
    
  public static String readTextFile(String fileName,String encode){  
    try {
        InputStreamReader read = new InputStreamReader (new FileInputStream(fileName),encode);
        BufferedReader reader=new BufferedReader(read);
        String str="";
        String line;
        while ((line = reader.readLine()) != null) {
            str+=line;
        }
        reader.close();
        read.close();
        return str;
    }catch(Exception e){
      logger.error( "readTextFile() "+e.toString());
      return null;
    }
  }
  
  public static JSONObject getRSAKeys(String address,boolean auto_generate,boolean auto_save) throws Exception{  
    String  privateKeyFilename="resources/db/keys/"+address+".json";
          
    if (!(new File(privateKeyFilename)).exists() ){
      if(!auto_generate)
        return null;
      
      //init a rsa key file for current address
      JSONObject keyMap = RSACoder.initKey();  
      //logger.info("Generated new keys: " + keyMap.toString());  
    
      if(auto_save){
        if(!exportTextToFile(keyMap.toString(),privateKeyFilename)){
          logger.error("Failed to save generated RSA keys to "+privateKeyFilename);
          return null;
        }
      }else{
        return keyMap;
      }
    }
    
    String  tmpKeyStr=Util.readTextFile(privateKeyFilename);
    if(tmpKeyStr==null){
      logger.error("Failed to get RSA keys from "+privateKeyFilename);
      return null;          
    }
    
    JSONObject keyMap=new JSONObject(tmpKeyStr);
    
    return keyMap;
  }
  
   /*
   * Convert byte[] to hex string.。   
   * @param src byte[] data   
   * @return hex string   
   */      
  public static String bytesToHexString(byte[] src){   
      StringBuilder stringBuilder = new StringBuilder("");   
      if (src == null || src.length <= 0) {   
          return null;   
      }   
      for (int i = 0; i < src.length; i++) {   
          int v = src[i] & 0xFF;   
          String hv = Integer.toHexString(v);   
          if (hv.length() < 2) {   
              stringBuilder.append(0);   
          }   
          stringBuilder.append(hv);   
      }   
      return stringBuilder.toString();   
  }   
  /**  
   * Convert hex string to byte[]  
   * @param hexString the hex string  
   * @return byte[]  
   */  
  public static byte[] hexStringToBytes(String hexString) {   
    try{
      if (hexString == null) {   
          return null;   
      }
      if (hexString.equals("")) {   
          return new byte[0];   
      }
      
      if(isHexNumberRex(hexString)){
          hexString = hexString.toUpperCase();   
          int length = hexString.length() / 2;   
          char[] hexChars = hexString.toCharArray();   
          byte[] d = new byte[length];   
          for (int i = 0; i < length; i++) {   
              int pos = i * 2;   
              d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));   
          }   
          return d;   
      }else{
          return hexString.getBytes(Config.BINARY_DATA_CHARSET);
      }
    }catch(Exception e){
      return null;
    }
  }   
  
  //判断是16进制HEX格式的字符窜
  //判断是16进制HEX格式的字符窜
  //判断是16进制HEX格式的字符窜
  public static boolean isHexNumberRex(String str){
    String validate = "(?i)[0-9a-f]+";
    return str.matches(validate);
  }
    
  
  //生成指定字节长度的HEX文本，如果长度不足则尾部追加指定字节值补足
  public static String generateSegmentHex(byte[] data,int segment_len,byte ch_append){
    List<Byte> dataArrayList = new ArrayList<Byte>();
    
    try {
      dataArrayList = toByteArrayList(data);
      
      for(int kk=dataArrayList.size();kk<segment_len;kk++){
        dataArrayList.add(ch_append); //不够指定字节长度的需尾部追加指定字节值补足
      }
    } catch (Exception e) {
      return null;
    }
    
    data = toByteArray(dataArrayList);

    return  bytesToHexString(data);
  } 
  
  /**  
   * Convert char to byte  
   * @param c char  
   * @return byte  
   */  
   private static byte charToByte(char c) {   
      return (byte) "0123456789ABCDEF".indexOf(c);   
  }  
  
  //生成有效的公钥数据块来嵌入指定的数据内容
  public static byte[] generateValidPubkey(String data_str){
    System.out.println("Util.generateValidPubkey() data_str="+data_str);
    byte[] data = null;
    
    try {
      data = data_str.getBytes(Config.BINARY_DATA_CHARSET);
      
      return generateValidPubkey(data);
    } catch (Exception e) {
      return null;
    }
  }
  
  public static byte[] generateValidPubkey(byte[] data){
    if(data.length>Config.PPK_PUBKEY_EMBED_DATA_MAX_LENGTH){
      System.out.println("The data segment length should be less than " + Config.PPK_PUBKEY_EMBED_DATA_MAX_LENGTH);
      return null;
    }
    
    List<Byte> dataArrayList = new ArrayList<Byte>();
    
    try {
      dataArrayList = Util.toByteArrayList(data);
      
      for(int kk=dataArrayList.size();kk<Config.PPK_PUBKEY_EMBED_DATA_MAX_LENGTH;kk++){
        dataArrayList.add((byte)0x20); //追加空格
      }
    } catch (Exception e) {
      return null;
    }

    dataArrayList.add(0,(byte) data.length ); 
    dataArrayList.add(0, Config.PPK_PUBKEY_TYPE_FLAG); 
    
    while(dataArrayList.size()<Config.PPK_PUBKEY_LENGTH)
      dataArrayList.add((byte)0x20);
    
    data = Util.toByteArray(dataArrayList);

    return  data;
    /*
    for(int bb=0;bb<256;bb++){
      try{
        data[data.length-1]=(byte) bb;
        ECKey tmp_key_more=ECKey.fromPublicOnly(data);
        System.out.println("Try["+bb+"]:"+(char)bb+" ok : "+tmp_key_more.toString());
        return  data;
      } catch (Exception e) {
        //System.out.println("Meet pubkey error:"+e.toString());
      }
    }
    return null;
    */
  } 
  
  //check the status of IPFS service
  public static boolean isIpfsRuning(){
    if(mIpfsRunning!=null)
      return mIpfsRunning;
    
    try{
      IPFS ipfs = new IPFS(Config.IPFS_API_ADDRESS);
      mIpfsRunning = true;
    }catch(Exception e){
      System.out.println("Failed to connnect IPFS service:"+e.toString());
      //System.exit(-1);
      mIpfsRunning = false;
    }
    
    return mIpfsRunning;
  }
  
  //Upload data to IPFS and return the HASH uri
  public static String uploadToIpfs(byte[] data){
    try{
      IPFS ipfs = new IPFS(Config.IPFS_API_ADDRESS);
      //ipfs.refs.local();
      NamedStreamable.ByteArrayWrapper file = new NamedStreamable.ByteArrayWrapper("ppkpub-odin.data", data);
      String addResult = ipfs.ppkAdd(file);
      System.out.println("Util.uploadToIpfs() addResult:"+addResult);
      JSONObject tmp_obj=new JSONObject(addResult);
      String hash=tmp_obj.optString("Hash");
      if(hash==null)
        return null;
      
      return "ipfs:"+hash;      
    }catch(Exception e){
      System.out.println("Util.uploadToIpfs() error:"+e.toString());
      return null;
    }
  }
  
  public static String getIpfsData(String ipfs_hash_address){
    try{
      IPFS ipfs = new IPFS(Config.IPFS_API_ADDRESS);
      Multihash filePointer = Multihash.fromBase58(ipfs_hash_address);
      byte[] fileContents = ipfs.cat(filePointer);
      return new String(fileContents);
    }catch(Exception e){
      System.out.println("Util.getIpfsData() error:"+e.toString());
      
      String tmp_url=Config.IPFS_DOWNLOAD_URL+ipfs_hash_address;
      System.out.println("Using IPFS Proxy to fetch:"+ tmp_url);
      
      return CommonHttpUtil.getInstance().getContentFromUrl(tmp_url);
    }
  }
  
  //Upload data to BtmFS and return the uri
  public static String uploadToBtmfs(byte[] data){
      String tmp_url=Config.BTMFS_PROXY_URL+"?hex=" + bytesToHexString( data );
      System.out.println("Using BTMFS Proxy to upload :"+ tmp_url);
      
      try{
        String str_resp_json=CommonHttpUtil.getInstance().getContentFromUrl(tmp_url);
        System.out.println("str_resp_json ="+ str_resp_json);
        
        JSONObject obj_ap_resp=new JSONObject(str_resp_json);
        if(obj_ap_resp==null)
          return null;
        
        String resp_status=obj_ap_resp.optString("status",null);
        if( "success".equalsIgnoreCase(resp_status) )
            return obj_ap_resp.optString("uri",null);
        else
            return null;
      }catch(Exception e){
        logger.error("Util.uploadToBtmfs() error:"+e.toString());
        return null;
      }
  }
  
  public static String getBtmfsData(String btmfs_uri){
      String tmp_url=Config.BTMFS_PROXY_URL+"?uri=" + java.net.URLEncoder.encode(btmfs_uri);
      System.out.println("Using BTMFS Proxy to fetch:"+ tmp_url);
      
      return CommonHttpUtil.getInstance().getContentFromUrl(tmp_url);
  }
  
  //Upload data to Dat and return the uri
  public static String uploadToDat(byte[] data,String related_subid){
      String tmp_url=Config.DAT_UPLOAD_URL;
      
      try{
        System.out.println("Using Dat Proxy to upload :"+ tmp_url);
        
        String tmp_post_content="subid="+ java.net.URLEncoder.encode(related_subid)+"&hex=" + bytesToHexString( data );
        String str_resp_json=CommonHttpUtil.getInstance().sendPostContent(tmp_url,tmp_post_content,"application/x-www-form-urlencoded");
        System.out.println("str_resp_json="+ str_resp_json);
        
        JSONObject obj_ap_resp=new JSONObject(str_resp_json);
        if(obj_ap_resp==null)
          return null;
        
        String resp_status=obj_ap_resp.optString("status",null);
        if( "success".equalsIgnoreCase(resp_status) )
            return obj_ap_resp.optString("uri",null);
        else
            return null;
      }catch(Exception e){
        logger.error("Util.uploadToDat() error:"+e.toString());
        return null;
      }
  }
  
  public static String getDatData(String dat_uri){
      String dat_hash=dat_uri.substring("dat://".length());
      
      String tmp_url=null;
      String tmp_page_result=null;
      for(int kk=0;kk<Config.DAT_DOWNLOAD_URL_LIST.length;kk++){
          tmp_url=Config.DAT_DOWNLOAD_URL_LIST[kk]+dat_hash;
          System.out.println("Using Dat Proxy to fetch:"+ tmp_url);
          
          tmp_page_result=CommonHttpUtil.getInstance().getContentFromUrl(tmp_url);
          if(tmp_page_result!=null && tmp_page_result.length()>0){
              return tmp_page_result;
          }
      }
      return null;
  }
  
  //Upload data to the AP (such as btmfs , ipfs, etc )
  public static String uploadToAP(String ap_type,byte[] data,String related_subid){
      if(ap_type.equalsIgnoreCase("ipfs")){
        return uploadToIpfs(data);
      }else if(ap_type.equalsIgnoreCase("dat")){
        return uploadToDat(data,related_subid);
      }else if(ap_type.equalsIgnoreCase("btmfs")){
        return uploadToBtmfs(data);
      }else{
        return null;
      }
  }

  public static String  fetchURI(String uri){
    try{
      String[] uri_chunks=uri.split(":");
      if(uri_chunks.length<2){
        logger.error("Util.fetchURI() meet invalid uri:"+uri);
        return null;
      }
      
      if(uri_chunks[0].equalsIgnoreCase("ipfs")){
        return getIpfsData(uri_chunks[1]);
      }else if(uri_chunks[0].equalsIgnoreCase("dat")){
        return getDatData(uri);
      }else if(uri_chunks[0].equalsIgnoreCase("btmfs")){
        return getBtmfsData(uri);
      }else if(uri_chunks[0].equalsIgnoreCase("ppk")){
        JSONObject obj_ap_resp=PPkURI.fetchPPkURI(uri);
        if(obj_ap_resp==null)
          return null;
        
        return obj_ap_resp.optString(Config.JSON_KEY_ORIGINAL_RESP,"ERROR:Invalid PTTP data!");
      }else if(uri_chunks[0].equalsIgnoreCase("data")){
        int from=uri_chunks[1].indexOf(",");
        if(from>=0){
          return uri_chunks[1].substring(from+1,uri_chunks[1].length());
        } else
          return uri_chunks[1];
      }else{
        return CommonHttpUtil.getInstance().getContentFromUrl(uri) ;
      }
    }catch(Exception e){
      logger.error("Util.fetchURI("+uri+") error:"+e.toString());
    }
    return null;
  }
  
  /**
   * 对图片字节数组进行Base64编码处理生成Data URL
   * @param  img_type ：图片的类型，如 image/jpeg 
   * @param  img_bytes ： 图片的字节数组
   * @return Data URL字符串
   */
   
  public static String imageToBase64DataURL(String img_type, byte[] img_bytes) {
    return "data:"+img_type+";base64,"+Base64.getEncoder().encodeToString( img_bytes );
  }
  
  
  /**
     * 匹配是否为数字
     * @param str 可能为中文，也可能是-19162431.1254，不使用BigDecimal的话，变成-1.91624311254E7
     * @return
     * @author yutao
     * @date 2016年11月14日下午7:41:22
     */
  public static boolean isNumeric(String str) {
      String bigStr;
      try {
          bigStr = new BigDecimal(str).toString();
      } catch (Exception e) {
          return false;//异常 说明包含非数字。
      }
      return true;
  }


}

class AddressInfo {
  public Double result;
}

class TransactionInfo {
  public Data data;

  public static class Data {
    public Integer confirmations;
  }
}

class UnspentOutputs {
  public List<UnspentOutput> result;
}
