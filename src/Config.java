import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;


public class Config {
  //name
  public static String appName = "PPk";
  public static String defaultLang = "EN";

  public static String log = appName+".log";
  public static String downloadUrl = "http://ppkpub.org/javatool/";
  public static String minVersionPage = downloadUrl+"min_version.txt";
  public static String dbPath = "resources/db/";
  public static String cachePath = "resources/cache/";
  public static String newsUrl = downloadUrl+"news.html";
  public static String newsUrlCN = downloadUrl+"news_cn.html";
  public static String downloadZipUrl = downloadUrl+"download.txt"; //Only update package
  public static Integer RPCPort = 44944;
  public static String RPCUsername = "";
  public static String RPCPassword = "";
  public static String ConfigFile = "./resources/ppk.conf";
  
  public static boolean debugKey = false;
  
  //version
  public static Integer majorVersion = 0;
  public static Integer minorVersion = 622;
  public static String version = Integer.toString(majorVersion)+"."+Integer.toString(minorVersion);
  public static Integer majorVersionDB = 1;
  public static Integer minorVersionDB = 2;
  public static String versionDB = Integer.toString(majorVersionDB)+"."+Integer.toString(minorVersionDB);  
  
  //bitcoin
  public static boolean useDustTX = true;
  public static Integer dustSize = 1000;
  //public static Integer minOrderMatchBTC = 100000;
  public static Integer minFee = 10000;
  public static Integer maxFee = 99999;
  public static Integer dataValue = 0;
  public static Integer btc_unit = 100000000;

  //PPk
  public static long ppkToolCreationTime = 1400561240-1;  //UTC 2014-5-20 04:47:20
  public static Integer firstBlock = 426896;
  
  public static Integer ppkStandardDataFee = 10000;
  
  public static int ODIN_PROTOCOL_VER=1; 
  
  //public static String 
  public static String PPK_ODIN_MARK_PUBKEY_HEX_TESTNET="02d173743cd0d94f64d241d82a42c6ca92327c443e489f3842464a4df118d4920a";//1PPkT1hoRbnvSRExCeNoP4s1zr61H12bbg : For testnet
  public static String PPK_ODIN_MARK_PUBKEY_HEX_MAINNET="0320a0de360cc2ae8672db7d557086a4e7c8eca062c0a5a4ba9922dee0aacf3e12";//1PPkPubRnK2ry9PPVW7HJiukqbSnWzXkbi : For Mainnet
  
  public static String PPK_ODIN_MARK_PUBKEY_HEX=null;
  
  public static byte PPK_PUBKEY_TYPE_FLAG=(byte)3;  //ODIN协议承载消息内容使用的公钥类型前缀取值
  public static byte PPK_PUBKEY_LENGTH=33;  //ODIN协议承载消息内容使用的单条公钥长度
  public static byte PPK_PUBKEY_EMBED_DATA_MAX_LENGTH=31;  //ODIN协议在单条公钥中最多嵌入的消息数据长度
  
  public static int MAX_MULTISIG_TX_NUM = 2; //一条交易里能支持的最大数量多重签名输出条目，建议设为2，如果过大可能会被比特币网络拒绝
  public static int MAX_N = 3;   //多重签名1-OF-N中的参数N最大数量，建议设为3，如果过大可能会被比特币网络拒绝
  public static int MAX_OP_RETURN_LENGTH = 75; //OP_RETURN能存放数据的最大字节数
  public static int MAX_ODIN_DATA_LENGTH=(MAX_N-2)*PPK_PUBKEY_EMBED_DATA_MAX_LENGTH+(MAX_N-1)*PPK_PUBKEY_EMBED_DATA_MAX_LENGTH*(MAX_MULTISIG_TX_NUM-1)+MAX_OP_RETURN_LENGTH;  //支持嵌入的ODIN数据最大字节数
  
  
  public static Byte FUNC_ID_ODIN_REGIST='R'; 
  public static Byte FUNC_ID_ODIN_UPDATE='U'; 

  public static Byte DATA_TEXT_UTF8= 'T'; //normal text in UTF-8
  public static Byte DATA_BIN_GZIP = 'G'; //Compressed by gzip
  
  public static String ODIN_CMD_UPDATE_BASE_INFO ="BI";
  public static String ODIN_CMD_UPDATE_AP_SET ="AP";
  public static String ODIN_CMD_UPDATE_VD_SET ="VD";
  public static String ODIN_CMD_CONFIRM_UPDATE ="CU";
  public static String ODIN_CMD_TRANS_REGISTER ="TR";  
  
  public static Byte DATA_CATALOG_UNKNOWN= 0; //Unkown Data,reserved
  
  public static String PPK_URI_PREFIX="ppk:";
  public static String PPK_URI_RESOURCE_MARK="#";

  public static String JSON_KEY_PPK_DATA="data";
  public static String JSON_KEY_PPK_SIGN="sign";
  public static String JSON_KEY_PPK_URI="ppk-uri";
  public static String JSON_KEY_PPK_ALGO="algo";
  public static String JSON_KEY_PPK_SIGN_BASE64="sign_base64";
  public static String JSON_KEY_PPK_PUBKEY="pubkey";
  public static String JSON_KEY_PPK_CERT_URI="cert_uri";
  public static String JSON_KEY_PPK_VALIDATION="validation";
  public static String JSON_KEY_PPK_CHUNK="chunk";
  public static String JSON_KEY_PPK_CHUNK_TYPE="chunk-type";
  public static String JSON_KEY_PPK_CHUNK_LENGTH="chunk-length";
  public static String JSON_KEY_PPK_CHUNK_URL="chunk-url";
  public static String JSON_KEY_PPK_REGISTER="register";
  public static String JSON_KEY_PPK_ADMIN="admin";
  
  public static int PPK_VALIDATION_OK        = 0;
  public static int PPK_VALIDATION_IGNORED   = 1;
  public static int PPK_VALIDATION_ERROR     = 2;
  
  //IPFS
  public static String IPFS_API_ADDRESS="/ip4/127.0.0.1/tcp/5001"; //"https://ipfs.infura.io:5001"
  public static String IPFS_PROXY_URL="https://ipfs.infura.io/ipfs/";
  
  //AP
  public static String PPK_DEFAULT_HREF_AP_URL = "http://0.0.0.0:8087/";

  //Charset
  public static String PPK_TEXT_CHARSET="UTF-8";  //适用文本内容
  public static String BINARY_DATA_CHARSET="ISO-8859-1";  //适用原始二进制数据与字符串类型间的转换

  //etc.
  public static Integer maxExpiration = 4*2016;
  public static Integer maxInt = ((int) Math.pow(2.0,63.0))-1;

  public static void loadUserDefined() {
    FileInputStream input;
    try {
      input = new FileInputStream(ConfigFile);
      Properties prop = new Properties();
      prop.load(input);
      RPCUsername = prop.getProperty("RPCUsername");
      RPCPassword = prop.getProperty("RPCPassword");
      
      defaultLang = prop.getProperty("Lang").toUpperCase();
      System.out.println("Lang:"+defaultLang);
      
      String strTest = prop.getProperty("UseTestNet");
      System.out.println("UseTestNet:"+strTest);
      if(strTest!=null && strTest.equals("1") ){
        appName += "Test";
        
        String strTestNetHash = prop.getProperty("TestNetHash");
        if(strTestNetHash!=null ){
          PPK_ODIN_MARK_PUBKEY_HEX=strTestNetHash;
        }else{
          PPK_ODIN_MARK_PUBKEY_HEX=PPK_ODIN_MARK_PUBKEY_HEX_TESTNET;
        }
        System.out.println("TestNetHash:"+strTestNetHash);
      }else{
        PPK_ODIN_MARK_PUBKEY_HEX=PPK_ODIN_MARK_PUBKEY_HEX_MAINNET;
      }
      
      
      ppkStandardDataFee = Integer.parseInt(prop.getProperty("StandardFeeSatoshi")) ;
      System.out.println("StandardFeeSatoshi:"+ppkStandardDataFee);
      
      dustSize = Integer.parseInt(prop.getProperty("DustSize")) ;
      System.out.println("DustSize:"+dustSize);
      
      String strUseDustTX = prop.getProperty("UseDustTX");
      System.out.println("UseDustTX:"+strUseDustTX);
      if(strUseDustTX!=null && strUseDustTX.equals("1") ){
        useDustTX=true;
      }else{
        useDustTX=false;
      }
      
      String strDebug = prop.getProperty("DEBUG_KEY");
      System.out.println("DEBUG_KEY:"+strDebug);
      if(strDebug!=null && strDebug.equals("1") ){
        debugKey=true;
      }else{
        debugKey=false;
      }
    } catch (IOException e) {
    }    
  }
}
