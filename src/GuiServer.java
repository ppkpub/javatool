import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.setPort;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Calendar;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;
import spark.template.freemarker.FreeMarkerRoute;

import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import com.google.common.collect.Lists;

import freemarker.template.Configuration;

public class GuiServer implements Runnable {
  public Logger logger = LoggerFactory.getLogger(GuiServer.class);

  public void run() { 
    init(); 
  } 
  
  public Map<String, Object> updateCommonStatus(Request request, Map<String, Object> attributes) {
    Blocks blocks = Blocks.getInstance();
    attributes.put("blocksBTC", blocks.bitcoinBlock);
    attributes.put("blocksPPK", blocks.ppkBlock);
    attributes.put("version", Config.version);
    attributes.put("version_major", Config.majorVersion);
    attributes.put("version_minor", Config.minorVersion);
    attributes.put("system_charset", java.nio.charset.Charset.defaultCharset().toString());
    
    String str_ipfs_status = Util.isIpfsRuning() ? "IPFS:OK":"IPFS:<font color='#F00'>Not running</font>";
    
    attributes.put("ipfs_status", str_ipfs_status);
    
    //Blocks.getInstance().versionCheck();
    if (Blocks.getInstance().parsing) attributes.put("parsing", Blocks.getInstance().parsingBlock);
    
    String address = Util.getAddresses().get(0);
    if (request.session().attributes().contains("address")) {
      address = request.session().attribute("address");
    }
    if (request.queryParams().contains("address")) {
      address = request.queryParams("address");
      request.session().attribute("address", address);
    }
    ArrayList<HashMap<String, Object>> addresses = new ArrayList<HashMap<String, Object>>();
    for (String addr : Util.getAddresses()) {
      HashMap<String,Object> map = new HashMap<String,Object>();  
      map.put("address", addr);
      //map.put("balance_BTC", Util.getBalance(addr, "BTC").doubleValue() / Config.btc_unit.doubleValue());
      addresses.add(map);
    }
    attributes.put("address", address);        
    attributes.put("addresses", addresses);
    for (ECKey key : blocks.wallet.getImportedKeys()) {
      if (key.toAddress(blocks.params).toString().equals(address)) {
        attributes.put("own", true);
      }
    }
    
    //attributes.put("balanceBTC", Util.getBalance(address, "BTC").doubleValue() / Config.btc_unit.doubleValue());
        
    attributes.put("LANG_PPKPUB", Language.getLangLabel("PPkPub"));
    attributes.put("LANG_ODIN", Language.getLangLabel("ODIN"));
    attributes.put("LANG_BROWSER", Language.getLangLabel("Browser"));
    attributes.put("LANG_WALLET", Language.getLangLabel("Wallet"));
    attributes.put("LANG_TECHNICAL", Language.getLangLabel("Technical"));
    attributes.put("LANG_COMMUNITY", Language.getLangLabel("Community"));

    attributes.put("LANG_BLOCKS", Language.getLangLabel("blocks"));
    attributes.put("LANG_VERSION", Language.getLangLabel("Version"));

    attributes.put("LANG_VIEWING_OTHER_ADDRESS", Language.getLangLabel("Viewing other address"));
    attributes.put("LANG_VIEWING_OTHER_ADDRESS_NOTICE", Language.getLangLabel("Notice: Click your address listed on the right side to go back your wallet."));
    attributes.put("LANG_REPARSE_TRANSACTIONS", Language.getLangLabel("Reparse transactions"));
    attributes.put("LANG_VERSION_OUT_OF_DATE", Language.getLangLabel("You must update to the latest version. Your version is out of date."));
    attributes.put("LANG_PARSING_TRANSACTIONS", Language.getLangLabel("The software is parsing transactions. You can still use the software, but the information you see will be out of date."));

    attributes.put("LANG_ERROR", Language.getLangLabel("Error"));
        
    return attributes;
  }
  
  public void init() {
    //start Blocks thread
    Blocks blocks = Blocks.getInstance();
    Thread blocksThread = new Thread(blocks);
    blocksThread.setDaemon(true);
    blocksThread.start(); 
    
    boolean inJar = false;
    try {
      CodeSource cs = this.getClass().getProtectionDomain().getCodeSource();
      inJar = cs.getLocation().toURI().getPath().endsWith(".jar");
    }
    catch (URISyntaxException e) {
      e.printStackTrace();
    }
    
    setPort( Config.GuiServerPort ); 
    
    final Configuration configuration = new Configuration();
    try {
      if (inJar) {
        Spark.externalStaticFileLocation("resources/static/");
        configuration.setClassForTemplateLoading(this.getClass(), "resources/templates/");
      } else {
        Spark.externalStaticFileLocation("./resources/static/");
        configuration.setDirectoryForTemplateLoading(new File("./resources/templates/"));  
      }
    } catch (Exception e) {
    }

    get(new Route("/chat_status_update") {
      @Override
      public Object handle(Request request, Response response) {
        request.session(true);
        if (request.queryParams().contains("chat_open")) {
          request.session().attribute("chat_open", request.queryParams("chat_open"));  
        }
        return request.session().attribute("chat_open");
      }
    });
    get(new FreeMarkerRoute("/") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        
        Blocks blocks = Blocks.getInstance();
        
        if (request.queryParams().contains("reparse")) {
          blocks.reparse();
        }

        Map<String, Object> attributes = new HashMap<String, Object>();
        request.session(true);
        attributes = updateCommonStatus(request, attributes);
      
        attributes.put("title", "A tool focusing on decentralized applications");
                
        if(Language.getCurrentLang().equals("CN"))
            attributes.put("news_url", Config.newsUrlCN);
        else
            attributes.put("news_url", Config.newsUrl);

        Database db = Database.getInstance();
        String address=(String)attributes.get("address");
        //get last 10 odins
        ArrayList<HashMap<String, Object>> odins = new ArrayList<HashMap<String, Object>>();
        
        ResultSet rs = db.executeQuery("select cp.full_odin,cp.short_odin,cp.register,cp.admin ,cp.tx_hash ,cp.tx_index ,cp.block_index,transactions.block_time,cp.odin_set, cp.validity from odins cp,transactions where cp.tx_index=transactions.tx_index order by cp.block_index desc, cp.tx_index desc limit 10;");
        odins = new ArrayList<HashMap<String, Object>>();
        try {
            while ( rs.next()) {
                HashMap<String,Object> map = new HashMap<String,Object>();
                map.put("full_odin", rs.getString("full_odin"));
                map.put("short_odin", rs.getString("short_odin"));
                map.put("register", rs.getString("register"));
                map.put("admin", rs.getString("admin"));
                map.put("tx_index", rs.getString("tx_index"));
                map.put("tx_hash", rs.getString("tx_hash"));
                map.put("validity", Language.getLangLabel(rs.getString("validity")));
                map.put("block_index", rs.getString("block_index"));
                map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
                
                try{
                    JSONObject odin_set = new JSONObject(rs.getString("odin_set")); 
                    map=Odin.parseOdinSet(map,odin_set,address,rs.getString("register"),rs.getString("admin"));
                    
                    odins.add(map);
                }catch (Exception e) {
                    logger.error(e.toString());
                }
            }
        } catch (SQLException e) {
        }
        
        if( !odins.isEmpty() ){
            attributes.put("recent_odins", odins);
        }
        
        attributes.put("LANG_A_UNIQUE_GROUP", Language.getLangLabel("a public group that focus on open decentralized protocols and tools."));
        attributes.put("LANG_MADE_FOR_A_LITTLE_JOY", Language.getLangLabel("ODIN is a decentralized DNS based on blockchains (BTC,ETH,BYTOM,Fabric,etc) . It's made for a little joy."));
        attributes.put("LANG_DOWNLOAD", Language.getLangLabel("Download"));
        attributes.put("LANG_SOFTWARE_INCLUDING", Language.getLangLabel("this opensource software -- including a tool for registing and managing ODIN , a simple bitcoin wallet . Start playing today!"));
        attributes.put("LANG_BUILT_ON_BITCOIN_BLOCKCHAIN", Language.getLangLabel("Built on top of the blockchain technology, PPkPub will research and develope a few fully open decentralized protocols & tools. There is no central control and central point of failure. It's owned by the people."));
        attributes.put("LANG_LEARN_MORE", Language.getLangLabel("Learn more"));
        attributes.put("LANG_NEWS", Language.getLangLabel("News"));

        attributes.put("LANG_REGISTE_A_NEW_ODIN", Language.getLangLabel("Registe a new ODIN"));
        attributes.put("LANG_FULL_ODIN", Language.getLangLabel("Full ODIN"));
        attributes.put("LANG_SHORT_ODIN", Language.getLangLabel("SN"));
        attributes.put("LANG_TIME", Language.getLangLabel("Time"));
        attributes.put("LANG_ODIN_TITLE", Language.getLangLabel("ODIN title"));
        attributes.put("LANG_ADMIN_REGISTER", Language.getLangLabel("Admin/Register"));
        attributes.put("LANG_ADMIN_BY", Language.getLangLabel("Admin by"));
        attributes.put("LANG_REGISTED_BY", Language.getLangLabel("Registed by"));
        attributes.put("LANG_AP_LIST", Language.getLangLabel("Access Point"));
        attributes.put("LANG_STATUS", Language.getLangLabel("Status"));
        attributes.put("LANG_UPDATE_DESC", Language.getLangLabel("Update description"));
        
        attributes.put("LANG_PENDING", Language.getLangLabel("Pending"));
        attributes.put("LANG_VALID", Language.getLangLabel("valid"));    
        attributes.put("LANG_UPDATE", Language.getLangLabel("Update"));      
        attributes.put("LANG_TRANSFER_REGISTER", Language.getLangLabel("Transfer register"));  

        return modelAndView(attributes, "index.html");
      }
    });
    get(new FreeMarkerRoute("/participate") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = new HashMap<String, Object>();
        request.session(true);
        attributes = updateCommonStatus(request, attributes);
        attributes.put("title", "Participate");
        return modelAndView(attributes, "participate.html");
      }
    });
    get(new FreeMarkerRoute("/community") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = new HashMap<String, Object>();
        request.session(true);

        attributes = updateCommonStatus(request, attributes);
        attributes.put("title", "Community");

        attributes.put("LANG_CONTACT", Language.getLangLabel("Contact"));  
        attributes.put("LANG_EMAIL", Language.getLangLabel("Email"));  
        attributes.put("LANG_WEBSITE", Language.getLangLabel("Website"));  
        attributes.put("LANG_TECHNICAL", Language.getLangLabel("Technical"));  
        attributes.put("LANG_RESOURCE", Language.getLangLabel("Resource"));  
        attributes.put("LANG_MEMBERS", Language.getLangLabel("Members"));  
        attributes.put("LANG_CHINA", Language.getLangLabel("China"));  
        attributes.put("LANG_CANADA", Language.getLangLabel("Canada"));  
        attributes.put("LANG_DONATIONS", Language.getLangLabel("Donations"));  
        attributes.put("LANG_DONATIONS_ARE_WELCOME", Language.getLangLabel("Donations are welcome."));  
                
        return modelAndView(attributes, "community.html");
      }
    });
  
    post(new FreeMarkerRoute("/wallet") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = handleWalletRequest(request);
        return modelAndView(attributes, "wallet.html");
      }
    });  
    get(new FreeMarkerRoute("/wallet") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = handleWalletRequest(request);
        return modelAndView(attributes, "wallet.html");
      }
    });  
    
    
    post(new FreeMarkerRoute("/odin") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = handleOdinRequest(request);
        return modelAndView(attributes, "odin.html");
      }
    });
    get(new FreeMarkerRoute("/odin") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = handleOdinRequest(request);
        return modelAndView(attributes, "odin.html");
      }
    });
    get(new FreeMarkerRoute("/odin-add") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = new HashMap<String, Object>();
        request.session(true);
        attributes = updateCommonStatus(request, attributes);
        attributes.put("title", "Register a new odin");


        attributes.put("LANG_REGISTE_A_NEW_ODIN", Language.getLangLabel("Registe a new ODIN"));
        attributes.put("LANG_ODIN_ADMIN_ADDRESS", Language.getLangLabel("Admin BTC address"));
        attributes.put("LANG_ADMIN_SAME_AS_REGISTER", Language.getLangLabel("Same as register"));  
        attributes.put("LANG_ODIN_TITLE", Language.getLangLabel("ODIN title"));
        attributes.put("LANG_THE_PUBLIC_EMAIL_FOR", Language.getLangLabel("The public email of the admin"));
        attributes.put("LANG_ODIN_AP", Language.getLangLabel("Access Point"));
        attributes.put("LANG_ODIN_AP_URL_SHOULD_BE", Language.getLangLabel("the access point URL"));
        attributes.put("LANG_EMAIL", Language.getLangLabel("Email"));  
        attributes.put("LANG_ODIN_AUTHORITY", Language.getLangLabel("Authority"));  
        attributes.put("LANG_THE_REGISTER_OR_ADMIN_CAN_UPDATE", Language.getLangLabel("The register or admin can update"));
        attributes.put("LANG_ONLY_THE_ADMIN_CAN_UPDATE", Language.getLangLabel("Only the admin can update"));
        attributes.put("LANG_REGISTER_AND_ADMIN_MUST_UPDATE_TOGETHER", Language.getLangLabel("Register and admin must update together"));
        
        attributes.put("LANG_OPTIONAL", Language.getLangLabel("Optional"));
        attributes.put("LANG_REGIST_IT", Language.getLangLabel("Regist it"));  
        attributes.put("LANG_CLICKED_WAITING", Language.getLangLabel("Waiting"));  
                  
        return modelAndView(attributes, "odin-add.html");
      }
    });
    post(new FreeMarkerRoute("/odin-confirm") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = handleOdinConfirmRequest(request);
        return modelAndView(attributes, "odin-confirm.html");
      }
    });  
    get(new FreeMarkerRoute("/odin-detail") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = handleOdinDetailRequest(request);
        return modelAndView(attributes, "odin-detail.html");
      }
    });  
    get(new FreeMarkerRoute("/odin-match") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = handleOdinMatchRequest(request);
        return modelAndView(attributes, "odin-match.html");
      }
    });  
    post(new FreeMarkerRoute("/odin-update") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = handleOdinUpdateRequest(request);
        return modelAndView(attributes, "odin-update.html");
      }
    });  
    get(new FreeMarkerRoute("/odin-update") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = handleOdinUpdateRequest(request);
        return modelAndView(attributes, "odin-update.html");
      }
    });  
    post(new FreeMarkerRoute("/odin-update-ap") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = handleOdinUpdateApSetRequest(request);
        return modelAndView(attributes, "odin-update-ap.html");
      }
    });  
    get(new FreeMarkerRoute("/odin-update-ap") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = handleOdinUpdateApSetRequest(request);
        return modelAndView(attributes, "odin-update-ap.html");
      }
    }); 
    post(new FreeMarkerRoute("/odin-create-first-ap") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = handleOdinCreateFirstApRequest(request);
        return modelAndView(attributes, "odin-create-first-ap.html");
      }
    });  
    get(new FreeMarkerRoute("/odin-create-first-ap") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = handleOdinCreateFirstApRequest(request);
        return modelAndView(attributes, "odin-create-first-ap.html");
      }
    });     
    post(new FreeMarkerRoute("/odin-update-vd") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = handleOdinUpdateVdSetRequest(request);
        
        return modelAndView(attributes, "odin-update-vd.html");
      }
    });  
    get(new FreeMarkerRoute("/odin-update-vd") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = handleOdinUpdateVdSetRequest(request);

        return modelAndView(attributes, "odin-update-vd.html");
      }
    }); 
    post(new FreeMarkerRoute("/odin-trans") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = handleOdinTransRequest(request);
        return modelAndView(attributes, "odin-trans.html");
      }
    });  
    get(new FreeMarkerRoute("/odin-trans") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = handleOdinTransRequest(request);
        return modelAndView(attributes, "odin-trans.html");
      }
    });  
    get(new FreeMarkerRoute("/odin-check-ap-vd") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = handleOdinCheckApVdRequest(request);
        return modelAndView(attributes, "odin-check-ap-vd.html");
      }
    });          
    get(new FreeMarkerRoute("/error") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        setConfiguration(configuration);
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("title", "Error");
        return modelAndView(attributes, "error.html");
      }
    });
  }
  
  public Map<String, Object> handleWalletRequest(Request request) {
    Map<String, Object> attributes = new HashMap<String, Object>();
    request.session(true);
    attributes = updateCommonStatus(request, attributes);
    attributes.put("title", "Wallet");
    
    Blocks blocks = Blocks.getInstance();
    String address=(String)attributes.get("address");
    if (request.queryParams().contains("form") && request.queryParams("form").equals("delete")) {
      ECKey deleteKey = null;
      String deleteAddress = request.queryParams("address");
      for (ECKey key : blocks.wallet.getImportedKeys()) {
        if (key.toAddress(blocks.params).toString().equals(deleteAddress)) {
          deleteKey = key;
        }
      }
      if (deleteKey != null) {
        logger.info("Deleting private key");
        blocks.wallet.removeKey(deleteKey);
        attributes.put("success", Language.getLangLabel("Your private key has been deleted. You can no longer transact from this address."));              
        if (blocks.wallet.getImportedKeys().size()<=0) {
          ECKey newKey = new ECKey();
          blocks.wallet.addKey(newKey);
        }
      }
    }
    if (request.queryParams().contains("form") && request.queryParams("form").equals("reimport")) {
      ECKey importKey = null;
      String deleteAddress = request.queryParams("address");
      for (ECKey key : blocks.wallet.getImportedKeys()) {
        if (key.toAddress(blocks.params).toString().equals(deleteAddress)) {
          importKey = key;
        }
      }
      if (importKey != null) {
        logger.info("Reimporting private key transactions");
        try {
          blocks.importPrivateKey(importKey);
          attributes.put("success", Language.getLangLabel("Your transactions have been reimported."));
        } catch (Exception e) {
          attributes.put("error", Language.getLangLabel("Error when reimporting transactions: ")+e.getMessage());
        }
      }
    }
    
    if (request.queryParams().contains("form") && request.queryParams("form").equals("send")) {
      String source = request.queryParams("source");
      String destination = request.queryParams("destination");
      String quantityStr=request.queryParams("quantity");
      
      try{
        Address.getParametersFromAddress(destination);
      } catch(Exception e){
        destination="";
      }

      if(destination.length()==0){
        attributes.put("error", Language.getLangLabel("Please input a valid destination address that you want to send."));
      }else if(quantityStr.length()==0){
        attributes.put("error", Language.getLangLabel("Please input the BTC amount that you want to send."));
      } else {
        try {
          Double rawQuantity = Double.parseDouble(quantityStr);
          BigInteger quantity = new BigDecimal(rawQuantity*Config.btc_unit).toBigInteger();
        
          Transaction tx = Send.create(source, destination, "BTC", quantity);
          blocks.sendTransaction(source,tx);
          attributes.put("success", Language.getLangLabel("Your request had been submited. Please wait confirms for at least 1 block."));
        } catch (Exception e) {
          attributes.put("error", e.getMessage());
        }
      }
    }

    if (request.queryParams().contains("form") && request.queryParams("form").equals("import")) {
      String privateKey = request.queryParams("privatekey");
      try {
        address = Blocks.getInstance().importPrivateKey(privateKey);
        request.session().attribute("address", address);
        attributes.put("address", address);        
        attributes.put("success", Language.getLangLabel("Your private key has been imported."));
      } catch (Exception e) {
        attributes.put("error", Language.getLangLabel("Error when importing private key: ")+e.getMessage());
      }
    }
    
    //Just for debug to show private&public keys
    if(Config.debugKey){
      for (ECKey key : blocks.wallet.getImportedKeys()) {
          if (key.toAddress(blocks.params).toString().equals(address)) {
            attributes.put("testShowKey", "PrivateKey HEX:"+key.getPrivateKeyAsHex() + " WIF:"+  key.getPrivateKeyAsWiF(MainNetParams.get())+"  PubKey:"+key.getPublicKeyAsHex());
          }
      }
    }

    attributes.put("balanceBTC", Util.getBalance(address, "BTC").doubleValue() / Config.btc_unit.doubleValue());
    
    Database db = Database.getInstance();

    //save wallet file
    try {
      blocks.wallet.saveToFile(new File(blocks.walletFile));
    } catch (IOException e) {
    }
        
    attributes.put("LANG_MY", Language.getLangLabel("My "));
    attributes.put("LANG_HIS", Language.getLangLabel("His "));
    attributes.put("LANG_BALANCE", Language.getLangLabel("balance"));
    attributes.put("LANG_BTC", Language.getLangLabel("BTC"));
    attributes.put("LANG_IMPORT_PRIVATE_KEY", Language.getLangLabel("Import private key"));
    attributes.put("LANG_PRIVATE_KEY", Language.getLangLabel("private key"));
    attributes.put("LANG_YOUR_PRIVATE_KEY_SHOULD_BE", Language.getLangLabel("Your private key should be in WIF format. For more information about where to find this, see the Participate page."));
    attributes.put("LANG_SEND", Language.getLangLabel("Send"));
    attributes.put("LANG_DESTINATION_ADDRESS", Language.getLangLabel("destination address"));
    attributes.put("LANG_QUANTITY_BTC", Language.getLangLabel("quantity (BTC)"));
    attributes.put("LANG_BLOCK", Language.getLangLabel("Block"));
    attributes.put("LANG_TIME", Language.getLangLabel("Time"));
    attributes.put("LANG_SOURCE", Language.getLangLabel("Source"));
    attributes.put("LANG_DESTINATION", Language.getLangLabel("Destination"));
    attributes.put("LANG_STATUS", Language.getLangLabel("Status"));
    attributes.put("LANG_QUANTITY", Language.getLangLabel("Quantity"));
    attributes.put("LANG_CLICKED_WAITING", Language.getLangLabel("Waiting"));  
    
    return attributes;
  }

  public Map<String, Object> handleOdinRequest(Request request) {
    Map<String, Object> attributes = new HashMap<String, Object>();
    request.session(true);
    attributes = updateCommonStatus(request, attributes);
    attributes.put("title", "Odin");
    
    Blocks blocks = Blocks.getInstance();
    String address=(String)attributes.get("address");
    
    if (request.queryParams().contains("form") && request.queryParams("form").equals("add-odin")) {
      logger.info("************* do add-odin **************");
    
      String register = request.queryParams("register");
      String admin = request.queryParams("admin_address");
      String titleStr=request.queryParams("title");
      String emailStr=request.queryParams("email");
      String authSet=request.queryParams("auth");

      if(admin.length()>0 && authSet.length()>0 ){
        try {
          Map mapOdinSet = new HashMap(); 
                    
          mapOdinSet.put("ver", Config.ODIN_PROTOCOL_VER); 
          mapOdinSet.put("auth", authSet); 

          if(register.equals(admin))
             admin="";
        
          if(titleStr.length()>0)
             mapOdinSet.put("title", titleStr); 
         
          if(emailStr.length()>0)
             mapOdinSet.put("email", emailStr); 
           
          JSONObject odin_set = new JSONObject(mapOdinSet); 
          
          Transaction tx = Odin.createOdin(register, admin,odin_set);
          blocks.sendTransaction(register,tx);
          attributes.put("success", Language.getLangLabel("Your request had been submited. Please wait confirms for at least 1 block."));
        } catch (Exception e) {
          logger.error("************* do add-odin error: "+e.getMessage());
          attributes.put("error", e.getMessage());
        }
      } else {
        attributes.put("error", Language.getLangLabel("Please input valid admin address,title and AP URLs."));
      }
    }
    
    Database db = Database.getInstance();
    
    ArrayList<HashMap<String, Object>> odins ;
    ArrayList<HashMap<String, Object>> my_pending_odins ;
    
    List<OdinInfo> allPendingOdins = Odin.getPending();
    logger.info( "\n=============================\n allPendingOdins.size="+allPendingOdins.size()+"\n=====================\n");
    
    odins = new ArrayList<HashMap<String, Object>>();
    my_pending_odins = new ArrayList<HashMap<String, Object>>();
    for (OdinInfo odinInfo : allPendingOdins) {
      HashMap<String,Object> map = new HashMap<String,Object>();
      map.put("full_odin", odinInfo.fullOdin);
      map.put("short_odin", odinInfo.shortOdin.toString());
      map.put("register", odinInfo.register);
      map.put("admin", odinInfo.admin);
      map.put("tx_index",odinInfo.txIndex.toString());
      map.put("tx_hash", odinInfo.txHash);
      map.put("block_index", odinInfo.blockIndex.toString());
      map.put("block_time", Util.timeFormat(odinInfo.blockTime));
      map.put("validity",odinInfo.validity);

      try{
        map=Odin.parseOdinSet(map,odinInfo.odinSet,address,odinInfo.register,odinInfo.admin);
        odins.add(map);
        
        if(address.equals(odinInfo.register))
          my_pending_odins.add(map);
        
      }catch (Exception e) {
        logger.error(e.toString());
      }
    }
    attributes.put("all_pending_odins", odins);
    attributes.put("my_pending_odins", my_pending_odins);
        
    //get last 200 odins
    ResultSet rs = db.executeQuery("select cp.full_odin,cp.short_odin,cp.register,cp.admin ,cp.tx_hash ,cp.tx_index ,cp.block_index,transactions.block_time,cp.odin_set, cp.validity from odins cp,transactions where cp.tx_index=transactions.tx_index order by cp.block_index desc, cp.tx_index desc limit 200;");
    odins = new ArrayList<HashMap<String, Object>>();
    try {
      while ( rs.next()) {
        HashMap<String,Object> map = new HashMap<String,Object>();
        map.put("full_odin", rs.getString("full_odin"));
        map.put("short_odin", rs.getString("short_odin"));
        map.put("register", rs.getString("register"));
        map.put("admin", rs.getString("admin"));
        map.put("tx_index", rs.getString("tx_index"));
        map.put("tx_hash", rs.getString("tx_hash"));
        map.put("validity", rs.getString("validity"));
        map.put("block_index", rs.getString("block_index"));
        map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
        
        try{
          JSONObject odin_set = new JSONObject(rs.getString("odin_set")); 
          map=Odin.parseOdinSet(map,odin_set,address,rs.getString("register"),rs.getString("admin"));
          
          odins.add(map);
        }catch (Exception e) {
          logger.error(e.toString());
        }
      }
    } catch (SQLException e) {
    }
    
    if( !odins.isEmpty() ){
      attributes.put("recent_odins", odins);
    }
    
    //get my registed odins
    rs = db.executeQuery("select cp.full_odin,cp.short_odin,cp.register,cp.admin ,cp.tx_hash ,cp.tx_index ,cp.block_index,transactions.block_time,cp.odin_set,cp.validity from odins cp,transactions where cp.register='"+address+"' and cp.tx_index=transactions.tx_index order by cp.block_index desc, cp.tx_index desc;");
    odins = new ArrayList<HashMap<String, Object>>();
    try {
      while ( rs.next()) {
        HashMap<String,Object> map = new HashMap<String,Object>();
        map.put("full_odin", rs.getString("full_odin"));
        map.put("short_odin", rs.getString("short_odin"));
        map.put("register", rs.getString("register"));
        map.put("admin", rs.getString("admin"));
        map.put("tx_index", rs.getString("tx_index"));
        map.put("tx_hash", rs.getString("tx_hash"));
        map.put("validity", rs.getString("validity"));
        map.put("block_index", rs.getString("block_index"));
        map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
                
        try{
          JSONObject odin_set = new JSONObject(rs.getString("odin_set")); 
          map=Odin.parseOdinSet(map,odin_set,address,rs.getString("register"),rs.getString("admin"));

          odins.add(map);
        }catch (Exception e) {
          logger.error(e.toString());
        }
      }
    } catch (SQLException e) {
    }
        
    attributes.put("my_registed_odins", odins);
    attributes.put("my_registed_odin_num", odins.size() );
    
    //get my admin odins
    rs = db.executeQuery("select cp.full_odin,cp.short_odin,cp.register,cp.admin ,cp.tx_hash ,cp.tx_index ,cp.block_index,transactions.block_time,cp.odin_set,cp.validity from odins cp,transactions where cp.admin='"+address+"' and cp.tx_index=transactions.tx_index order by cp.block_index desc, cp.tx_index desc;");
    odins = new ArrayList<HashMap<String, Object>>();
    try {
      while ( rs.next()) {
        HashMap<String,Object> map = new HashMap<String,Object>();
        map.put("full_odin", rs.getString("full_odin"));
        map.put("short_odin", rs.getString("short_odin"));
        map.put("register", rs.getString("register"));
        map.put("admin", rs.getString("admin"));
        map.put("tx_index", rs.getString("tx_index"));
        map.put("tx_hash", rs.getString("tx_hash"));
        map.put("validity", rs.getString("validity"));
        map.put("block_index", rs.getString("block_index"));
        map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
        
        try{
          JSONObject odin_set = new JSONObject(rs.getString("odin_set")); 
          map=Odin.parseOdinSet(map,odin_set,address,rs.getString("register"),rs.getString("admin"));
          
          odins.add(map);
        }catch (Exception e) {
          logger.error(e.toString());
        }
      }
    } catch (SQLException e) {
    }
    
    attributes.put("my_admin_odins", odins);
    attributes.put("my_admin_odin_num", odins.size() );
    
    //get my pending update
    List<OdinUpdateInfo> updatePending = OdinUpdate.getPending(address);
    logger.info( "\n=============================\n updatePending.size="+updatePending.size()+"\n=====================\n");
    
    odins = new ArrayList<HashMap<String, Object>>();
    for (OdinUpdateInfo updateOdinInfo : updatePending) {
      HashMap<String,Object> map = new HashMap<String,Object>();
      map.put("full_odin", updateOdinInfo.fullOdin);
      map.put("short_odin", updateOdinInfo.shortOdin.toString());
      map.put("updater", updateOdinInfo.updater);
      map.put("tx_index",updateOdinInfo.txIndex.toString());
      map.put("tx_hash", updateOdinInfo.txHash);
      map.put("block_index", updateOdinInfo.blockIndex.toString());
      map.put("block_time", Util.timeFormat(updateOdinInfo.blockTime));
      map.put("validity",updateOdinInfo.validity);

      try{
        map=OdinUpdate.parseOdinUpdateSet(map,updateOdinInfo.updater,updateOdinInfo.destination,updateOdinInfo.updateSet);
        odins.add(map);
      }catch (Exception e) {
        logger.error(e.toString());
      }
    }
    attributes.put("my_pending_update_logs", odins);       

    //get valid updates that awaiting my receipt or i'm awaiting another receipt
    rs = db.executeQuery("select l.log_id,l.tx_index, l.block_index,l.updater,l.destination, l.update_set,l.validity,cp.full_odin,cp.short_odin,cp.register,cp.admin,transactions.block_time,transactions.tx_hash from odins cp,odin_update_logs l,transactions where l.validity='receipting' and l.full_odin=cp.full_odin and ( cp.register='"+address+"' or cp.admin='"+address+"' or l.destination='"+address+"') and l.tx_index=transactions.tx_index order by l.block_index desc, cp.tx_index desc limit 100;");
    odins = new ArrayList<HashMap<String, Object>>();
    try {
      while ( rs.next()) {
        HashMap<String,Object> map = new HashMap<String,Object>();
        map.put("full_odin", rs.getString("full_odin"));
        map.put("short_odin", rs.getString("short_odin"));
        map.put("updater", rs.getString("updater"));
        map.put("log_id", rs.getString("log_id"));
        map.put("tx_index", rs.getString("tx_index"));
        map.put("tx_hash", rs.getString("tx_hash"));
        map.put("validity", rs.getString("validity"));
        map.put("block_index", rs.getString("block_index"));
        map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
        
        try{
          JSONObject update_set = new JSONObject(rs.getString("update_set")); 
          map=OdinUpdate.parseOdinUpdateSet(map,rs.getString("updater"),rs.getString("destination"),update_set);

          if(address.equals(rs.getString("destination")))
              map.put("awaiting_my_receipting", true);

          odins.add(map);
        }catch (Exception e) {
          logger.error(e.toString());
        }
      }
    } catch (SQLException e) {
    }
    attributes.put("my_receipting_updates", odins);
    
    //get valid updates that awaiting my confirm or i'm awaiting another confirm
    rs = db.executeQuery("select l.log_id,l.tx_index, l.block_index,l.updater,l.destination, l.update_set,l.validity,cp.full_odin,cp.short_odin,cp.register,cp.admin,transactions.block_time,transactions.tx_hash from odins cp,odin_update_logs l,transactions where l.validity='awaiting' and l.full_odin=cp.full_odin and (l.updater='"+address+"' or l.required_confirmer='"+address+"') and ( cp.register='"+address+"' or cp.admin='"+address+"') and l.tx_index=transactions.tx_index order by l.block_index desc, cp.tx_index desc limit 100;");
    odins = new ArrayList<HashMap<String, Object>>();
    try {
      while ( rs.next()) {
        HashMap<String,Object> map = new HashMap<String,Object>();
        map.put("full_odin", rs.getString("full_odin"));
        map.put("short_odin", rs.getString("short_odin"));
        map.put("updater", rs.getString("updater"));
        map.put("log_id", rs.getString("log_id"));
        map.put("tx_index", rs.getString("tx_index"));
        map.put("tx_hash", rs.getString("tx_hash"));
        map.put("validity", rs.getString("validity"));
        map.put("block_index", rs.getString("block_index"));
        map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
        
        try{
          JSONObject update_set = new JSONObject(rs.getString("update_set")); 
          map=OdinUpdate.parseOdinUpdateSet(map,rs.getString("updater"),rs.getString("destination"),update_set);

          if(!address.equals(rs.getString("updater")))
              map.put("awaiting_my_confirm", true);

          odins.add(map);
        }catch (Exception e) {
          logger.error(e.toString());
        }
      }
    } catch (SQLException e) {
    }
    attributes.put("my_awaiting_updates", odins);
        
    //get my end update logs
    rs = db.executeQuery("select l.tx_index, l.block_index,l.updater,l.destination, l.update_set,l.validity,cp.full_odin,cp.short_odin,cp.register,cp.admin,transactions.block_time,transactions.tx_hash from odins cp,odin_update_logs l,transactions where l.updater='"+address+"' and l.full_odin=cp.full_odin and (l.validity='valid' or l.validity='invalid') and l.tx_index=transactions.tx_index order by l.block_index desc, cp.tx_index desc limit 100;");
    odins = new ArrayList<HashMap<String, Object>>();
    try {
      while ( rs.next()) {
        HashMap<String,Object> map = new HashMap<String,Object>();
        map.put("full_odin", rs.getString("full_odin"));
        map.put("short_odin", rs.getString("short_odin"));
        map.put("updater", rs.getString("updater"));
        map.put("tx_index", rs.getString("tx_index"));
        map.put("tx_hash", rs.getString("tx_hash"));
        map.put("validity", rs.getString("validity"));
        map.put("block_index", rs.getString("block_index"));
        map.put("block_time", Util.timeFormat(rs.getInt("block_time")));
        
        try{
          JSONObject update_set = new JSONObject(rs.getString("update_set")); 
          map=OdinUpdate.parseOdinUpdateSet(map,rs.getString("updater"),rs.getString("destination"),update_set);
          odins.add(map);
        }catch (Exception e) {
          logger.error(e.toString());
        }
      }
    } catch (SQLException e) {
    }
    attributes.put("my_update_logs", odins);
    
    attributes.put("LANG_REGIST_NEW_ODIN", Language.getLangLabel("Registe a new ODIN"));
    
    attributes.put("LANG_RECENT_ODINS", Language.getLangLabel("Recent ODINs"));
    attributes.put("LANG_MY_REGISTED_ODINS", Language.getLangLabel("My registed ODINs"));
    attributes.put("LANG_MY_ADMIN_ODINS", Language.getLangLabel("My admin ODINs"));
    attributes.put("LANG_MY_UPDATE_LOGS", Language.getLangLabel("My update logs"));
    attributes.put("LANG_QUERY_ODIN", Language.getLangLabel("Query ODIN"));
    attributes.put("LANG_INPUT_ODIN", Language.getLangLabel("Input the ODIN"));
    attributes.put("LANG_INPUT_ODIN_DESC", Language.getLangLabel("Input the ODIN number that you want to query"));
    attributes.put("LANG_MATCH_WORD", Language.getLangLabel("Match word"));
    attributes.put("LANG_INPUT_WORD", Language.getLangLabel("Input the word"));
    attributes.put("LANG_INPUT_WORD_DESC", Language.getLangLabel("Input the word string that you want to match"));
    
    attributes.put("LANG_FULL_ODIN", Language.getLangLabel("Full ODIN"));
    attributes.put("LANG_SHORT_ODIN", Language.getLangLabel("SN"));
    attributes.put("LANG_TIME", Language.getLangLabel("Time"));
    attributes.put("LANG_ODIN_TITLE", Language.getLangLabel("ODIN title"));
    attributes.put("LANG_ADMIN_REGISTER", Language.getLangLabel("Admin/Register"));
    attributes.put("LANG_ADMIN_BY", Language.getLangLabel("Admin by"));
    attributes.put("LANG_REGISTED_BY", Language.getLangLabel("Registed by"));
    attributes.put("LANG_AP_LIST", Language.getLangLabel("Access Point"));
    attributes.put("LANG_STATUS", Language.getLangLabel("Status"));
    attributes.put("LANG_UPDATE_DESC", Language.getLangLabel("Update description"));
    
    attributes.put("LANG_PENDING", Language.getLangLabel("Pending"));
    attributes.put("LANG_VALID", Language.getLangLabel("valid"));    
    attributes.put("LANG_UPDATE", Language.getLangLabel("Update"));      
    attributes.put("LANG_TRANSFER_REGISTER", Language.getLangLabel("Transfer register"));  
    attributes.put("LANG_AWAIT_YOUR_CONFIRM", Language.getLangLabel("Awaiting your confirm"));  
    attributes.put("LANG_AWAIT_YOUR_RECEIPT", Language.getLangLabel("Awaiting your receipt"));  
    
    
    return attributes;
  }

  public Map<String, Object> handleOdinUpdateRequest(Request request) {
    Map<String, Object> attributes = new HashMap<String, Object>();
    request.session(true);
    
    attributes = updateCommonStatus(request, attributes);
    attributes.put("title", "Update an ODIN's baseinfo");
    
    String odin=request.queryParams("odin");
    if(odin==null){
        attributes.put("error", "handleOdinUpdateRequest: no odin.");
        return attributes;
    } 
       
    Blocks blocks = Blocks.getInstance();
    String address=(String)attributes.get("address");
    
    OdinInfo odinInfo=Odin.getOdinInfo(odin);

    if(odinInfo==null){
      attributes.put("error", "handleOdinUpdateRequest Invalid odin.");
    }else if (request.queryParams().contains("form") && request.queryParams("form").equals("update-odin-baseinfo")) {
      logger.info("************* do update-odin **************");

      String admin = request.queryParams("admin");
      String titleStr=request.queryParams("title");
      String emailStr=request.queryParams("email");
      String authSet=request.queryParams("auth");

      if( titleStr.length()>0 ){
        try {
            HashMap<String,Object> map = new HashMap<String,Object>();
            map.put("full_odin", odinInfo.fullOdin);
            map.put("short_odin", odinInfo.shortOdin.toString());
            map.put("register", odinInfo.register);
            map.put("admin", admin);
            map.put("tx_index",odinInfo.txIndex.toString());
            map.put("tx_hash", odinInfo.txHash);
            map.put("block_index", odinInfo.blockIndex.toString());
            map.put("block_time", Util.timeFormat(odinInfo.blockTime));
            map.put("validity",odinInfo.validity);

            JSONObject odin_set = odinInfo.odinSet; 
            map=Odin.parseOdinSet(map,odin_set,address,odinInfo.register,odinInfo.admin);

            if(map.containsKey("me_updatable"))
            {
                Map mapNewOdinSet = new HashMap(); 
                boolean  needBroadcast=false;
            
                mapNewOdinSet.put("ver", Config.ODIN_PROTOCOL_VER); 
                mapNewOdinSet.put("cmd", Config.ODIN_CMD_UPDATE_BASE_INFO); 
                
                if(!admin.equals(odinInfo.admin) ){ //Need update while admin was changed
                  needBroadcast=true;
                }else{
                  admin=""; //Not need update admin while not changed
                }
                
                if(!titleStr.equals(map.get("title").toString()) ){//Only value that changed need to be updated
                  mapNewOdinSet.put("title", titleStr); 
                  needBroadcast=true;
                }
                
                if(!emailStr.equals(map.get("email").toString()) ) {
                  mapNewOdinSet.put("email", emailStr); 
                  needBroadcast=true;
                }
                
                if(!authSet.equals(map.get("auth").toString()) ) {
                  mapNewOdinSet.put("auth", authSet); 
                  needBroadcast=true;
                }
                
                if(needBroadcast){
                  JSONObject new_odin_set = new JSONObject(mapNewOdinSet); 
                  
                  Transaction tx = OdinUpdate.updateOdinBaseInfo(odinInfo.fullOdin,address,admin,new_odin_set);
                  blocks.sendTransaction(address,tx);
                  attributes.put("success", Language.getLangLabel("Your request had been submited. Please wait confirms for at least 1 block."));
                } else {
                  attributes.put("warning", Language.getLangLabel("Please make sure that you have changed some values then submit."));
                }
            } else {
                attributes.put("error", Language.getLangLabel("No permission."));
            }
            
            map.put("title", titleStr);
            map.put("email", emailStr);  
            map.put("auth", authSet); 

            attributes.put("odin", map);  
        } catch (Exception e) {
          logger.error("************* do update-odin error: "+e.getMessage());
          attributes.put("error", e.getMessage());
        }
      } else {
        attributes.put("error", Language.getLangLabel("Please input valid title."));
      }
    } else {  
      HashMap<String,Object> map = new HashMap<String,Object>();
      map.put("full_odin", odinInfo.fullOdin);
      map.put("short_odin", odinInfo.shortOdin.toString());
      map.put("register", odinInfo.register);
      map.put("admin", odinInfo.admin);
      map.put("tx_index",odinInfo.txIndex.toString());
      map.put("tx_hash", odinInfo.txHash);
      map.put("block_index", odinInfo.blockIndex.toString());
      map.put("block_time", Util.timeFormat(odinInfo.blockTime));
      map.put("validity",odinInfo.validity);
      
      try{
        JSONObject odin_set = odinInfo.odinSet; 
        map=Odin.parseOdinSet(map,odin_set,address,odinInfo.register,odinInfo.admin);

        attributes.put("odin", map);    
      }catch (Exception e) {
        logger.error(e.toString());
      }
    }          
    
    attributes.put("LANG_UPDATE_THE_ADMIN_SET_OF", Language.getLangLabel("Update the admin set of"));

    attributes.put("LANG_ODIN_ADMIN_ADDRESS", Language.getLangLabel("Admin BTC address"));
    attributes.put("LANG_ODIN_REGISTER_ADDRESS", Language.getLangLabel("Register BTC address"));
    
    attributes.put("LANG_ODIN_TITLE", Language.getLangLabel("ODIN title"));
    attributes.put("LANG_THE_PUBLIC_EMAIL_FOR", Language.getLangLabel("The public email of the admin"));
    attributes.put("LANG_ODIN_AP", Language.getLangLabel("Access Point"));
    attributes.put("LANG_ODIN_AP_URL_SHOULD_BE", Language.getLangLabel("the access point URL"));
    attributes.put("LANG_EMAIL", Language.getLangLabel("Email"));  
    attributes.put("LANG_ODIN_AUTHORITY", Language.getLangLabel("Authority"));    
    attributes.put("LANG_THE_REGISTER_OR_ADMIN_CAN_UPDATE", Language.getLangLabel("The register or admin can update"));
    attributes.put("LANG_ONLY_THE_ADMIN_CAN_UPDATE", Language.getLangLabel("Only the admin can update"));
    attributes.put("LANG_REGISTER_AND_ADMIN_MUST_UPDATE_TOGETHER", Language.getLangLabel("Register and admin must update together"));
    attributes.put("LANG_SUBMIT_TO_UPDATE", Language.getLangLabel("Submit to update"));
    attributes.put("LANG_SUBMIT_TO_CREATE_FIRST_AP", Language.getLangLabel("Create your first AP sample based Blockchain ..."));

    attributes.put("LANG_OPTIONAL", Language.getLangLabel("Optional"));
    attributes.put("LANG_UPDATE_IT", Language.getLangLabel("Update it"));  
    attributes.put("LANG_UPDATE_BASEINFO", Language.getLangLabel("Update base info")); 
    attributes.put("LANG_UPDATE_AP_SET", Language.getLangLabel("Update AP list")); 
    attributes.put("LANG_UPDATE_VD_SET", Language.getLangLabel("Validtion setting"));  
    attributes.put("LANG_TRANSFER_REGISTER", Language.getLangLabel("Transfer register"));  
    attributes.put("LANG_CLICKED_WAITING", Language.getLangLabel("Waiting"));  
        
    return attributes;
  }
  
  public Map<String, Object> handleOdinUpdateApSetRequest(Request request) {
    Map<String, Object> attributes = new HashMap<String, Object>();
    request.session(true);
    
    attributes = updateCommonStatus(request, attributes);
    attributes.put("title", "Update an ODIN's AP setting");
    
    String odin=request.queryParams("odin");
    if(odin==null){
        attributes.put("error", "handleOdinUpdateApSetRequest: no odin.");
        return attributes;
    } 
       
    Blocks blocks = Blocks.getInstance();
    String address=(String)attributes.get("address");
    
    OdinInfo odinInfo=Odin.getOdinInfo(odin);

    if(odinInfo==null){
      attributes.put("error", "handleOdinUpdateApSetRequest Invalid odin.");
    }else if (request.queryParams().contains("form") && request.queryParams("form").equals("update-odin-aplist")) {
      logger.info("************* do update-odin-aplist **************");

      try {
          HashMap<String,Object> map = new HashMap<String,Object>();
          map.put("full_odin", odinInfo.fullOdin);
          map.put("short_odin", odinInfo.shortOdin.toString());
          map.put("register", odinInfo.register);
          map.put("admin", odinInfo.admin);
          map.put("tx_index",odinInfo.txIndex.toString());
          map.put("tx_hash", odinInfo.txHash);
          map.put("block_index", odinInfo.blockIndex.toString());
          map.put("block_time", Util.timeFormat(odinInfo.blockTime));
          map.put("validity",odinInfo.validity);

          JSONObject odin_set = odinInfo.odinSet; 
          map=Odin.parseOdinSet(map,odin_set,address,odinInfo.register,odinInfo.admin);

          JSONObject apUpdate = new JSONObject(); 
          for(int tt=0;tt<5;tt++){
              String tmp_ap_url_str = request.queryParams("ap"+tt+"_url");
              if(tmp_ap_url_str==null)
                tmp_ap_url_str="";
              else
                tmp_ap_url_str.trim();
              
              String old_ap_url_str=(String)map.get("ap"+tt+"_url");
              if(old_ap_url_str==null)
                old_ap_url_str="";

              System.out.println("tmp_ap_url_str="+tmp_ap_url_str+"  oldurl="+old_ap_url_str);
              if( !tmp_ap_url_str.equals(old_ap_url_str) ){
                  Map map_new_ap_record = new HashMap(); 
                  map_new_ap_record.put("url", tmp_ap_url_str); 
                  apUpdate.put(""+tt,new JSONObject(map_new_ap_record));
              } 
              map.put("ap"+tt+"_url", tmp_ap_url_str);
          }
          
          if(map.containsKey("me_updatable"))
          {
              if( apUpdate.length()==0 ){
                  attributes.put("error", Language.getLangLabel("Please update at least one valid access point."));
              } else {
                  Transaction tx = OdinUpdate.updateOdinApSet(odinInfo.fullOdin,address,apUpdate);
                  blocks.sendTransaction(address,tx);
                  attributes.put("success", Language.getLangLabel("Your request had been submited. Please wait confirms for at least 1 block."));
              }
          } else {
              attributes.put("error", Language.getLangLabel("No permission."));
          }

          attributes.put("odin", map);  
      } catch (Exception e) {
        logger.error("************* do update-odin-aplist error: "+e.getMessage());
        attributes.put("error", e.getMessage());
      }
    } else {  
      HashMap<String,Object> map = new HashMap<String,Object>();
      map.put("full_odin", odinInfo.fullOdin);
      map.put("short_odin", odinInfo.shortOdin.toString());
      map.put("register", odinInfo.register);
      map.put("admin", odinInfo.admin);
      map.put("tx_index",odinInfo.txIndex.toString());
      map.put("tx_hash", odinInfo.txHash);
      map.put("block_index", odinInfo.blockIndex.toString());
      map.put("block_time", Util.timeFormat(odinInfo.blockTime));
      map.put("validity",odinInfo.validity);
      
      try{
        JSONObject odin_set = odinInfo.odinSet; 
        map=Odin.parseOdinSet(map,odin_set,address,odinInfo.register,odinInfo.admin);

        attributes.put("odin", map);    
      }catch (Exception e) {
        logger.error(e.toString());
      }
    }          
    
    attributes.put("LANG_ODIN_TITLE", Language.getLangLabel("ODIN title"));
    attributes.put("LANG_THE_PUBLIC_EMAIL_FOR", Language.getLangLabel("The public email of the admin"));
    attributes.put("LANG_ODIN_AP", Language.getLangLabel("Access Point"));
    attributes.put("LANG_ODIN_AP_URL_SHOULD_BE", Language.getLangLabel("the access point URL"));
    attributes.put("LANG_EMAIL", Language.getLangLabel("Email"));  
    attributes.put("LANG_ODIN_AUTHORITY", Language.getLangLabel("Authority"));    
    attributes.put("LANG_THE_REGISTER_OR_ADMIN_CAN_UPDATE", Language.getLangLabel("The register or admin can update"));
    attributes.put("LANG_ONLY_THE_ADMIN_CAN_UPDATE", Language.getLangLabel("Only the admin can update"));
    attributes.put("LANG_REGISTER_AND_ADMIN_MUST_UPDATE_TOGETHER", Language.getLangLabel("Register and admin must update together"));
    attributes.put("LANG_SUBMIT_TO_UPDATE", Language.getLangLabel("Submit to update"));
    attributes.put("LANG_SUBMIT_TO_CREATE_FIRST_AP", Language.getLangLabel("Create your first AP sample based Blockchain ..."));
    
    attributes.put("LANG_OPTIONAL", Language.getLangLabel("Optional"));
    attributes.put("LANG_UPDATE_IT", Language.getLangLabel("Update it"));  
    attributes.put("LANG_DELETE_IT", Language.getLangLabel("Delete it"));  
    attributes.put("LANG_UPDATE_BASEINFO", Language.getLangLabel("Update base info")); 
    attributes.put("LANG_UPDATE_AP_SET", Language.getLangLabel("Update AP list")); 
    attributes.put("LANG_UPDATE_VD_SET", Language.getLangLabel("Validtion setting"));  
    attributes.put("LANG_TRANSFER_REGISTER", Language.getLangLabel("Transfer register"));  
    attributes.put("LANG_CLICKED_WAITING", Language.getLangLabel("Waiting"));  
        
    return attributes;
  }
  
  public Map<String, Object> handleOdinCreateFirstApRequest(Request request) {
    Map<String, Object> attributes = new HashMap<String, Object>();
    request.session(true);
    
    attributes = updateCommonStatus(request, attributes);
    attributes.put("title", "Create AP sample based Blockchain");
    
    String odin=request.queryParams("odin");
    if(odin==null){
        attributes.put("error", "handleOdinCreateFirstApRequest: no odin.");
        return attributes;
    } 
       
    Blocks blocks = Blocks.getInstance();
    String address=(String)attributes.get("address");
    
    OdinInfo odinInfo=Odin.getOdinInfo(odin);

    if(odinInfo==null){
      attributes.put("error", "handleOdinCreateFirstApRequest Invalid odin.");
    }else if (request.queryParams().contains("form") && request.queryParams("form").equals("odin-create-first-ap")) {
      logger.info("************* do odin-create-first-ap **************");

      try {
          HashMap<String,Object> map = new HashMap<String,Object>();
          map.put("full_odin", odinInfo.fullOdin);
          map.put("short_odin", odinInfo.shortOdin.toString());
          map.put("register", odinInfo.register);
          map.put("admin", odinInfo.admin);
          map.put("tx_index",odinInfo.txIndex.toString());
          map.put("tx_hash", odinInfo.txHash);
          map.put("block_index", odinInfo.blockIndex.toString());
          map.put("block_time", Util.timeFormat(odinInfo.blockTime));
          map.put("validity",odinInfo.validity);

          JSONObject odin_set = odinInfo.odinSet; 
          map=Odin.parseOdinSet(map,odin_set,address,odinInfo.register,odinInfo.admin);

          JSONObject apUpdate = new JSONObject(); 
          
          //Generate PTTP data package of the sample page 
          String tmp_page_title = request.queryParams("ap_page_title");
          String tmp_page_content = request.queryParams("ap_page_content");
          String chunk_content="<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>"+tmp_page_title+"</title><meta content=\"IE=edge\" http-equiv=\"X-UA-Compatible\"><meta content=\"width=device-width, initial-scale=1\" name=\"viewport\"></head><body><h2>"+tmp_page_title+"</h2>"+tmp_page_content+"</body></html>";
          
          String sample_ppk_uri=  Config.PPK_URI_PREFIX + odinInfo.fullOdin +"/#" + Util.getNowTimestamp();
          System.out.println("sample_ppk_uri="+sample_ppk_uri);
          
          JSONObject obj_chunk_metainfo=new JSONObject();
          obj_chunk_metainfo.put("chunk_index", 0 );
          obj_chunk_metainfo.put("chunk_count", 1 );
          obj_chunk_metainfo.put("content_type", "text/html"  );
          obj_chunk_metainfo.put("content_length", chunk_content.length()  );

          JSONObject obj_newest_ap_chunk=new JSONObject();
          obj_newest_ap_chunk.put("uri",sample_ppk_uri);
          obj_newest_ap_chunk.put("status_code",200);
          obj_newest_ap_chunk.put("status_info","OK");

          obj_newest_ap_chunk.put("metainfo",obj_chunk_metainfo);
          obj_newest_ap_chunk.put("content",chunk_content);

          JSONObject obj_newest_ap_data=new JSONObject();
          obj_newest_ap_data.put("ver",1);
          obj_newest_ap_data.put("data",obj_newest_ap_chunk.toString());
          obj_newest_ap_data.put("sign","");

          String tmp_ap_url_str = Util.uploadToBtmfs(obj_newest_ap_data.toString().getBytes(Config.PPK_TEXT_CHARSET));
          if(tmp_ap_url_str!=null && tmp_ap_url_str.length()>0 ){
              System.out.println("tmp_ap_url_str="+tmp_ap_url_str);

              Map map_new_ap_record = new HashMap(); 
              map_new_ap_record.put("url", tmp_ap_url_str); 
              apUpdate.put("0",new JSONObject(map_new_ap_record));
              map.put("ap0_url", tmp_ap_url_str);
              
              
              if(map.containsKey("me_updatable"))
              {
                  if( apUpdate.length()==0 ){
                      attributes.put("error", Language.getLangLabel("Please update at least one valid access point."));
                  } else {
                      Transaction tx = OdinUpdate.updateOdinApSet(odinInfo.fullOdin,address,apUpdate);
                      blocks.sendTransaction(address,tx);
                      attributes.put("success", Language.getLangLabel("Your request had been submited. Please wait confirms for at least 1 block."));
                  }
              } else {
                  attributes.put("error", Language.getLangLabel("No permission."));
              }
          }else{
              attributes.put("error", Language.getLangLabel("Invalid Inputs"));
          }
          
          attributes.put("odin", map);  
      } catch (Exception e) {
        logger.error("************* do odin-create-first-ap error: "+e.getMessage());
        attributes.put("error", e.getMessage());
      }
    } else {  
      HashMap<String,Object> map = new HashMap<String,Object>();
      map.put("full_odin", odinInfo.fullOdin);
      map.put("short_odin", odinInfo.shortOdin.toString());
      map.put("register", odinInfo.register);
      map.put("admin", odinInfo.admin);
      map.put("tx_index",odinInfo.txIndex.toString());
      map.put("tx_hash", odinInfo.txHash);
      map.put("block_index", odinInfo.blockIndex.toString());
      map.put("block_time", Util.timeFormat(odinInfo.blockTime));
      map.put("validity",odinInfo.validity);
      
      try{
        JSONObject odin_set = odinInfo.odinSet; 
        map=Odin.parseOdinSet(map,odin_set,address,odinInfo.register,odinInfo.admin);

        attributes.put("odin", map);    
      }catch (Exception e) {
        logger.error(e.toString());
      }
    }          
    
    attributes.put("LANG_CREATE_AP_SAMPLE", Language.getLangLabel("Create AP sample based Blockchain"));
    attributes.put("LANG_CREATE_AP_SAMPLE_TITLE", Language.getLangLabel("Page title"));
    attributes.put("LANG_CREATE_AP_SAMPLE_CONTENT", Language.getLangLabel("Page Content"));

    
    attributes.put("LANG_ODIN_TITLE", Language.getLangLabel("ODIN title"));
    attributes.put("LANG_THE_PUBLIC_EMAIL_FOR", Language.getLangLabel("The public email of the admin"));
    attributes.put("LANG_ODIN_AP", Language.getLangLabel("Access Point"));
    attributes.put("LANG_ODIN_AP_URL_SHOULD_BE", Language.getLangLabel("the access point URL"));
    attributes.put("LANG_EMAIL", Language.getLangLabel("Email"));  
    attributes.put("LANG_ODIN_AUTHORITY", Language.getLangLabel("Authority"));    
    attributes.put("LANG_THE_REGISTER_OR_ADMIN_CAN_UPDATE", Language.getLangLabel("The register or admin can update"));
    attributes.put("LANG_ONLY_THE_ADMIN_CAN_UPDATE", Language.getLangLabel("Only the admin can update"));
    attributes.put("LANG_REGISTER_AND_ADMIN_MUST_UPDATE_TOGETHER", Language.getLangLabel("Register and admin must update together"));
    attributes.put("LANG_SUBMIT_TO_UPDATE", Language.getLangLabel("Submit to update"));
    
    attributes.put("LANG_OPTIONAL", Language.getLangLabel("Optional"));
    attributes.put("LANG_UPDATE_BASEINFO", Language.getLangLabel("Update base info")); 
    attributes.put("LANG_UPDATE_AP_SET", Language.getLangLabel("Update AP list")); 
    attributes.put("LANG_UPDATE_VD_SET", Language.getLangLabel("Validtion setting"));  
    attributes.put("LANG_TRANSFER_REGISTER", Language.getLangLabel("Transfer register"));  
    attributes.put("LANG_CLICKED_WAITING", Language.getLangLabel("Waiting"));  
        
    return attributes;
  }
  
  public Map<String, Object> handleOdinUpdateVdSetRequest(Request request) {
    Map<String, Object> attributes = new HashMap<String, Object>();
    request.session(true);
    
    attributes = updateCommonStatus(request, attributes);
    attributes.put("title", "Update an ODIN's validtion setting");
    
    String odin=request.queryParams("odin");
    if(odin==null){
        attributes.put("error", "handleOdinUpdateVdSetRequest: no odin.");
        return attributes;
    } 
       
    Blocks blocks = Blocks.getInstance();
    String address=(String)attributes.get("address");
    
    OdinInfo odinInfo=Odin.getOdinInfo(odin);

    if(odinInfo==null){
      attributes.put("error", "handleOdinUpdateApSetRequest Invalid odin.");
    }else if (request.queryParams().contains("form") && request.queryParams("form").equals("update-odin-vdset")) {
      logger.info("************* do update-odin-vdset **************");

      try {
          HashMap<String,Object> map = new HashMap<String,Object>();
          map.put("full_odin", odinInfo.fullOdin);
          map.put("short_odin", odinInfo.shortOdin.toString());
          map.put("register", odinInfo.register);
          map.put("admin", odinInfo.admin);
          map.put("tx_index",odinInfo.txIndex.toString());
          map.put("tx_hash", odinInfo.txHash);
          map.put("block_index", odinInfo.blockIndex.toString());
          map.put("block_time", Util.timeFormat(odinInfo.blockTime));
          map.put("validity",odinInfo.validity);

          JSONObject odin_set = odinInfo.odinSet; 
          map=Odin.parseOdinSet(map,odin_set,address,odinInfo.register,odinInfo.admin);

          String new_vd_set_cert_uri=request.queryParams("new_vd_set_cert_uri");
          String new_vd_set_algo=request.queryParams("new_vd_set_algo");
          //String new_vd_set_format=request.queryParams("new_vd_set_format");
          String new_vd_set_pubkey=request.queryParams("new_vd_set_pubkey");
          
          if(new_vd_set_cert_uri!=null){
             new_vd_set_cert_uri=new_vd_set_cert_uri.trim();
             attributes.put("new_vd_set_cert_uri", new_vd_set_cert_uri);
             if( new_vd_set_cert_uri.length()==0 ){
                  attributes.put("error", Language.getLangLabel("Please input valid URI of certificate."));
             }
          }else if(new_vd_set_pubkey!=null && new_vd_set_pubkey.trim().length()>0 ){
            new_vd_set_cert_uri=Util.uploadToIpfs(new_vd_set_pubkey);
            if( new_vd_set_cert_uri==null){
              attributes.put("warning", Language.getLangLabel("The IPFS service is unusable. So try to store the pubkey to blockchain."));
              
              new_vd_set_cert_uri="data:,"+new_vd_set_pubkey;
            }
            
            attributes.put("new_vd_set_algo", new_vd_set_algo);
            //attributes.put("new_vd_set_format", new_vd_set_format);
            attributes.put("new_vd_set_pubkey", new_vd_set_pubkey);
          }
          
          if(map.containsKey("me_updatable"))
          {
              if( new_vd_set_cert_uri!=null && new_vd_set_cert_uri.length()>0 ){
                  JSONObject new_vd_set=new JSONObject();
                  new_vd_set.put(Config.JSON_KEY_PPK_ALGO,new_vd_set_algo);
                  new_vd_set.put(Config.JSON_KEY_PPK_CERT_URI,new_vd_set_cert_uri);
                  Transaction tx = OdinUpdate.updateOdinVdSet(odinInfo.fullOdin,address,new_vd_set);
                  blocks.sendTransaction(address,tx);
                  attributes.put("success", Language.getLangLabel("Your request had been submited. Please wait confirms for at least 1 block."));
              }
          } else {
              attributes.put("error", Language.getLangLabel("No permission."));
          }

          attributes.put("odin", map);  
      } catch (Exception e) {
        logger.error("************* do update-odin-vdset error: "+e.getMessage());
        attributes.put("error", e.getMessage());
      }
    } else {  
      HashMap<String,Object> map = new HashMap<String,Object>();
      map.put("full_odin", odinInfo.fullOdin);
      map.put("short_odin", odinInfo.shortOdin.toString());
      map.put("register", odinInfo.register);
      map.put("admin", odinInfo.admin);
      map.put("tx_index",odinInfo.txIndex.toString());
      map.put("tx_hash", odinInfo.txHash);
      map.put("block_index", odinInfo.blockIndex.toString());
      map.put("block_time", Util.timeFormat(odinInfo.blockTime));
      map.put("validity",odinInfo.validity);
      
      try{
        JSONObject odin_set = odinInfo.odinSet; 
        map=Odin.parseOdinSet(map,odin_set,address,odinInfo.register,odinInfo.admin);

        if(!map.containsKey("vd_set_cert_uri")){//如果尚未设置内容验证证书，系统将为用户自动产生RSA公私钥
          JSONObject keyMap=Util.getRSAKeys(odinInfo.fullOdin,true,true);
          
          String publicKey = RSACoder.getPublicKey(keyMap);  
          String privateKey = RSACoder.getPrivateKey(keyMap);            

          attributes.put("new_vd_set_algo", RSACoder.DEFAULT_SIGNATURE_ALGORITHM);
          attributes.put("new_vd_set_pubkey", publicKey);
          attributes.put("new_vd_set_prvkey", privateKey);
        }

        attributes.put("odin", map);    
      }catch (Exception e) {
        logger.error(e.toString());
      }
    }          
    
    attributes.put("LANG_UPDATE_THE_VD_SET_OF", Language.getLangLabel("Update the validtion set of"));

    attributes.put("LANG_CURRENT_VALIDTION_SETTING", Language.getLangLabel("Current Validtion Setting"));
    attributes.put("LANG_URI", Language.getLangLabel("URI"));
    attributes.put("LANG_ALGORITHM", Language.getLangLabel("Algorithm"));
    attributes.put("LANG_PUBLIC_KEY", Language.getLangLabel("Public key"));
    attributes.put("LANG_PRIVATE_KEY", Language.getLangLabel("Private key"));
    attributes.put("LANG_YOU_CAN_GENERATE_THE_PUBLIC_KEY_BY_YOURSELF", Language.getLangLabel("You can generate the public key by yourself and save it to a trusted storage service on the network, then fill its resource URI here."));
    attributes.put("LANG_GENERATE_PUBLIC_AND_PRIVATE_KEYS_HERE", Language.getLangLabel("Or generate public and private keys automatically here and save the public key to the IPFS distributed storage service for public verification use."));
    attributes.put("LANG_UPDATE_VALIDTION_SETTING", Language.getLangLabel("Update Validtion Setting"));
    attributes.put("LANG_PLEASE_BACKUP_THE_PRIVATE_KEY", Language.getLangLabel("Please backup the private key."));  
    attributes.put("LANG_SUBMIT_TO_UPDATE", Language.getLangLabel("Submit to update"));
    
    attributes.put("LANG_OPTIONAL", Language.getLangLabel("Optional"));
    attributes.put("LANG_UPDATE_IT", Language.getLangLabel("Update it"));  
    attributes.put("LANG_DELETE_IT", Language.getLangLabel("Delete it"));  
    attributes.put("LANG_UPDATE_BASEINFO", Language.getLangLabel("Update base info")); 
    attributes.put("LANG_UPDATE_AP_SET", Language.getLangLabel("Update AP list")); 
    attributes.put("LANG_UPDATE_VD_SET", Language.getLangLabel("Validtion setting"));  
    attributes.put("LANG_TRANSFER_REGISTER", Language.getLangLabel("Transfer register"));  
    attributes.put("LANG_CLICKED_WAITING", Language.getLangLabel("Waiting"));  
        
    return attributes;
  }
    
  public Map<String, Object> handleOdinTransRequest(Request request) {
    Map<String, Object> attributes = new HashMap<String, Object>();
    request.session(true);
    
    attributes = updateCommonStatus(request, attributes);
    attributes.put("title", "Transfer an ODIN");
    
    String odin=request.queryParams("odin");
    if(odin==null){
      attributes.put("error", "handleOdinTransRequest : no odin.");
      return attributes;
    } 
        
    Blocks blocks = Blocks.getInstance();
    String address=(String)attributes.get("address");

    OdinInfo odinInfo=Odin.getOdinInfo(odin);

    if(odinInfo==null){
      attributes.put("error", "handleOdinTransRequest : Invalid odin.");
    }else if (request.queryParams().contains("form") && request.queryParams("form").equals("trans-register")) {
      logger.info("************* do trans-register **************");

      String new_register = request.queryParams("new_register");

      if( new_register.length()>0 && !new_register.equals(address) ){
        try {
            HashMap<String,Object> map = new HashMap<String,Object>();
            map.put("full_odin", odinInfo.fullOdin);
            map.put("short_odin", odinInfo.shortOdin.toString());
            map.put("register", odinInfo.register);
            map.put("validity",odinInfo.validity);

            JSONObject odin_set = odinInfo.odinSet; 
            map=Odin.parseOdinSet(map,odin_set,address,odinInfo.register,odinInfo.admin);

            if(map.containsKey("me_updatable"))
            {
                Transaction tx = OdinUpdate.transOdinRegister(odinInfo.fullOdin,address,new_register);
                blocks.sendTransaction(address,tx);
                attributes.put("success", Language.getLangLabel("Your request had been submited. Please wait confirms for at least 1 block."));
            } else {
                attributes.put("error", Language.getLangLabel("No permission."));
            }

            attributes.put("odin", map);  
        } catch (Exception e) {
          logger.error("************* do trans-odin error: "+e.getMessage());
          attributes.put("error", e.getMessage());
        }
      } else {
        attributes.put("error", Language.getLangLabel("Please input another valid register address."));
      }
    } else {  
      HashMap<String,Object> map = new HashMap<String,Object>();
      map.put("full_odin", odinInfo.fullOdin);
      map.put("short_odin", odinInfo.shortOdin.toString());
      map.put("register", odinInfo.register);
      map.put("validity",odinInfo.validity);
      
      try{
        JSONObject odin_set = odinInfo.odinSet; 
        map=Odin.parseOdinSet(map,odin_set,address,odinInfo.register,odinInfo.admin);

        attributes.put("odin", map);    
      }catch (Exception e) {
        logger.error(e.toString());
      }
    }          

    attributes.put("LANG_TRANSFER_THE_REGISTER_OF", Language.getLangLabel("Transfer the register of"));
    attributes.put("LANG_ODIN_REGISTER_ADDRESS", Language.getLangLabel("Register BTC address"));
    attributes.put("LANG_ODIN_NEW_REGISTER_ADDRESS", Language.getLangLabel("The new register address"));
    attributes.put("LANG_TRANSFER_REGISTER", Language.getLangLabel("Transfer register"));  
    attributes.put("LANG_CLICKED_WAITING", Language.getLangLabel("Waiting"));  
        
    return attributes;
  }
    
  public Map<String, Object> handleOdinConfirmRequest(Request request) {
    Map<String, Object> attributes = new HashMap<String, Object>();
    request.session(true);
    
    attributes = updateCommonStatus(request, attributes);
    attributes.put("title", "Confirm ODIN updates");
    
    String odin=request.queryParams("odin");
    if(odin==null){
      attributes.put("error", "handleOdinConfirmRequest : no odin.");
      return attributes;
    } 

    Blocks blocks = Blocks.getInstance();
    String address=(String)attributes.get("address");
    
    OdinInfo odinInfo=Odin.getOdinInfo(odin);
    if(odinInfo==null){
      attributes.put("error", "handleOdinConfirmRequest: Invalid odin.");
      return attributes;
    }
    
    logger.info("************* do confirm-update **************");
    
    JSONArray confirm_update_list=new JSONArray();
    int awaiting_update_log_num=Integer.parseInt(request.queryParams("awaiting_update_log_num"));
    
    try {
        for(int sn=0; sn<awaiting_update_log_num;sn++ ){
          String awaiting_update_log_id=request.queryParams("update_log_id"+sn);

          if(awaiting_update_log_id!=null){
              confirm_update_list.put(awaiting_update_log_id);
          }
        }
        if(confirm_update_list.length()>0){
            Transaction tx = OdinUpdate.confirmUpdate(odinInfo.fullOdin,address,confirm_update_list);
            blocks.sendTransaction(address,tx);
            attributes.put("success", Language.getLangLabel("Your request had been submited. Please wait confirms for at least 1 block."));
        }else{
            attributes.put("error", Language.getLangLabel("Invalid awaiting_update_log_id."));
        }
    } catch (Exception e) {
      e.printStackTrace();
      logger.error("************* do confirm-update error: "+e.getMessage());
      attributes.put("error", e.getMessage());
    }
        
    return attributes;
  }
  
  public Map<String, Object> handleOdinDetailRequest(Request request) {
    Map<String, Object> attributes = new HashMap<String, Object>();
    request.session(true);
    
    attributes = updateCommonStatus(request, attributes);
    attributes.put("title", "View an ODIN");
    
    String odin=request.queryParams("odin");
    if(odin==null){
        attributes.put("error", "handleOdinDetailRequest : no odin.");
        return attributes;
    } 
    
    
    Blocks blocks = Blocks.getInstance();
    String address=(String)attributes.get("address");
    
    OdinInfo odinInfo=Odin.getOdinInfo(odin);

    if(odinInfo==null){
      attributes.put("error", "handleOdinDetailRequest: Invalid odin.");
      return attributes;
    } 
  
    HashMap<String,Object> map = new HashMap<String,Object>();
    map.put("full_odin", odinInfo.fullOdin);
    map.put("short_odin", odinInfo.shortOdin.toString());
    map.put("register", odinInfo.register);
    map.put("admin", odinInfo.admin);
    map.put("tx_index",odinInfo.txIndex.toString());
    map.put("tx_hash", odinInfo.txHash);
    map.put("block_index", odinInfo.blockIndex.toString());
    map.put("block_time", Util.timeFormat(odinInfo.blockTime));
    map.put("validity",odinInfo.validity);
    
    map.put("escaped_list_debug", Odin.getEscapedListOfShortODIN(odinInfo.shortOdin).toString());
    
    try{
      String awaiting_update_log_id=request.queryParams("awaiting_update_log_id");
      
      JSONObject odin_set = odinInfo.odinSet; 
      map=Odin.parseOdinSet(map,odin_set,address,odinInfo.register,odinInfo.admin);
      attributes.put("odin", map);   
      
      //get valid updates that awaiting my confirm or receipt 
      String sql_str="select l.log_id from odins cp,odin_update_logs l where  (cp.full_odin='"+odin+"' or cp.short_odin='"+odin+"')  AND ( ( l.validity='awaiting' and l.full_odin=cp.full_odin and l.required_confirmer='"+address+"' and (cp.register=l.required_confirmer or cp.admin=l.required_confirmer ) ) or (l.validity='receipting' and l.full_odin=cp.full_odin and  l.destination='"+address+"') ) ";
      
      if(awaiting_update_log_id!=null) //指定某条更新记录
        sql_str+=" and l.log_id='"+awaiting_update_log_id+"'";
      else
        sql_str+=" order by l.log_id;";

      //System.out.println("sql_str="+sql_str);
      Database db = Database.getInstance();
      ResultSet rs = db.executeQuery(sql_str);
      
      try {
        ArrayList<HashMap<String, Object>> update_logs = new ArrayList<HashMap<String, Object>>();
        
        int sn=0;
        while ( rs.next()) {
          awaiting_update_log_id = rs.getString("log_id");
          System.out.println("update_log_id="+awaiting_update_log_id);
          OdinUpdateInfo updateOdinInfo=OdinUpdate.getOdinUpdateInfo(awaiting_update_log_id);
          if(updateOdinInfo!=null && updateOdinInfo.updateSet!=null){
            HashMap<String,Object> map_log = new HashMap<String,Object>();
            
            map_log.put("log_sn", sn);
            map_log.put("log_id", updateOdinInfo.logId);
            map_log.put("block_index", updateOdinInfo.blockIndex);
            map_log.put("block_time", Util.timeFormat(updateOdinInfo.blockTime));
            map_log.put("updater", updateOdinInfo.updater);
            map_log.put("destination", updateOdinInfo.destination);
           
            map_log=OdinUpdate.parseOdinUpdateSet(map_log,updateOdinInfo.updater,updateOdinInfo.destination,updateOdinInfo.updateSet);
            
            update_logs.add(map_log);
            
            sn++;
          }else{
            System.out.println("meet invalid update log:"+awaiting_update_log_id);
          }
        }
        
        System.out.println("update_logs.size()="+update_logs.size());
        if(update_logs.size()>0){
          attributes.put("awaiting_update_log_num", update_logs.size());
          attributes.put("awaiting_update_logs", update_logs);
        }
      } catch (SQLException e) {
        e.printStackTrace();
        logger.error(e.toString());
      }
      
       
    }catch (Exception e) {
      e.printStackTrace();
      logger.error(e.toString());
    }

    attributes.put("LANG_CONFIRM_THE_BELOW_UPDATE_OF", Language.getLangLabel("Confirm the below update of"));
    attributes.put("LANG_VIEW_THE_DETAIL_OF", Language.getLangLabel("View the detail of"));

    attributes.put("LANG_ODIN_ADMIN_ADDRESS", Language.getLangLabel("Admin BTC address"));
    attributes.put("LANG_ODIN_REGISTER_ADDRESS", Language.getLangLabel("Register BTC address"));
    
    attributes.put("LANG_ODIN_TITLE", Language.getLangLabel("ODIN title"));
    attributes.put("LANG_THE_PUBLIC_EMAIL_FOR", Language.getLangLabel("The public email of the admin"));
    attributes.put("LANG_ODIN_AP", Language.getLangLabel("Access Point"));
    attributes.put("LANG_ODIN_AP_URL_SHOULD_BE", Language.getLangLabel("the access point URL"));
    attributes.put("LANG_EMAIL", Language.getLangLabel("Email"));  
    attributes.put("LANG_ODIN_AUTHORITY", Language.getLangLabel("Authority"));  
    attributes.put("LANG_ODIN_VALIDTION", Language.getLangLabel("Validtion"));  
    attributes.put("LANG_THE_REGISTER_OR_ADMIN_CAN_UPDATE", Language.getLangLabel("The register or admin can update"));
    attributes.put("LANG_ONLY_THE_ADMIN_CAN_UPDATE", Language.getLangLabel("Only the admin can update"));
    attributes.put("LANG_REGISTER_AND_ADMIN_MUST_UPDATE_TOGETHER", Language.getLangLabel("Register and admin must update together"));
    attributes.put("LANG_ESCAPED_LIST", Language.getLangLabel("Escaped names"));
    attributes.put("LANG_TIME", Language.getLangLabel("Time"));
    attributes.put("LANG_BLOCK", Language.getLangLabel("Block"));
    
    attributes.put("LANG_SUBMIT_TO_CREATE_FIRST_AP", Language.getLangLabel("Create your first AP sample based Blockchain ..."));
    
    attributes.put("LANG_OPTIONAL", Language.getLangLabel("Optional"));
    attributes.put("LANG_CONFIRM_THE_UPDATE", Language.getLangLabel("Confirm this update")); 
    attributes.put("LANG_UPDATE", Language.getLangLabel("Update"));          
    attributes.put("LANG_BROWSE_AP", Language.getLangLabel("Browse the Aeccss Point"));      
    attributes.put("LANG_TRANSFER_REGISTER", Language.getLangLabel("Transfer register"));  
    attributes.put("LANG_CLICKED_WAITING", Language.getLangLabel("Waiting"));  
        
    return attributes;
  }
  
  public Map<String, Object> handleOdinMatchRequest(Request request) {
    Map<String, Object> attributes = new HashMap<String, Object>();
    request.session(true);
    
    attributes = updateCommonStatus(request, attributes);
    attributes.put("title", "Match a word");
    
    String input_word=request.queryParams("word");
    if(input_word==null){
        attributes.put("matched_result", "handleOdinMatchRequest : no word.");
    } 
    
    String matched_odin=Odin.convertLetterToNumberInRootODIN(input_word);
    
    if(matched_odin==null){
      attributes.put("matched_result", Language.getLangLabel("No matched ODIN for the word!"));
    }else{
      OdinInfo odinInfo=Odin.getOdinInfo(matched_odin);
      if(odinInfo==null){
        attributes.put("matched_result", Language.getLangLabel("Matched ODIN")+"["+matched_odin+"]. "+Language.getLangLabel("The ODIN not appeared")  );
      }else{
        attributes.put("matched_result", Language.getLangLabel("Matched ODIN")+"["+matched_odin+"]."+" <a href='/odin-detail?odin="+matched_odin+"'>"+Language.getLangLabel("The ODIN had been registered by ") + odinInfo.register+"</a>" );
      }
    }

    attributes.put("LANG_MATCH_WORD", Language.getLangLabel("Match word"));
    attributes.put("input_word", input_word);

    return attributes;
  }
  
  public Map<String, Object> handleOdinCheckApVdRequest(Request request) {
    Map<String, Object> attributes = new HashMap<String, Object>();
    request.session(true);
    
    attributes = updateCommonStatus(request, attributes);
    attributes.put("title", "Validate an ODIN's AP");
    
    String odin=request.queryParams("odin");
    if(odin==null){
        attributes.put("error", "handleOdinCheckApVdRequest: no odin.");
        return attributes;
    } 
    
    String apid=request.queryParams("apid");
    if(apid==null){
        attributes.put("error", "handleOdinCheckApVdRequest: no apid.");
        return attributes;
    } 
    
       
    Blocks blocks = Blocks.getInstance();
    String address=(String)attributes.get("address");
    
    OdinInfo odinInfo=Odin.getOdinInfo(odin);

    if(odinInfo==null){
      attributes.put("error", "handleOdinCheckApVdRequest Invalid odin.");
      return attributes;
    }
    
    HashMap<String,Object> map = new HashMap<String,Object>();
    map.put("full_odin", odinInfo.fullOdin);
    map.put("short_odin", odinInfo.shortOdin.toString());
    map.put("register", odinInfo.register);
    map.put("admin", odinInfo.admin);
    map.put("tx_index",odinInfo.txIndex.toString());
    map.put("tx_hash", odinInfo.txHash);
    map.put("block_index", odinInfo.blockIndex.toString());
    map.put("block_time", Util.timeFormat(odinInfo.blockTime));
    map.put("validity",odinInfo.validity);
    
    try{
      JSONObject odin_set = odinInfo.odinSet; 
      map=Odin.parseOdinSet(map,odin_set,address,odinInfo.register,odinInfo.admin);
      attributes.put("odin", map);
      
      String ap_url=(String)map.get("ap"+apid+"_url");
      
      String vd_set_algo=(String)map.get("vd_set_algo");
      String vd_set_pubkey=(String)map.get("vd_set_pubkey");
      
      //暂时允许不配置验证参数也可以获取内容, chenhui,20180309
      //if(vd_set_algo==null || vd_set_pubkey==null){
      //  attributes.put("error", "Invalid algorithm or pubkey. Please update the validtion setting.");
      //  return attributes;
      //}
      
      JSONObject obj_ap_resp=PPkURI.fetchAndValidationAP(
            Config.PPK_URI_PREFIX+odinInfo.fullOdin+"/",
            odinInfo.fullOdin,
            odin_set.getJSONObject("ap_set").getJSONObject(apid) ,
            odin_set.optJSONObject("vd_set")
        );
      
      String ap_resp_content="";
      String ap_resp_ppk_uri="";
      String ap_resp_url="";
      String ap_resp_sign="";
      String ap_resp_validate_result="<font color='#F00'>Invalid</font>";
      
      if(obj_ap_resp!=null){
        ap_resp_url=obj_ap_resp.optString(Config.JSON_KEY_PPK_CHUNK_URL,"");
        if( obj_ap_resp.optString(Config.JSON_KEY_PPK_CHUNK_TYPE,"").toLowerCase().startsWith("text") )
          ap_resp_content = new String( (byte[])obj_ap_resp.opt(Config.JSON_KEY_PPK_CHUNK) );
        else
          ap_resp_content = obj_ap_resp.optString(Config.JSON_KEY_PPK_CHUNK_TYPE,"No defined content type");
        
        ap_resp_ppk_uri = obj_ap_resp.optString(Config.JSON_KEY_PPK_URI);
        ap_resp_sign = obj_ap_resp.optString(Config.JSON_KEY_PPK_SIGN);
        
        int validcode=obj_ap_resp.optInt(Config.JSON_KEY_PPK_VALIDATION,Config.PPK_VALIDATION_ERROR);
        if( validcode == Config.PPK_VALIDATION_IGNORED )
           ap_resp_validate_result="<font color='#F72'>Valiade ignored! The content unable to be identified. </font>";
        else if( validcode == Config.PPK_VALIDATION_OK )
           ap_resp_validate_result="<font color='#0F0'>Valiade OK using algorithm: "+vd_set_algo+"</font>";
        else
           ap_resp_validate_result="<font color='#F00'>Valiade failed using algorithm: "+vd_set_algo+" ! Please check the related setting.</font>";
      }
      
      attributes.put("apid", apid);   
      attributes.put("ap_url", ap_url);   
      
      attributes.put("ap_resp_content", ap_resp_content);   
      attributes.put("ap_resp_ppk_uri", ap_resp_ppk_uri);   
      attributes.put("ap_resp_url", ap_resp_url);   
      attributes.put("ap_resp_sign", ap_resp_sign);   
      attributes.put("vd_set_pubkey", vd_set_pubkey);   
      attributes.put("ap_resp_validate_result", ap_resp_validate_result);        
      
    }catch (Exception e) {
      logger.error("GuiServer.handleOdinCheckApVdRequest() : "+e.toString());
    }

    attributes.put("LANG_VALIDATE_AN_AP", Language.getLangLabel("Browse an AP of"));
    
    attributes.put("LANG_ODIN_TITLE", Language.getLangLabel("ODIN title"));
    attributes.put("LANG_ODIN_AP", Language.getLangLabel("Access Point"));
    
    attributes.put("LANG_CHECK_AP_VD", Language.getLangLabel("Validate the Aeccss Point"));     
    attributes.put("LANG_UPDATE_BASEINFO", Language.getLangLabel("Update base info")); 
    attributes.put("LANG_UPDATE_AP_SET", Language.getLangLabel("Update AP list")); 
    attributes.put("LANG_UPDATE_VD_SET", Language.getLangLabel("Validtion setting"));  
    attributes.put("LANG_TRANSFER_REGISTER", Language.getLangLabel("Transfer register"));  
    
    attributes.put("LANG_RESPONSE_URI", Language.getLangLabel("Response URI"));
    attributes.put("LANG_RESPONSE_URL", Language.getLangLabel("Response URL"));
    attributes.put("LANG_RESPONSE_CONTENT", Language.getLangLabel("Response content"));
    attributes.put("LANG_RESPONSE_SIGNATURE", Language.getLangLabel("Response signature"));
    attributes.put("LANG_VALIDATE_PUBKEY", Language.getLangLabel("Validate pubkey"));
    attributes.put("LANG_VALIDATE_RESULT", Language.getLangLabel("Validate result"));

        
    return attributes;
  }
}