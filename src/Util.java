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

  public static String getPage(String urlString) {
    return getPage(urlString, 1);

  }

  public static String getPage(String urlString, int retries) {
    try {
      logger.info("Getting URL: "+urlString);
      doTrustCertificates();
      URL url = new URL(urlString);
      HttpURLConnection connection = null;
      connection = (HttpURLConnection)url.openConnection();
      connection.setUseCaches(false);
      connection.addRequestProperty("User-Agent", Config.appName+" "+Config.version); 
      connection.setRequestMethod("GET");
      connection.setDoOutput(true);
      connection.setReadTimeout(10000);
      connection.connect();

      BufferedReader rd  = new BufferedReader(new InputStreamReader(connection.getInputStream()));
      StringBuilder sb = new StringBuilder();
      String line;
      
      while ((line = rd.readLine()) != null)
      {
        sb.append(line + '\n');
      }
      //System.out.println (sb.toString());

      return sb.toString();
    } catch (Exception e) {
      logger.error("Fetch URL error: "+e.toString());
    }
    return "";
    /*
    URL url;
    String text = null;
    try {
      doTrustCertificates();
      url = new URL(urlString);
      URLConnection urlc = url.openConnection();
      urlc.setRequestProperty("User-Agent", "PPkTool "+Config.version);
      urlc.setDoOutput(false);
      urlc.connect();

      BufferedInputStream buffer = new BufferedInputStream(urlc.getInputStream());

      StringBuilder builder = new StringBuilder();

      int byteRead;

      while ((byteRead = buffer.read()) != -1) {
        builder.append((char) byteRead);
      }

      buffer.close();

      text=builder.toString();

    } catch (Exception e) {
      if (retries != 0) {
        return getPage(url_string, retries-1);  
      } else {
        logger.error(e.toString());
      }
    }
    return text;
     */
  }  

  public static void doTrustCertificates() throws Exception {
    TrustManager[] trustAllCerts = new TrustManager[]{
        new X509TrustManager() {
          public java.security.cert.X509Certificate[] getAcceptedIssuers()
          {
            return null;
          }
          public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType)
          {
          }
          public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType)
          {
          }
        }
    };
    try 
    {
      SSLContext sc = SSLContext.getInstance("SSL");
      sc.init(null, trustAllCerts, new java.security.SecureRandom());
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
    } 
    catch (Exception e) 
    {
      System.out.println(e);
    }
  }  

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
    String formattedDate = sdf.format(date);
    return formattedDate;
  }

  public static String timeFormat(Integer timestamp) {
    Date date = new Date(timestamp*1000L); // *1000 is to convert seconds to milliseconds
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); // the format of your date
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

  public static void debit(String address, String asset, BigInteger amount, String callingFunction, String event, Integer blockIndex) {
    Database db = Database.getInstance();
    if (hasBalance(address, asset)) {
      BigInteger existingAmount = getBalance(address,asset);
      BigInteger newAmount = existingAmount.subtract(amount);
      if (newAmount.compareTo(BigInteger.ZERO)>=0) {
        db.executeUpdate("update balances set amount='"+newAmount.toString()+"' where address='"+address+"' and asset='"+asset+"';");
        db.executeUpdate("insert into debits(address, asset, amount, calling_function, event, block_index) values('"+address+"','"+asset+"','"+amount.toString()+"', '"+callingFunction+"', '"+event+"', '"+blockIndex.toString()+"');");
      }
    }
  }

  public static void credit(String address, String asset, BigInteger amount, String callingFunction, String event, Integer blockIndex) {
    Database db = Database.getInstance();
    if (hasBalance(address, asset)) {
      BigInteger existingAmount = getBalance(address,asset);
      BigInteger newAmount = existingAmount.add(amount);
      db.executeUpdate("update balances set amount='"+newAmount.toString()+"' where address='"+address+"' and asset='"+asset+"';");
    } else {
      db.executeUpdate("insert into balances(address, asset, amount) values('"+address+"','"+asset+"','"+amount.toString()+"');");        
    }
    db.executeUpdate("insert into credits(address, asset, amount, calling_function, event, block_index) values('"+address+"','"+asset+"','"+amount.toString()+"', '"+callingFunction+"', '"+event+"', '"+blockIndex.toString()+"');");
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
    //return getUnspents2(address);
    
    String result = getPage( "https://bitcoin.toshi.io/api/v0/addresses/"+address+"/unspent_outputs" );
    List<UnspentOutput> unspents = new ArrayList<UnspentOutput> ();
    try {
        JSONArray tempArray=new JSONArray(result);
        
        for(int tt=0;tt<tempArray.length();tt++){
            JSONObject item_obj=(JSONObject)tempArray.get(tt);
            
            //check the script is valid
            String[] chunks=item_obj.getString("script").split(" ");
            boolean is_script_valid=true;
            for(String chunk : chunks){ 
              if( ( chunk.length()==65*2 && !chunk.startsWith("04") )
                 || ( chunk.length()==33*2 && !chunk.startsWith("02") && !chunk.startsWith("03")  ) 
                ) 
                is_script_valid=false;
            }
            
            if(is_script_valid){
              UnspentOutput tempUnspentObj=new UnspentOutput();
              
              tempUnspentObj.amount=item_obj.getDouble("amount")/Config.btc_unit;
              tempUnspentObj.txid=item_obj.getString("transaction_hash");
              tempUnspentObj.vout=item_obj.getInt("output_index");

              tempUnspentObj.scriptPubKeyHex=item_obj.getString("script_hex");

              unspents.add(tempUnspentObj);
            }else{
              logger.error("Ignore a invalid transction:"+item_obj.getString("script"));
            }
        }
    } catch (Exception e) {
      logger.error(e.toString());
      return getUnspents2(address);
    }
    return unspents;
   
  }
  */
  public static List<UnspentOutput> getUnspents(String address) {
    String result = getPage( "https://blockchain.info/unspent?active="+address );
    List<UnspentOutput> unspents = new ArrayList<UnspentOutput> ();
    try {
            JSONObject tempResultObject=new JSONObject(result);
            JSONArray tempArray=tempResultObject.getJSONArray("unspent_outputs");
            ArrayList<HashMap<String, Object>> item_set_array = new ArrayList<HashMap<String, Object>>();
            for(int tt=0;tt<tempArray.length();tt++){
                JSONObject item_obj=(JSONObject)tempArray.get(tt);
                
                UnspentOutput tempUnspentObj=new UnspentOutput();
                tempUnspentObj.amount=item_obj.getDouble("value")/Config.btc_unit;
                tempUnspentObj.txid=item_obj.getString("tx_hash_big_endian");
                tempUnspentObj.vout=item_obj.getInt("tx_output_n");
                //tempUnspentObj.type=item_obj.getString("");
                //tempUnspentObj.confirmations=item_obj.getInt("confirmations");

                //tempUnspentObj.scriptPubKeyAsm="Invalid";
                tempUnspentObj.scriptPubKeyHex=item_obj.getString("script");

                unspents.add(tempUnspentObj);
            }
    } catch (Exception e) {
      logger.error(e.toString());
      return getUnspents2(address);
    }
    return unspents;
  }
  
  public static List<UnspentOutput> getUnspents2(String address) {
    String result = getPage( "http://blockmeta.com/api/v1/address/unspent/"+address );
    List<UnspentOutput> unspents = new ArrayList<UnspentOutput> ();
    try {
            JSONObject tempResultObject=new JSONObject(result);
            JSONArray tempArray=tempResultObject.getJSONArray("data");
            tempResultObject=(JSONObject)tempArray.get(0);
            tempArray=tempResultObject.getJSONArray("unspent_outputs");

            for(int tt=0;tt<tempArray.length();tt++){
                JSONObject item_obj=(JSONObject)tempArray.get(tt);
                
                UnspentOutput tempUnspentObj=new UnspentOutput();
                tempUnspentObj.amount=item_obj.getDouble("value");
                tempUnspentObj.txid=item_obj.getString("tx_hash");
                tempUnspentObj.vout=item_obj.getInt("tx_output_n");

                tempUnspentObj.scriptPubKeyHex=item_obj.getString("script");

                unspents.add(tempUnspentObj);
            }
    } catch (Exception e) {
      logger.error(e.toString());
    }
    return unspents;
  }


  public static TransactionInfo getTransaction(String txHash) {
    String result = getPage(transactionAddress(txHash));
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      TransactionInfo transactionInfo = objectMapper.readValue(result, new TypeReference<TransactionInfo>() {});
      return transactionInfo;
    } catch (Exception e) {
      logger.error(e.toString());
      return null;
    }
  }

  public static BigInteger getBTCBalance(String address) {
    String result = getPage( "http://blockmeta.com/api/v1/address/info/"+address);
    try {
      JSONObject tempResultObject=new JSONObject(result);
      JSONArray tempArray=tempResultObject.getJSONArray("data");
      tempResultObject=(JSONObject)tempArray.get(0);
      return  BigDecimal.valueOf(tempResultObject.getDouble("balance")*Config.btc_unit).toBigInteger();
    } catch (Exception e) {
      return getBTCBalance2(address);
    }
  }
  /*
  public static BigInteger getBTCBalance(String address) {
    String result = getPage( "https://bitcoin.toshi.io/api/v0/addresses/"+address);
    try {
      JSONObject addressInfo=new JSONObject(result);
      return BigInteger.valueOf(addressInfo.getLong("balance"));
    } catch (Exception e) {
      return getBTCBalance2(address);
    }
  }
  */
  public static BigInteger getBTCBalance2(String address) {
    String result = getPage( "https://blockchain.info/zh-cn/address/"+address+"?format=json&limit=0" );
    try {
      JSONObject addressInfo=new JSONObject(result);
      return BigInteger.valueOf(addressInfo.getLong("final_balance"));
    } catch (Exception e) {
      logger.error(e.toString());
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
  
  public static List<String> getAddresses() {
    Blocks blocks = Blocks.getInstance();
    List<ECKey> keys = blocks.wallet.getImportedKeys();
    List<String> addresses = new ArrayList<String>();
    for(ECKey key : keys) {
      addresses.add(key.toAddress(blocks.params).toString());
    }
    return addresses;
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
    
    mMinVersion = getPage(Config.minVersionPage);
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
  public static String compress(String str) throws Exception {
    if (str == null || str.length() == 0) {
      return str;
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    GZIPOutputStream gzip = new GZIPOutputStream(out);
    gzip.write(str.getBytes(Config.PPK_TEXT_CHARSET));
    gzip.close();
    return out.toString(Config.BINARY_DATA_CHARSET);
  }

  //Uncompress string
  public static String uncompress(String str) throws Exception {
    if (str == null || str.length() == 0) {
      return str;
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayInputStream in = new ByteArrayInputStream(str.getBytes(Config.BINARY_DATA_CHARSET));
    GZIPInputStream gunzip = new GZIPInputStream(in);
    byte[] buffer = new byte[256];
    int n;
    while ((n = gunzip.read(buffer)) >= 0) {
      out.write(buffer, 0, n);
    }
    return out.toString(Config.PPK_TEXT_CHARSET);
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
      logger.error(e.toString());
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
      if (hexString == null || hexString.equals("")) {   
          return null;   
      }   
      hexString = hexString.toUpperCase();   
      int length = hexString.length() / 2;   
      char[] hexChars = hexString.toCharArray();   
      byte[] d = new byte[length];   
      for (int i = 0; i < length; i++) {   
          int pos = i * 2;   
          d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));   
      }   
      return d;   
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
      mIpfsRunning = false;
    }
    
    return mIpfsRunning;
  }
  
  //Upload data to IPFS and return the HASH uri
  public static String uploadToIpfs(String data){
    try{
      IPFS ipfs = new IPFS(Config.IPFS_API_ADDRESS);
      //ipfs.refs.local();
      NamedStreamable.ByteArrayWrapper file = new NamedStreamable.ByteArrayWrapper("ppkpub-odin.data", data.getBytes());
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
      
      String tmp_url=Config.IPFS_PROXY_URL+ipfs_hash_address;
      System.out.println("Using IPFS Proxy to fetch:"+ tmp_url);
      
      return getPage(tmp_url);
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
      }else if(uri_chunks[0].equalsIgnoreCase("ppk")){
        //return fetchPPkURI(uri);
      }else if(uri_chunks[0].equalsIgnoreCase("data")){
        int from=uri_chunks[1].indexOf(",");
        if(from>=0){
          return uri_chunks[1].substring(from+1,uri_chunks[1].length());
        } else
          return uri_chunks[1];
      }else{
        return getPage(uri);
      }
    }catch(Exception e){
      logger.error("Util.fetchURI("+uri+") error:"+e.toString());
    }
    return null;
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
