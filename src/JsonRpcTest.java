import java.net.MalformedURLException;
import java.net.URL;

import com.googlecode.jsonrpc4j.JsonRpcHttpClient;
import com.googlecode.jsonrpc4j.ProxyUtil;

public class JsonRpcTest {

  private JsonRpcServletEngine JsonRpcServletEngine;

  public void setup() throws Exception {
    JsonRpcServletEngine = new JsonRpcServletEngine();
    JsonRpcServletEngine.startup();
  }

  public void runJsonRpcHttpClient() throws MalformedURLException {
    JsonRpcHttpClient jsonRpcHttpClient = new JsonRpcHttpClient(new URL(
        "http://127.0.0.1:" + JsonRpcServletEngine.PORT + "/"+ Config.appName.toLowerCase()));
    JsonRpcService service = ProxyUtil.createClientProxy(
        JsonRpcService.class.getClassLoader(), JsonRpcService.class,
        jsonRpcHttpClient);

    System.out.println(service.getBalance("1BckY64TE6VrjVcGMizYBE7gt22axnq6CM"));
  }

  public void teardown() throws Exception {
    JsonRpcServletEngine.stop();
  }
  
  public static void main(String args[]) {
    JsonRpcTest test = new JsonRpcTest();
    try {
      test.setup();
      test.runJsonRpcHttpClient();
      test.teardown();
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }    
  }

}