import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PPkTool {
  public static Logger logger = LoggerFactory.getLogger(Blocks.class);

  public static void main(String[] args) {
    Config.loadUserDefined();
    
    System.out.println(Language.getLangLabel("PPkPub"));
    System.out.println(Language.getLangLabel("Loading")+" "+Config.appName+" Tool V"+Config.version);
    
    Blocks blocks = Blocks.getInstanceSkipVersionCheck();
    blocks.init();
    blocks.versionCheck(false);
    JsonRpcServletEngine engine = new JsonRpcServletEngine();
    try {
      engine.startup();
    } catch (Exception e) {
      e.printStackTrace();
    }
    blocks.follow();
    Thread blocksThread = new Thread(blocks);
    blocksThread.setDaemon(true);
    blocksThread.start(); 
    
  }
}