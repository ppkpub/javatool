import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONArray; 
import org.json.JSONObject;
import org.json.JSONException;

import com.googlecode.jsonrpc4j.JsonRpcHttpClient;

public class APoverETH {
  static Logger logger = LoggerFactory.getLogger(APoverETH.class);

  public static JSONObject fetchInterest(String ap_url, String interest) {
    try {
        //just for test
        JSONObject objInterest=new JSONObject(interest);
        interest=objInterest.getJSONObject("interest").getString("uri");
    }catch (Exception e) {

    }
    JSONObject  obj_ap_resp=new JSONObject();
    logger.info("APoverETH.fetchInterest("+ap_url+","+interest+") ...");
    
    
    //缺省采用 infura.io 提供的json-rpc服务，可以切换成geth等提供的json-rpc比如 http://localhost:8545
    HashMap<String,String> mapEthNetworkJsonRPCs=new HashMap<String,String>();
    mapEthNetworkJsonRPCs.put("mainnet","https://mainnet.infura.io/2M0Ezt8fWNsDZ6wLOAaT");
    mapEthNetworkJsonRPCs.put("ropsten","https://ropsten.infura.io/2M0Ezt8fWNsDZ6wLOAaT");
    mapEthNetworkJsonRPCs.put("infuranet","https://infuranet.infura.io/2M0Ezt8fWNsDZ6wLOAaT");
    mapEthNetworkJsonRPCs.put("kovan","https://kovan.infura.io/2M0Ezt8fWNsDZ6wLOAaT");
    mapEthNetworkJsonRPCs.put("rinkeby","https://rinkeby.infura.io/2M0Ezt8fWNsDZ6wLOAaT");
    
    JsonRpcHttpClient client;
    try {
        //Parse the ap_url like "ethap:rinkeby/0x5c65aab68834c518460a77b32daf5be6ce9fcad7/0xd3317d25"
        String[] arrayTmp=ap_url.split(":");
        arrayTmp=arrayTmp[1].split("/");
        String strEthNet=arrayTmp[0];
        String strEthContractAddress=arrayTmp[1];
        String strEthContractFunctionHash=arrayTmp[2];
        
        //Create the json request object
        JSONObject jsonRequest = new JSONObject();
        
        JSONObject jsonContractInput = new  JSONObject();     
        jsonContractInput.put("to",strEthContractAddress);   
        jsonContractInput.put("data", 
            strEthContractFunctionHash
            +"0000000000000000000000000000000000000000000000000000000000000020"
            +toABIHex(interest)
          );
        
        JSONArray jsonParams = new JSONArray();
        jsonParams.put(jsonContractInput);
        jsonParams.put("latest");
        
        jsonRequest.put("jsonrpc","2.0");
        jsonRequest.put("method", "eth_call"); //暂时只支持不影响区块链状态的call操作，后续可以完善增加调用send_transaction
        jsonRequest.put("params", jsonParams);
        jsonRequest.put("id", UUID.randomUUID().hashCode());
        
        System.out.println("APoverETH.fetchInterest() jsonRequest:"+jsonRequest.toString());
        
        //实例化请求地址，注意服务端地址的配置
        URL destRpcUrl = new URL( mapEthNetworkJsonRPCs.get( strEthNet ) );
        HttpURLConnection connection = (HttpURLConnection) destRpcUrl.openConnection();

        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.connect();

        OutputStream out = connection.getOutputStream();

        out.write(jsonRequest.toString().getBytes());
        out.flush();
        out.close();

        int statusCode = connection.getResponseCode();
        if (statusCode == HttpURLConnection.HTTP_OK) {
          //通过输入流获取二进制数据
          InputStream inStream = connection.getInputStream();
          //得到二进制数据，以二进制封装得到数据，具有通用性
          byte[] data = Util.readInputStream(inStream);
          
          if(data!=null){
            String str_resp=new String(data);
            System.out.println("APoverETH.fetchInterest() str_resp:"+str_resp);
            JSONObject obj_resp=new JSONObject(str_resp);
            String str_result_hex=obj_resp.getString("result");
            
            String str_ap_data=getFirstSegmentFromABIHex(str_result_hex);
            System.out.println("APoverETH.fetchInterest() str_ap_data:"+str_ap_data);
            
            JSONObject obj_ap_data=new JSONObject(str_ap_data);
            JSONObject obj_data=obj_ap_data.getJSONObject("data");
            JSONObject obj_chunk_metainfo=obj_data.getJSONObject("metainfo");
            
            statusCode=obj_data.getInt("status_code");
            if (statusCode == HttpURLConnection.HTTP_OK) {
              byte[]  chunk_content=obj_data.getString("content").getBytes();
              
              obj_ap_resp.put(Config.JSON_KEY_PPK_URI,obj_data.getString("uri"));
              obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_TYPE,obj_chunk_metainfo.getString("content_type"));
              obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK,chunk_content);
              obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_LENGTH,chunk_content.length);
              obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_URL,ap_url);
            }else{
              String str_status_detail=obj_data.optString("status_detail","");
              String str_content=obj_data.optString("content","");
              if(str_content.length()==0)
                str_content="AP status_code : "+statusCode + " " + str_status_detail ;
              byte[]  chunk_content = str_content.getBytes();
              obj_ap_resp.put(Config.JSON_KEY_PPK_URI,obj_data.getString("uri"));
              obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_TYPE,obj_chunk_metainfo.getString("content_type"));
              obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK,chunk_content);
              obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_LENGTH,chunk_content.length);
              obj_ap_resp.put(Config.JSON_KEY_PPK_CHUNK_URL,ap_url);
            }
            
            obj_ap_resp.put(Config.JSON_KEY_PPK_SIGN, obj_ap_data.optString("sign","") );
          }
        }
    }catch (Throwable e) {
        logger.error("APoverETH.fetchInterest() error: "+e.toString());
    }

    System.out.println("APoverETH.fetchInterest() obj_ap_resp:"+obj_ap_resp.toString());
    return obj_ap_resp;
  }

  
  
  //按以太坊ABI规范将一段字符串转换为HEX文本
  protected static String toABIHex(String input) throws Exception{
    String strHexABI="";
    byte[] data = null;
    List<Byte> dataArrayList = new ArrayList<Byte>();

    try {
      data = input.getBytes(Config.BINARY_DATA_CHARSET);
      dataArrayList = Util.toByteArrayList(data);
    } catch (UnsupportedEncodingException e) {
      return null;
    }
    
    int data_length = dataArrayList.size();
    if( data_length > 0 ){
        //生成内容长度描述头HEX
        byte[] tmpLenBytes=new byte[32];
        tmpLenBytes[30]=(byte)(data_length/256);
        tmpLenBytes[31]=(byte)(data_length%256);
        strHexABI=strHexABI+Util.bytesToHexString(tmpLenBytes);
        
        //生成具体字符串内容HEX文本
        strHexABI=strHexABI+Util.bytesToHexString(data);
    }
    
    //如果字符串按字节长度不足32字节的正整数倍，则相应补足0
    if( data_length == 0 || data_length % 32 != 0 ){
      for (int from = data_length % 32; from < 32;from++) {
        strHexABI=strHexABI+"00";
      }
    }
    
    System.out.println("strHexABI:"+strHexABI);
    return strHexABI;
  }
  
  //按以太坊ABI规范从HEX文本中提取出第一段的字符串，注意起始的0x
  protected static String getFirstSegmentFromABIHex( String str_hex ) throws Exception{
    byte[] tmpBytes=Util.hexStringToBytes(str_hex);
    List<Byte> dataArrayList =  Util.toByteArrayList(tmpBytes);
    
    int content_len = tmpBytes[64] & 0xFF |  
            (tmpBytes[63] & 0xFF) << 8 |  
            (tmpBytes[62] & 0xFF) << 16 |  
            (tmpBytes[61] & 0xFF) << 24;  
    System.out.println("content_len="+content_len);    
    
    byte[] chunk = Util.toByteArray( new ArrayList<Byte>(dataArrayList.subList(65, 65+content_len) ) );
    
    return new String(chunk,Config.PPK_TEXT_CHARSET); 
  }
}