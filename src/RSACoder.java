import java.security.Key;  
import java.security.KeyFactory;  
import java.security.KeyPair;  
import java.security.KeyPairGenerator;  
import java.security.PrivateKey;  
import java.security.PublicKey;  
import java.security.Signature;  
import java.security.interfaces.RSAPrivateKey;  
import java.security.interfaces.RSAPublicKey;  
import java.security.spec.PKCS8EncodedKeySpec;  
import java.security.spec.X509EncodedKeySpec;  

import org.json.JSONObject;
//import java.util.HashMap;  
//import java.util.Map;  
  
import javax.crypto.Cipher;  
  
/**  
 * @author liangdong 
 * @version 1.0 
 * @since 1.0 
 */  
public abstract class RSACoder extends Coder {  
    public static final String KEY_ALGORITHM = "RSA";  
    private static final String DEFAULT_SIGNATURE_ALGORITHM = "SHA256withRSA"; 
    //public static final String DEFAULT_FORMAT="BASE64";    
  
    private static final String PUBLIC_KEY = "pubkey";  
    private static final String PRIVATE_KEY = "prvkey";  
    
    private static final int KEY_SIZE = 2048; //BIT
  
    /** 
     * 用私钥对信息生成数字签名 
     *  
     * @param data 
     *            加密数据 
     * @param privateKey 
     *            私钥 
     * @param sign_algo 
     *            指定算法，详见 
     *            https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#Signature
     * @return 
     * @throws Exception 
     */  
    public static String sign(byte[] data, String privateKey) throws Exception {  
      return sign(data, privateKey,DEFAULT_SIGNATURE_ALGORITHM);
    }
    public static String sign(byte[] data, String privateKey,String sign_algo) throws Exception {  
        // 解密由base64编码的私钥  
        byte[] keyBytes = decryptBASE64(parseValidPrvKey(privateKey));  
  
        // 构造PKCS8EncodedKeySpec对象  
        PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);  
  
        // KEY_ALGORITHM 指定的加密算法  
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);  
  
        // 取私钥匙对象  
        PrivateKey priKey = keyFactory.generatePrivate(pkcs8KeySpec);  
  
        // 用私钥对信息生成数字签名  
        Signature signature = Signature.getInstance(sign_algo);  
        signature.initSign(priKey);  
        signature.update(data);  
  
        return encryptBASE64(signature.sign());  
    }  
  
    /** 
     * 校验数字签名 
     *  
     * @param data 
     *            加密数据 
     * @param publicKey 
     *            公钥 
     * @param sign 
     *            数字签名 
     *  
     * @return 校验成功返回true 失败返回false 
     * @throws Exception 
     *  
     */  
    public static boolean verify(byte[] data, String publicKey, String sign)  
            throws Exception {  
       return verify(data, publicKey, sign,DEFAULT_SIGNATURE_ALGORITHM);
    }
    
    public static boolean verify(byte[] data, String publicKey, String sign,String sign_algo)  
            throws Exception {  
  
        // 解密由base64编码的公钥  
        byte[] keyBytes = decryptBASE64( parseValidPubKey(publicKey) );  
  
        // 构造X509EncodedKeySpec对象  
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);  
  
        // KEY_ALGORITHM 指定的加密算法  
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);  
  
        // 取公钥匙对象  
        PublicKey pubKey = keyFactory.generatePublic(keySpec);  
  
        Signature signature = Signature.getInstance(sign_algo);  
        signature.initVerify(pubKey);  
        signature.update(data);  
  
        // 验证签名是否正常  
        return signature.verify(decryptBASE64(sign));  
    }  
  
    /** 
     * 解密<br> 
     * 用私钥解密 
     *  
     * @param data 
     * @param key 
     * @return 
     * @throws Exception 
     */  
    public static byte[] decryptByPrivateKey(byte[] data, String key)  
            throws Exception {  
        // 对密钥解密  
        byte[] keyBytes = decryptBASE64( parseValidPrvKey(key) );  
  
        // 取得私钥  
        PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);  
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);  
        Key privateKey = keyFactory.generatePrivate(pkcs8KeySpec);  
  
        // 对数据解密  
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());  
        cipher.init(Cipher.DECRYPT_MODE, privateKey);  
  
        return cipher.doFinal(data);  
    }  
  
    /** 
     * 解密<br> 
     * 用公钥解密 
     *  
     * @param data 
     * @param key 
     * @return 
     * @throws Exception 
     */  
    public static byte[] decryptByPublicKey(byte[] data, String key)  
            throws Exception {  
        // 对密钥解密  
        byte[] keyBytes = decryptBASE64(parseValidPubKey(key));  
  
        // 取得公钥  
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(keyBytes);  
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);  
        Key publicKey = keyFactory.generatePublic(x509KeySpec);  
  
        // 对数据解密  
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());  
        cipher.init(Cipher.DECRYPT_MODE, publicKey);  
  
        return cipher.doFinal(data);  
    }  
  
    /** 
     * 加密<br> 
     * 用公钥加密 
     *  
     * @param data 
     * @param key 
     * @return 
     * @throws Exception 
     */  
    public static byte[] encryptByPublicKey(byte[] data, String key)  
            throws Exception {  
        // 对公钥解密  
        byte[] keyBytes = decryptBASE64( parseValidPubKey(key) );  
  
        // 取得公钥  
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(keyBytes);  
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);  
        Key publicKey = keyFactory.generatePublic(x509KeySpec);  
  
        // 对数据加密  
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());  
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);  
  
        return cipher.doFinal(data);  
    }  
  
    /** 
     * 加密<br> 
     * 用私钥加密 
     *  
     * @param data 
     * @param key 
     * @return 
     * @throws Exception 
     */  
    public static byte[] encryptByPrivateKey(byte[] data, String key)  
            throws Exception {  
        // 对密钥解密  
        byte[] keyBytes = decryptBASE64(parseValidPrvKey(key));  
  
        // 取得私钥  
        PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);  
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);  
        Key privateKey = keyFactory.generatePrivate(pkcs8KeySpec);  
  
        // 对数据加密  
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());  
        cipher.init(Cipher.ENCRYPT_MODE, privateKey);  
  
        return cipher.doFinal(data);  
    }  
  
    /** 
     * 取得私钥 
     *  
     * @param keyMap 
     * @return 
     * @throws Exception 
     */  
    public static String getPrivateKey(JSONObject keyMap)  
            throws Exception {  
        return  keyMap.getString(PRIVATE_KEY);  
    }  
  
    /** 
     * 取得公钥 
     *  
     * @param keyMap 
     * @return 
     * @throws Exception 
     */  
    public static String getPublicKey(JSONObject keyMap)  
            throws Exception {  
       return  keyMap.getString(PUBLIC_KEY);  
    }  
  
    /** 
     * 初始化密钥 
     *  
     * @return 
     * @throws Exception 
     */  
    public static JSONObject initKey() throws Exception {  
        KeyPairGenerator keyPairGen = KeyPairGenerator  
                .getInstance(KEY_ALGORITHM);  
        keyPairGen.initialize(KEY_SIZE);  
  
        KeyPair keyPair = keyPairGen.generateKeyPair();  
  
        // 公钥  
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();  
  
        // 私钥  
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();  
  
        JSONObject  keyMap=new JSONObject();
        keyMap.put(PUBLIC_KEY, "-----BEGIN PUBLIC KEY-----\r\n"+encryptBASE64(publicKey.getEncoded())+"-----END PUBLIC KEY-----\r\n");  
        keyMap.put(PRIVATE_KEY, "-----BEGIN PRIVATE KEY-----\r\n"+encryptBASE64(privateKey.getEncoded())+"-----END PRIVATE KEY-----\r\n");  
        keyMap.put("keysize",KEY_SIZE );
        
        return keyMap;  
    }  
    
    
    /** 
     * 提取有效的公钥数据 
     *  
     * @return 
     */  
    public static String parseValidPubKey(String source){  
      String pubkey=null;
      
      try{
        if(source.indexOf("-----BEGIN PUBLIC KEY-----")>=0){
          int from=source.indexOf("-----BEGIN PUBLIC KEY-----")+"-----BEGIN PUBLIC KEY-----".length();
          int end=source.indexOf("-----END PUBLIC KEY-----")-1;
          if(end<0)
            end=source.length();
        
          pubkey=source.substring(from,end);
        }else{
          pubkey=source;
        }
        
        //去掉换行符
      }catch(Exception e){
        System.out.println("RSACoder.parseValidPubKey() failed:"+e);
      }
      
      return pubkey;
    }
    
    /** 
     * 提取有效的私钥数据 
     *  
     * @return 
     */  
    public static String parseValidPrvKey(String source){  
      String pubkey=null;
      
      try{
        if(source.indexOf("-----BEGIN PRIVATE KEY-----")>=0){
          int from=source.indexOf("-----BEGIN PRIVATE KEY-----")+"-----BEGIN PRIVATE KEY-----".length();
          int end=source.indexOf("-----END PRIVATE KEY-----")-1;
          if(end<0)
            end=source.length();
        
          pubkey=source.substring(from,end);
        }else if(source.indexOf("-----BEGIN RSA PRIVATE KEY-----")>=0){
          int from=source.indexOf("-----BEGIN RSA PRIVATE KEY-----")+"-----BEGIN RSA PRIVATE KEY-----".length();
          int end=source.indexOf("-----END RSA PRIVATE KEY-----")-1;
          if(end<0)
            end=source.length();
        
          pubkey=source.substring(from,end);
        }else{
          pubkey=source;
        }
        
        //去掉换行符
      }catch(Exception e){
        System.out.println("RSACoder.parseValidPubKey() failed:"+e);
      }
      
      return pubkey;
    }
}  