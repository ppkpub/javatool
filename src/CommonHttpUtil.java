import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * 通用HTTP工具类
 * 更新：2016-12-3 16:13:27
 * 版本：1.0.0
 * 作者：HoKis 
 * 来源：CSDN 
 * 原文：https://blog.csdn.net/HoKis/article/details/53445964 
 */
public final class CommonHttpUtil {
   
    //默认超时时间（毫秒）
    private final static int TIME_OUT_MS = 10000;

    //协议类型
    private final static String HTTP = "http:";
    private final static String HTTPS = "https:";


    /**
     * 判断协议类型，转发到不同的方法进行处理
     * @throws IOException 
     */
    private static String chooseProtocol(String url) throws IOException{
        if (url == null) {
            throw new RuntimeException("url shouldn't be null.");
        }

        if (!url.startsWith(HTTP) && !url.startsWith(HTTPS)) {
            throw new RuntimeException("url format is not supported.");
        }
        //如果是http协议
        if (url.startsWith(HTTP)) {
            return getFromHttp(url);

        }else if (url.startsWith(HTTPS)) {
            //如果是https协议
            return getFromHttps(url);
        }
        return null;
    }

    /**
     * http协议的url
     * @param url
     * @return
     * @throws IOException
     */
    private static String getFromHttp(String url) throws IOException{
        HttpURLConnection con = null;
        try {
            // 打开网页
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setReadTimeout(TIME_OUT_MS);
            con.connect();
            //判断状态码
            if (con.getResponseCode() == 200) {
                //读取流
                return getTextFromCon(con);
            }
        } catch (IOException e) {
            throw new IOException("Connet exception.", e);
        } finally { 
            if (con != null) {
                con.disconnect();
            }
        }

        return null;
    }

    /**
     * https协议的url
     * @param url
     * @return
     * @throws IOException
     */
    private static String getFromHttps(String url) throws IOException{
        HttpsURLConnection httpsConn = null;
        try {
            //构造TrustManager 对象数组
            TrustManager[] tm = {new X509TrustManager() {

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {

                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {

                }
            }}; 

            //创建SSLContext对象，并使用我们指定的信任管理器初始化
            SSLContext sslContext = SSLContext.getInstance("SSL","SunJSSE"); 
            sslContext.init(null, tm, new SecureRandom());

            //从上述SSLContext对象中得到SSLSocketFactory对象
            SSLSocketFactory ssf = sslContext.getSocketFactory();

            //创建HttpsURLConnection对象，并设置其SSLSocketFactory对象
            httpsConn = (HttpsURLConnection)new URL(url).openConnection();
            httpsConn.setSSLSocketFactory(ssf);
            httpsConn.setReadTimeout(TIME_OUT_MS);
            //连接
            httpsConn.connect();

            //判断状态码
            if (httpsConn.getResponseCode() == 200) {
                //读取流
                return getTextFromCon(httpsConn);
            }

        } catch (Exception e) {
            throw new IOException("Connet exception.", e);
        }finally{
            if (httpsConn != null) {
                httpsConn.disconnect();
            }

        }
        return null;
    }

    /**
     * 从流中读取数据
     * @param con
     * @return
     * @throws IOException
     */
    private static String getTextFromCon(URLConnection con) throws IOException{
        try{
        	BufferedReader rd  = new BufferedReader(new InputStreamReader(con.getInputStream()));
      	    StringBuilder sb = new StringBuilder();
      	    String line;
      	      
      	    while ((line = rd.readLine()) != null)
      	    {
      	        sb.append(line + '\n');
      	    }

            return sb.toString();

        } catch (IOException e) {
            throw new IOException("Read stream exception.", e);
        }
    }


    /**
     * 根据URL获取码源
     * @param url
     * @return 返回字符串型码源
     * @throws IOException
     */
    public static String getSourceFromUrl(String url) throws IOException {
        return chooseProtocol(url);
    }


}