import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.lang.Math;

public class Config {
  //name
  public static String appName = "PPk";
  public static String defaultLang = "EN";

  public static String log = appName+".log";
  public static String downloadURL = "http://ppkpub.org/javatool/";
  public static String proxyURL = null;
  public static String minVersionPage = downloadURL+"min_version.txt";
  public static String dbDirPrefix = "resources/db/";
  public static String cacheDirPrefix = "resources/cache/";
  public static String newsUrl = downloadURL+"news.html";
  public static String newsUrlCN = downloadURL+"news_cn.html";
  public static String downloadZipUrl = downloadURL+"download.txt"; //Only update package
  public static Integer RPCPort = 44944;
  public static Integer GuiServerPort = 11087;
  public static String RPCUsername = "";
  public static String RPCPassword = "";
  public static String configFile = "resources/ppk.conf";
  public static String walletFile = "resources/db/wallet";
  
  public static boolean debugKey = false;
  
  public static String  jdbcUrl  = "";
  
  //version
  public static Integer majorVersion = 0;
  public static Integer minorVersion = 901;
  public static String version = Integer.toString(majorVersion)+"."+Integer.toString(minorVersion);
  public static Integer majorVersionDB = 1;
  
  public static String defaultSqliteFile = null;  
  
  //bitcoin
  public static boolean useDustTX = true;
  public static Integer dustSize = 1000;
  //public static Integer minOrderMatchBTC = 100000;
  //public static Integer minFee = 10000;
  public static Integer maxUseUTXO = 20;  //输入最多使用的UTXO条目数
  public static Integer maxFee = 99999;  //Avoid sending too much btc than normal fee
  
  public static Integer dataValue = 0;
  public static Integer btc_unit = 100000000;

  //PPk
  public static Integer ppkStandardDataFee = 1000;
  
  public static final long ppkToolCreationTime = 1400561240-1;  //UTC 2014-5-20 04:47:20
  public static Integer firstBlock = 0;  

  public static final int ODIN_PROTOCOL_VER=2; 
  public static final int PTTP_PROTOCOL_VER=2; 
  
  public static final Integer TESTNET_FIRST_BLOCK = 547660;  //Testnet
  public static final String PPK_ODIN_MARK_PUBKEY_HEX_TESTNET="02d173743cd0d94f64d241d82a42c6ca92327c443e489f3842464a4df118d4920a";//1PPkT1hoRbnvSRExCeNoP4s1zr61H12bbg : For testnet
  
  public static final Integer MAINNET_FIRST_BLOCK = 426896;  //Mainnet
  public static final String PPK_ODIN_MARK_PUBKEY_HEX_MAINNET="0320a0de360cc2ae8672db7d557086a4e7c8eca062c0a5a4ba9922dee0aacf3e12";//1PPkPubRnK2ry9PPVW7HJiukqbSnWzXkbi : For Mainnet
  
  public static String PPK_ODIN_MARK_PUBKEY_HEX=null;
  
  public static final byte PPK_PUBKEY_TYPE_FLAG=(byte)3;  //ODIN协议承载消息内容使用的公钥类型前缀取值
  public static final byte PPK_PUBKEY_LENGTH=33;  //ODIN协议承载消息内容使用的单条公钥长度
  public static final byte PPK_PUBKEY_EMBED_DATA_MAX_LENGTH=31;  //ODIN协议在单条公钥中最多嵌入的消息数据长度
  
  public static final int MAX_MULTISIG_TX_NUM = 2; //一条交易里能支持的最大数量多重签名输出条目，建议设为2，如果过大可能会被比特币网络拒绝
  public static final int MAX_N = 3;   //多重签名1-OF-N中的参数N最大数量，建议设为3，如果过大可能会被比特币网络拒绝
  public static final int MAX_OP_RETURN_LENGTH = 75; //OP_RETURN能存放数据的最大字节数
  public static final int MAX_ODIN_DATA_LENGTH=(MAX_N-2)*PPK_PUBKEY_EMBED_DATA_MAX_LENGTH+(MAX_N-1)*PPK_PUBKEY_EMBED_DATA_MAX_LENGTH*(MAX_MULTISIG_TX_NUM-1)+MAX_OP_RETURN_LENGTH;  //支持嵌入的ODIN数据最大字节数
  
  
  public static final Byte FUNC_ID_ODIN_REGIST='R'; 
  public static final Byte FUNC_ID_ODIN_UPDATE='U'; 

  public static final Byte DATA_TEXT_UTF8= 'T'; //normal text in UTF-8
  public static final Byte DATA_BIN_GZIP = 'G'; //Compressed by gzip
  public static final Byte DATA_BIN_DEFLATE = 'D'; //Compressed by deflate
  
  public static final String ODIN_CMD_UPDATE_BASE_INFO ="BI";
  public static final String ODIN_CMD_UPDATE_AP_SET ="AP";
  public static final String ODIN_CMD_UPDATE_VD_SET ="VD";
  public static final String ODIN_CMD_CONFIRM_UPDATE ="CU";
  public static final String ODIN_CMD_TRANS_REGISTER ="TR";  
  
  public static final String ODIN_STATUS_PENDING = "pending";       //"等待区块链收录"
  public static final String ODIN_STATUS_VALID = "valid";         //"正常"
  public static final String ODIN_STATUS_INVALID = "invalid";       //"有错误"
  
  public static final String ODIN_UPDATE_STATUS_RECEIPTING = "receipting";    //"待接收";
  public static final String ODIN_UPDATE_STATUS_AWAITING = "awaiting";      //"待同意更新"
  
  public static final String ODIN_BASE_SET_REGISTER="register";
  public static final String ODIN_BASE_SET_ADMIN="admin";
  public static final String ODIN_BASE_SET_AUTH="auth";
  public static final String ODIN_BASE_SET_PNS_URL="pns_url";
  
  public static final String ODIN_SET_VD_TYPE="type";
  public static final String ODIN_SET_VD_PUBKEY="pubkey";
  public static final String ODIN_SET_VD_CERT_URI="cert_uri"; //保留字段，用于测试
  public static final String ODIN_SET_VD_ENCODE_TYPE_PEM = "PEM";   
  public static final String ODIN_SET_VD_ENCODE_TYPE_BASE64 = "BASE64";  
  
  public static final Byte DATA_CATALOG_UNKNOWN= 0; //Unkown Data,reserved
  
  public static String PTTP_INTEREST="pttp";
  
  public static final String PPK_URI_PREFIX="ppk:";
  public static final String PPK_URI_RESOURCE_MARK="*";

  public static final String PTTP_KEY_VER="ver";
  public static final String PTTP_KEY_URI="uri";
  
  public static final String PTTP_KEY_OPTION="option";

  public static final String PTTP_KEY_SPEC="spec";
  public static final String PTTP_KEY_SPEC_NONE="none"; //无签名
  public static final String PTTP_KEY_SPEC_PAST="past."; //带有签名并符合PASTPAST规范
  public static final String PTTP_KEY_SPEC_PAST_HEADER_V1_PUBLIC="v1.public."; //符合PASTPAST规范v1版本和采用公钥数字签名
  
  public static final String PTTP_KEY_METAINFO="metainfo";
  public static final String PTTP_KEY_IAT="iat";
  public static final String PTTP_KEY_STATUS_CODE="status_code";
  public static final String PTTP_KEY_STATUS_DETAIL="status_detail";
  public static final String PTTP_KEY_CONTENT_ENCODING="content_encoding";
  public static final String PTTP_KEY_CONTENT_TYPE="content_type";
  public static final String PTTP_KEY_CONTENT_LENGTH="content_length";
  public static final String PTTP_KEY_CACHE_AS_LATEST="cache_as_latest";
  
  public static final String PTTP_KEY_CONTENT="content";
  public static final String PTTP_KEY_SIGNATURE="signature";
  
  public static final String PTTP_SIGN_MARK_INTEREST  =  "INTEREST";
  public static final String PTTP_SIGN_MARK_DATA  =  "DATA";
  
  public static final int PTTP_STATUS_CODE_OK = 200;
  public static final int PTTP_STATUS_CODE_LOCAL_ERROR = 775;  //PPk定义的本地异常代码

  public static final String JSON_KEY_PPK_URI="ppk-uri";
  public static final String JSON_KEY_PPK_VALIDATION="validation";
  public static final String JSON_KEY_CHUNK_BYTES="chunk";
  public static final String JSON_KEY_CHUNK_TYPE="chunk-type";
  public static final String JSON_KEY_CHUNK_LENGTH="chunk-length";
  public static final String JSON_KEY_CHUNK_URL="chunk-url";
  public static final String JSON_KEY_EXP_UTC="exp-utc";
  public static final String JSON_KEY_FROM_CACHE="from-cache";

  public static final String JSON_KEY_ORIGINAL_RESP="original-resp";
  
  public static final String DEFAULT_CACHE_AS_LATEST="public,max-age=600"; //默认作为最新版本的缓存时间是10分钟
  public static final String IGNORE_CACHE_AS_LATEST="no-store"; //不作为最新版本缓存
  
  public static final long CACHE_NO_EXP_UTC=0; //长期缓存的特殊时间戳，没有具体失效时间，会根据系统存储容量等条件尽可能缓存长的时间

  public static final int PTTP_VALIDATION_OK        = 0;
  public static final int PTTP_VALIDATION_IGNORED   = 1;
  public static final int PTTP_VALIDATION_ERROR     = 2;

  //Dat
  public static String DAT_UPLOAD_URL="http://tool.ppkpub.org/dat/"; 
  public static String[] DAT_DOWNLOAD_URL_LIST={"http://tool.ppkpub.org/dat/?uri=dat://","https://datbase.org/download/"}; 
  
  //IPFS
  public static String IPFS_API_ADDRESS="/ip4/tool.ppkpub.org/tcp/5001"; //"https://ipfs.infura.io:5001"
  public static String IPFS_DOWNLOAD_URL="http://tool.ppkpub.org:8080/ipfs/";//"https://ipfs.infura.io/ipfs/";
  
  //Bytom File System
  public static String BTMFS_PROXY_URL="http://btmdemo.ppkpub.org/btmfs/"; //Test service
  
  //P2P
  public static boolean enableP2P = false;
  public static String  p2pSetJSON = null;   
  
  //AP
  public static Integer ppkPttpServicePort = 8088;
  public static String  ppkPttpServiceIP   = "127.0.0.1";
  public static String  ppkDefaultHrefApUrl ;
  
  public static String  ppkDefaultHomepage      = "ppk:0/";  //缺省主页
  public static String  ppkSwapToolURI           = "https://tool.ppkpub.org/swap/"; //关联的PPk开放交换工具网址
  public static String  ppkDefaultPnsURI = "" ; //缺省的PNS服务网址

  //Charset
  public static String PPK_TEXT_CHARSET="UTF-8";  //适用文本内容
  public static String PPK_JS_CHARSET="UnicodeBigUnmarked"; //适用JS以HEX编码上传的文本内容
  public static String BINARY_DATA_CHARSET="ISO-8859-1";  //适用原始二进制数据与字符串类型间的转换
  
  //etc.
  public static Integer maxExpiration = 4*2016;
  public static Integer maxInt = ((int) Math.pow(2.0,63.0))-1;
  public static String  defaultTimeZone = "GMT+8"; //缺省显示时区

  public static void loadUserDefined() {
    FileInputStream input;
    String strTemp;
    try {
      input = new FileInputStream(configFile);
      Properties prop = new Properties();
      prop.load(input);
      RPCUsername = prop.getProperty("RPCUsername");
      RPCPassword = prop.getProperty("RPCPassword");
      
      strTemp = prop.getProperty("PttpServicePort");
      if(strTemp!=null && strTemp.length()>0 ){
        ppkPttpServicePort = Integer.parseInt( strTemp );
      }
      System.out.println("PttpServicePort:"+ppkPttpServicePort);
      
      strTemp = prop.getProperty("PttpServiceIP");
      if(strTemp!=null && strTemp.length()>0 ){
        ppkPttpServiceIP = strTemp;
      }
      System.out.println("PttpServiceIP:"+ppkPttpServiceIP);
      //ppkDefaultHrefApUrl = "http://"+ppkPttpServiceIP+":"+ppkPttpServicePort+"/";
      
      strTemp = prop.getProperty("DefaultHomepage");
      System.out.println("DefaultHomepage:"+strTemp);
      if(strTemp!=null && strTemp.length()>0 ){
        ppkDefaultHomepage = strTemp;
      }

      defaultLang = prop.getProperty("Lang").toUpperCase();
      System.out.println("Lang:"+defaultLang);
      
      strTemp = prop.getProperty("UseTestNet");
      System.out.println("UseTestNet:"+strTemp);
      if(strTemp!=null && strTemp.equals("1") ){
        appName += "Test";
        
        String strTestNetHash = prop.getProperty("TestNetHash");
        if(strTestNetHash!=null ){
          PPK_ODIN_MARK_PUBKEY_HEX=strTestNetHash;
        }else{
          PPK_ODIN_MARK_PUBKEY_HEX=PPK_ODIN_MARK_PUBKEY_HEX_TESTNET;
        }
        firstBlock=TESTNET_FIRST_BLOCK;
        System.out.println("TestNetHash:"+strTestNetHash);
      }else{
        PPK_ODIN_MARK_PUBKEY_HEX=PPK_ODIN_MARK_PUBKEY_HEX_MAINNET;
        firstBlock=MAINNET_FIRST_BLOCK;
      }
      
      strTemp = prop.getProperty("WalletFile");
      if(strTemp!=null && strTemp.length()>0 ){
        walletFile = strTemp;
      }
      System.out.println("WalletFile:"+walletFile);

      ppkStandardDataFee = Integer.parseInt(prop.getProperty("StandardFeeSatoshi")) ;
      System.out.println("StandardFeeSatoshi:"+ppkStandardDataFee);
      
      dustSize = Integer.parseInt(prop.getProperty("DustSize")) ;
      System.out.println("DustSize:"+dustSize);
      
      
      strTemp = prop.getProperty("UseDustTX");
      System.out.println("UseDustTX:"+strTemp);
      if(strTemp!=null && strTemp.equals("1") ){
        useDustTX=true;
      }else{
        useDustTX=false;
      }
      
      strTemp = prop.getProperty("MaxUseUTXO");
      if(strTemp!=null && strTemp.length()>0 ){
        maxUseUTXO = Integer.parseInt( strTemp );
      }
      System.out.println("MaxUseUTXO:"+maxUseUTXO);
      
      strTemp = prop.getProperty("DEBUG_KEY");
      System.out.println("DEBUG_KEY:"+strTemp);
      if(strTemp!=null && strTemp.equals("1") ){
        debugKey=true;
      }else{
        debugKey=false;
      }
      
      strTemp = prop.getProperty("EnableP2P");
      System.out.println("EnableP2P:"+strTemp);
      if(strTemp!=null && strTemp.equals("1") ){
        enableP2P=true;
      }else{
        enableP2P=false;
      }
      
      if(enableP2P){
         p2pSetJSON = prop.getProperty("P2P_Set");
      }

      strTemp = prop.getProperty("GuiServerPort");
      if(strTemp!=null && strTemp.length()>0 ){
        GuiServerPort = Integer.parseInt( strTemp );
      }else{
        final double rnd = Math.random();
        // 通过d获取一个[0, 10000)之间的整数
        GuiServerPort = 23456 + (int)(rnd*10000);  //Random a port for local private GUI
      }
      System.out.println("GuiServerPort:"+GuiServerPort);
      
      defaultSqliteFile = dbDirPrefix + appName.toLowerCase()+"-"+majorVersionDB.toString()+".db";
      
      strTemp = prop.getProperty("JDBC");
      if(strTemp!=null && strTemp.length()>0  ){
        jdbcUrl=strTemp;
      }else{
        jdbcUrl = "sqlite:"+defaultSqliteFile;
      }
      System.out.println("JDBC:"+jdbcUrl);
      
      
      strTemp = prop.getProperty("DatUploadURL");
      if(strTemp!=null && strTemp.length()>0 ){
        DAT_UPLOAD_URL = strTemp;
      }
      System.out.println("DatUploadURL:"+DAT_UPLOAD_URL);
      
      /*
      strTemp = prop.getProperty("DatDownloadList");
      if(strTemp!=null && strTemp.length()>0 ){
        DAT_DOWNLOAD_URL_LIST = strTemp;
      }
      System.out.println("DatDownloadList:"+DAT_DOWNLOAD_URL);      
      */
      
      strTemp = prop.getProperty("IpfsApiAddress");
      if(strTemp!=null && strTemp.length()>0 ){
        IPFS_API_ADDRESS = strTemp;
      }
      System.out.println("IpfsApiAddress:"+IPFS_API_ADDRESS);
      
      strTemp = prop.getProperty("IpfsDownloadURL");
      if(strTemp!=null && strTemp.length()>0 ){
        IPFS_DOWNLOAD_URL = strTemp;
      }
      System.out.println("IpfsDownloadURL:"+IPFS_DOWNLOAD_URL);
      
      strTemp = prop.getProperty("BtmpfsProxyURL");
      if(strTemp!=null && strTemp.length()>0 ){
        BTMFS_PROXY_URL = strTemp;
      }
      System.out.println("BtmpfsProxyURL:"+BTMFS_PROXY_URL);
      
      strTemp = prop.getProperty("ProxyURL");
      if(strTemp!=null && strTemp.length()>0 ){
        proxyURL = strTemp;
      }
      System.out.println("ProxyURL:"+proxyURL);
      
      strTemp = prop.getProperty("TimeZone");
      if(strTemp!=null && strTemp.length()>0 ){
        defaultTimeZone = strTemp;
      }
      System.out.println("TimeZone:"+defaultTimeZone);
      
      strTemp = prop.getProperty("PPkSwapToolURI");
      if(strTemp!=null && strTemp.length()>0 ){
        ppkSwapToolURI = strTemp;
      }
      System.out.println("PPkSwapToolURI:"+ppkSwapToolURI);
      
      strTemp = prop.getProperty("DefaultPnsURI");
      if(strTemp!=null && strTemp.trim().length()>0 ){
        ppkDefaultPnsURI = strTemp.trim();
      }
      System.out.println("DefaultPnsURI:"+ppkDefaultPnsURI);
      
      
      //自动清除缓存
      //Util.deleteDir(cacheDirPrefix,false);
      //System.out.println("Cleared PTTP cache : "+cacheDirPrefix);
      
    } catch (IOException e) {
    }    
  }
}
