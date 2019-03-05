import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bitcoinj.core.Transaction;

public class JsonRpcServiceImpl implements JsonRpcService {
    static Logger logger = LoggerFactory.getLogger(JsonRpcServiceImpl.class);
  
  public String getBalance(String address) {
    BigInteger balance = Util.getBalance(address, "BTC");
    return Double.toString(balance.doubleValue() / Config.btc_unit);
  }
  
  public String send(String source, String destination, Double amount) {
    Blocks blocks = Blocks.getInstance();
    BigInteger quantity = new BigDecimal(amount*Config.btc_unit).toBigInteger();
    try {
      Transaction tx = blocks.transaction(source, destination, quantity, BigInteger.valueOf(Config.ppkStandardDataFee),null, null);
      
      blocks.sendTransaction(source, tx);
      logger.info("Success! You sent "+amount+" BTC to "+destination+".");
      return tx.getHashAsString();
    } catch (Exception e) {
      logger.info("Error! There was a problem with your transaction: "+e.getMessage());            
      return "Error: "+e.getMessage();
    }
  }

  public String importPrivKey(String privateKey) {
    return importPrivateKey(privateKey);
  }
  public String importPrivateKey(String privateKey) {
    Blocks blocks = Blocks.getInstance();
    String address;
    try {
      address = blocks.importPrivateKey(privateKey);
      BigInteger balanceBTC = Util.getBalance(address, "BTC");
      return "\""+address+"\""+":"+String.format("%.8f",balanceBTC.doubleValue() / Config.btc_unit.doubleValue());
    } catch (Exception e) {
      return "Error: "+e.getMessage();
    }
  }

  public void reparse() {
      Blocks blocks = Blocks.getInstance();
      blocks.reparse();
    }
  
}