import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bitcoinj.core.Transaction;
import com.google.common.primitives.Ints;

public class Send {
    static Logger logger = LoggerFactory.getLogger(Send.class);
  public static Integer length = 8+8;
  public static Integer id = 0;
  
  public static Transaction create(String source, String destination, String asset, BigInteger amount_satoshi) throws Exception {
    if (!source.equals("") && !destination.equals("") && asset.equals("BTC") && amount_satoshi.compareTo(BigInteger.ZERO)>0) {
      Blocks blocks = Blocks.getInstance();
      Transaction txBTC = blocks.transaction(source, destination, amount_satoshi, BigInteger.valueOf(Config.ppkStandardDataFee),null, "");
      return txBTC;      
    } else {
      throw new Exception("Please specify a source address and destination address, and send more than 0 BTC.");
    }
  }
}
