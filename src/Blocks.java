import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.sql.*;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.text.NumberFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import org.json.JSONObject;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionBroadcast;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.PeerGroup.FilterRecalculateMode;
import org.bitcoinj.core.Transaction.SigHash;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UnsafeByteArrayOutputStream;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptOpCodes;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.H2FullPrunedBlockStore;
import org.bitcoinj.wallet.WalletTransaction;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.sun.org.apache.xpath.internal.compiler.OpCodes;

public class Blocks implements Runnable {
  public NetworkParameters params;
  public Logger logger = LoggerFactory.getLogger(Blocks.class);
  private static Blocks instance = null;
  public Wallet wallet;
  public String walletFile = "resources/db/wallet";
  public PeerGroup peerGroup;
  public BlockChain blockChain;
  public BlockStore blockStore;
  public Boolean working = false;
  public Boolean parsing = false;
  public Boolean initializing = false;
  public Boolean initialized = false;
  public Integer parsingBlock = 0;
  public Integer versionCheck = 0;
  public Integer bitcoinBlock = 0;
  public Integer ppkBlock = 0;
  public String statusMessage = "";
  
  private static String lastTransctionSource=null;
  private static String lastTransctionDestination=null;
  private static BigInteger lastTransctionBtcAmount=null;
  private static BigInteger lastTransctionFee=null;
  private static String lastTransctionDataString=null;

  public static Blocks getInstanceSkipVersionCheck() {
    if(instance == null) {
      instance = new Blocks();
    } 
    return instance;
  }

  public static Blocks getInstanceFresh() {
    if(instance == null) {
      instance = new Blocks();
      instance.versionCheck();
    } 
    return instance;
  }

  public static Blocks getInstanceAndWait() {
    if(instance == null) {
      instance = new Blocks();
      instance.versionCheck();
      new Thread() { public void run() {instance.init();}}.start();
    } 
    instance.follow();
    return instance;
  }

  public static Blocks getInstance() {
    if(instance == null) {
      instance = new Blocks();
      instance.versionCheck();
      new Thread() { public void run() {instance.init();}}.start();
    } 
    if (!instance.working && instance.initialized) {
      new Thread() { public void run() {instance.follow();}}.start();
    }
    return instance;
  }

  public void versionCheck() {
    versionCheck(false);
  }
  
  public void versionCheck(Boolean autoUpdate) {
    Integer minMajorVersion = Util.getMinMajorVersion();
    Integer minMinorVersion = Util.getMinMinorVersion();
    if (Config.majorVersion<minMajorVersion || (Config.majorVersion.equals(minMajorVersion) && Config.minorVersion<minMinorVersion)) {
      if (autoUpdate) {
        statusMessage = "Version is out of date, updating now"; 
        logger.info(statusMessage);
        try {
          Runtime.getRuntime().exec("java -jar update/update.jar");
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      } else {
        logger.info("Version is out of date. Please upgrade to version "+Util.getMinVersion()+".");
      }
      System.exit(0);
    }
  }

  @Override
  public void run() {
    while (true) {
      logger.info("Looping blocks");
      Blocks.getInstance();
      try {
        Thread.sleep(1000*60); //once a minute, we run blocks.follow()
      } catch (InterruptedException e) {
        logger.error("Error during loop: "+e.toString());
      }
    }
  }

  public void init() {
    if (!initializing) {
      initializing = true;
      Locale.setDefault(new Locale("en", "US"));

      params = MainNetParams.get();
        
      try {
        //test
        //PPkURI.fetchPPkURI("ppk:426137.1411/");
        //PPkURI.fetchPPkURI("ppk:426195.373/");
        //PPkURI.fetchApByHTTP("http://ppk001.sinaapp.com/ap/?ppk-uri=ppk:426137.1411/");
        //PPkURI.fetchApByHTTP("http://ppk001.sinaapp.com/ap/ppk_img.php?ppk-uri=ppk:426195.373/");
        /*
        JSONObject key_set=Util.getRSAKeys("test",true,true);
        String tmpstr=RSACoder.sign(
                        "test".getBytes(), 
                        key_set.getString("RSAPrivateKey"),
                        "SHA256withRSA");
        System.out.println("tmpstr="+tmpstr);
        */
        /*
        Database test_db = Database.getInstance();
        int test_tx_index=7;
        ResultSet rsTx = test_db.executeQuery("select * from transactions where tx_index="+test_tx_index);
        rsTx.next();
        String dataString = rsTx.getString("data");
        System.out.println("dataString="+dataString);
        rsTx.close();
        
        OdinUpdate.parse(test_tx_index,Blocks.getPPkMessageFromTransaction(dataString));
        System.exit(0);
        */
        //test end
        
        if ((new File(walletFile)).exists()) {
          statusMessage = Language.getLangLabel("Found wallet file"); 
          logger.info(statusMessage);
          wallet = Wallet.loadFromFile(new File(walletFile));
        } else {
          statusMessage = Language.getLangLabel("Creating new wallet file"); 
          logger.info(statusMessage);
          wallet = new Wallet(params);
          ECKey newKey = new ECKey();
          newKey.setCreationTimeSeconds(Config.ppkToolCreationTime);
          wallet.addKey(newKey);
        }
        String fileBTCdb = Config.dbPath+Config.appName.toLowerCase()+".h2.db";
        String fileODINdb = Config.defaultSqliteFile;
        if (!new File(fileODINdb).exists()) {
          statusMessage = "Downloading ODIN database"; 
          logger.info(statusMessage);
          Util.downloadToFile(Config.downloadUrl+Config.appName.toLowerCase()+"-"+Config.majorVersionDB.toString()+".db", fileODINdb);
        }
        statusMessage = Language.getLangLabel("Downloading Bitcoin blocks");
        blockStore = new H2FullPrunedBlockStore(params, Config.dbPath+Config.appName.toLowerCase(), 2000);
        blockChain = new BlockChain(params, wallet, blockStore);
        peerGroup = new PeerGroup(params, blockChain);
        peerGroup.addWallet(wallet);
        peerGroup.setFastCatchupTimeSecs(Config.ppkToolCreationTime);
        wallet.autosaveToFile(new File(walletFile), 1, TimeUnit.MINUTES, null);
        peerGroup.addPeerDiscovery(new DnsDiscovery(params));
        peerGroup.start();//peerGroup.startAndWait(); //for bitcoinj0.14
        peerGroup.addEventListener(new PPkPeerEventListener());
        peerGroup.downloadBlockChain();
        while (!hasChainHead()) {
          try {
            logger.info("Blockstore doesn't yet have a chain head, so we are sleeping.");
            Thread.sleep(1000);
          } catch (InterruptedException e) {
          }
        }

        Database db = Database.getInstance();
        try {
          Integer lastParsedBlock = Util.getLastParsedBlock(); 
          if(lastParsedBlock.equals(0)){
            db.executeUpdate("CREATE TABLE IF NOT EXISTS sys_parameters (para_name VARCHAR(32) PRIMARY KEY, para_value TEXT )");
            lastParsedBlock = Util.getLastBlock(); 
            Util.updateLastParsedBlock(lastParsedBlock); 
          }
        } catch (Exception e) {
          logger.error(e.toString());
        }
        Odin.init();
      } catch (Exception e) {
        logger.error("Error during init: "+e.toString());
        //e.printStackTrace();
        System.exit(-1);
        /*
        deleteDatabases();
        initialized = false;
        initializing = false;
        init();
        */
      }
      initialized = true;
      initializing = false;
    }
    
  }

  public void deleteDatabases() {
    logger.info("Deleting Bitcoin and ODIN databases");
    String fileBTCdb = Config.dbPath+Config.appName.toLowerCase()+".h2.db";
    new File(fileBTCdb).delete();
    String fileODINdb = Config.defaultSqliteFile;
    new File(fileODINdb).delete();
  }

  public Boolean hasChainHead() {
    try {
      Integer blockHeight = blockStore.getChainHead().getHeight();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public void follow() {
    follow(false);
  }
  public void follow(Boolean force) {
    logger.info("Working status: "+working);
    if ((!working && initialized) || force) {
      statusMessage = "Checking block height";
      logger.info(statusMessage);
      if (!force) {
        working = true;
      }
      try {
        //catch ODIN up to Bitcoin
        Integer blockHeight = blockStore.getChainHead().getHeight();
        Integer lastBlock = Util.getLastBlock();
        Integer lastBlockTime = Util.getLastBlockTimestamp();
        
        bitcoinBlock = blockHeight;
        ppkBlock = lastBlock;

        if (lastBlock == 0) {
          lastBlock = Config.firstBlock - 1;
        }
        Integer nextBlock = lastBlock + 1;

        logger.info("Bitcoin block height: "+blockHeight);  
        logger.info("PPk block height: "+lastBlock);
        if (lastBlock < blockHeight) {
          //traverse new blocks
          parsing = true;
          Integer blocksToScan = blockHeight - lastBlock;
          List<Sha256Hash> blockHashes = new ArrayList<Sha256Hash>();

          Block block = peerGroup.getDownloadPeer().getBlock(blockStore.getChainHead().getHeader().getHash()).get(59, TimeUnit.SECONDS);
          while (blockStore.get(block.getHash()).getHeight()>lastBlock) {
            blockHashes.add(block.getHash());
            block = blockStore.get(block.getPrevBlockHash()).getHeader();
          }

          for (int i = blockHashes.size()-1; i>=0; i--) { //traverse blocks in reverse order
            block = peerGroup.getDownloadPeer().getBlock(blockHashes.get(i)).get(59, TimeUnit.SECONDS);
            blockHeight = blockStore.get(block.getHash()).getHeight();
            ppkBlock = blockHeight;
            statusMessage = "Catching ODIN up to Bitcoin "+Util.format((blockHashes.size() - i)/((double) blockHashes.size())*100.0)+"%";  
            logger.info("Catching ODIN up to Bitcoin (block "+blockHeight.toString()+"): "+Util.format((blockHashes.size() - i)/((double) blockHashes.size())*100.0)+"%");  
            importBlock(block, blockHeight);
          }

          parsing = false;
        }
      } catch (Exception e) {
        logger.error("Error during follow: "+e.toString());
        e.printStackTrace();
      }  
      
      //Ensure to parse new imported blocks while follow finished or failed
      try{ 
        Integer lastImportedBlock = Util.getLastBlock();
        Integer lastImportedBlockTime = Util.getLastBlockTimestamp();
        Integer lastParsedBlock = Util.getLastParsedBlock(); 
        if (lastParsedBlock < lastImportedBlock) {
          parsing = true;
          parseFrom(lastParsedBlock+1, true);

          parsing = false;
        }
      } catch (Exception e) {
        logger.error("Error during parse: "+e.toString());
        e.printStackTrace();
      }  
      
      if (!force) {
        working = false;
      }
    }
  }

  public void reDownloadBlockTransactions(Integer blockHeight) {
    Database db = Database.getInstance();
    ResultSet rs = db.executeQuery("select * from blocks where block_index='"+blockHeight.toString()+"';");
    try {
      if (rs.next()) {
        Block block = peerGroup.getDownloadPeer().getBlock(new Sha256Hash(rs.getString("block_hash"))).get();
        db.executeUpdate("delete from transactions where block_index='"+blockHeight.toString()+"';");
        Integer txSnInBlock=0;
        for (Transaction tx : block.getTransactions()) {
          importPPkTransaction(tx,txSnInBlock, block, blockHeight);
          txSnInBlock++;
        }
      }
    } catch (Exception e) {

    }
  }

  public void importBlock(Block block, Integer blockHeight) {
    statusMessage = "Importing block "+blockHeight;
    logger.info(statusMessage);
    Database db = Database.getInstance();
    ResultSet rs = db.executeQuery("select * from blocks where block_hash='"+block.getHashAsString()+"';");
    try {
      if (!rs.next()) {
        db.executeUpdate("INSERT INTO blocks(block_index,block_hash,block_time,block_nonce) VALUES('"+blockHeight.toString()+"','"+block.getHashAsString()+"','"+block.getTimeSeconds()+"','"+block.getNonce()+"')");
      }
      Integer txSnInBlock=0;
      for (Transaction tx : block.getTransactions()) {
        importPPkTransaction(tx,txSnInBlock, block, blockHeight);
        txSnInBlock++;
      }
    } catch (SQLException e) {
    }
  }

  public void reparse() {
    reparse(false);
  }
  public void reparse(final Boolean force) {
    Database db = Database.getInstance();
    db.executeUpdate("delete from odins;");
    db.executeUpdate("delete from odin_update_logs;");
    db.executeUpdate("delete from balances;");
    db.executeUpdate("delete from sends;");
    db.executeUpdate("delete from messages;");
    db.executeUpdate("delete from sys_parameters;");
    new Thread() { public void run() {parseFrom(Config.firstBlock, force);}}.start();
  }

  public void parseFrom(Integer blockNumber) {
    parseFrom(blockNumber, false);
  }
  public void parseFrom(Integer blockNumber, Boolean force) {
    if (!working || force) {
      parsing = true;
      if (!force) {
        working = true;
      }
      Database db = Database.getInstance();
      ResultSet rs = db.executeQuery("select * from blocks where block_index>="+blockNumber.toString()+" order by block_index asc;");
      try {
        while (rs.next()) {
          Integer blockIndex = rs.getInt("block_index");
          Integer blockTime = rs.getInt("block_time");  //Added for POS
          parseBlock(blockIndex,blockTime);
          
          Util.updateLastParsedBlock(blockIndex); 
        }
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      if (!force) {
        working = false;
      }
      parsing = false;
    }
  }

  public List<Byte> getMessageFromTransaction(String txDataString) {
    byte[] data;
    List<Byte> message = null;
    try {
      data = txDataString.getBytes(Config.BINARY_DATA_CHARSET);
      List<Byte> dataArrayList = Util.toByteArrayList(data);

      message = dataArrayList.subList(4, dataArrayList.size());    
      return message;
    } catch (UnsupportedEncodingException e) {
    }
    return message;
  }

  public List<Byte> getMessageTypeFromTransaction(String txDataString) {
    byte[] data;
    List<Byte> messageType = null;
    try {
      data = txDataString.getBytes(Config.BINARY_DATA_CHARSET);
      List<Byte> dataArrayList = Util.toByteArrayList(data);

      messageType = dataArrayList.subList(0, 4);
      return messageType;
    } catch (UnsupportedEncodingException e) {
    }
    return messageType;
  }  
    
    

  public void parseBlock(Integer blockIndex,Integer blockTime) { 
    Database db = Database.getInstance();
    ResultSet rsTx = db.executeQuery("select * from transactions where block_index="+blockIndex.toString()+" order by tx_index asc;");
    parsingBlock = blockIndex;
    statusMessage = "\n++++++++++++++++++++++++++++++++++\n Parsing block "+blockIndex.toString()+"\n++++++++++++++++++++++++++++++++++\n";
    logger.info(statusMessage);
    try {
      while (rsTx.next()) {
        Integer txIndex = rsTx.getInt("tx_index");
        String source = rsTx.getString("source");
        String destination = rsTx.getString("destination");
        BigInteger btcAmount = BigInteger.valueOf(rsTx.getInt("btc_amount"));
        String dataString = rsTx.getString("data");
        Integer prefix_type = rsTx.getInt("prefix_type");
        
        if(1==prefix_type){ //PPk ODIN
            Byte messageType = getPPkMessageTypeFromTransaction(dataString);
            List<Byte> message = getPPkMessageFromTransaction(dataString);
            
            logger.info("\n--------------------\n Parsing PPk txIndex "+txIndex.toString()+"\n------------\n");
            
            if (messageType!=null && message!=null) {
                logger.info("\n--------------------\n Parsing PPk messageType "+messageType.toString()+"\n------------\n");
                if (messageType==Odin.id) {
                    Odin.parse(txIndex, message);
                }else if (messageType==OdinUpdate.id) {
                    OdinUpdate.parse(txIndex, message);
                }         
            }
        } else { //normal bitcoin operation
            
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }    
  }

  public void deletePending() {
    Database db = Database.getInstance();
    db.executeUpdate("delete from transactions where block_index<0 and tx_index<(select max(tx_index) from transactions)-10;");
  }

/*
  public Integer getDBMinorVersion() {
    Database db = Database.getInstance();
    ResultSet rs = db.executeQuery("PRAGMA user_version;");
    try {
      while(rs.next()) {
        return rs.getInt("user_version");
      }
    } catch (SQLException e) {
    }  
    return 0;
  }

  public void updateMinorVersion() {
    // Update minor version
    Database db = Database.getInstance();
    db.executeUpdate("PRAGMA user_version = "+Config.minorVersionDB.toString());
  }
*/
  public Integer getHeight() {
    try {
      Integer height = blockStore.getChainHead().getHeight();
      return height;
    } catch (BlockStoreException e) {
    }
    return 0;
  }

  public String importPrivateKey(ECKey key) throws Exception {
    String address = "";
    logger.info("Importing private key");
    address = key.toAddress(params).toString();
    logger.info("Importing address "+address);
    if (wallet.getImportedKeys().contains(key)) {
      wallet.removeKey(key);
    }
    wallet.addKey(key);
    /*
    try {
      importTransactionsFromAddress(address);
    } catch (Exception e) {
      throw new Exception(e.getMessage());
    }
     */
    return address;    
  }
  public String importPrivateKey(String privateKey) throws Exception {
    DumpedPrivateKey dumpedPrivateKey;
    String address = "";
    ECKey key = null;
    logger.info("Importing private key");
    try {
      dumpedPrivateKey = new DumpedPrivateKey(params, privateKey);
      key = dumpedPrivateKey.getKey();
      return importPrivateKey(key);
    } catch (AddressFormatException e) {
      throw new Exception(e.getMessage());
    }
  }

  /*
  public void importTransactionsFromAddress(String address) throws Exception {
    logger.info("Importing transactions");
    try {
      wallet.addWatchedAddress(new Address(params, address));
    } catch (AddressFormatException e) {
    }
    List<Map.Entry<String,String>> txsInfo = Util.getTransactions(address);
    BigInteger balance = BigInteger.ZERO;
    BigInteger balanceSent = BigInteger.ZERO;
    BigInteger balanceReceived = BigInteger.ZERO;
    Integer transactionCount = 0;
    for (Map.Entry<String,String> txHashBlockHash : txsInfo) {
      String txHash = txHashBlockHash.getKey();
      String blockHash = txHashBlockHash.getValue();
      try {
        Block block = peerGroup.getDownloadPeer().getBlock(new Sha256Hash(blockHash)).get();
        List<Transaction> txs = block.getTransactions();
        for (Transaction tx : txs) {
          if (tx.getHashAsString().equals(txHash)){// && wallet.isPendingTransactionRelevant(tx)) {
            transactionCount ++;
            wallet.receivePending(tx, peerGroup.getDownloadPeer().downloadDependencies(tx).get());
            balanceReceived = balanceReceived.add(tx.getValueSentToMe(wallet));
            balanceSent = balanceSent.add(tx.getValueSentFromMe(wallet));
            balance = balance.add(tx.getValueSentToMe(wallet));
            balance = balance.subtract(tx.getValueSentFromMe(wallet));
          }
        }
      } catch (InterruptedException e) {
        throw new Exception(e.getMessage());
      } catch (ExecutionException e) {        
        throw new Exception(e.getMessage());
      }
    }
    logger.info("Address balance: "+balance);    
  }
   */

  public Transaction transaction(String source, String destination, BigInteger btcAmount, BigInteger fee, String markPubkeyHexStr,String dataString) throws Exception {
    /*
    //Anti duplicate same reuqest
    if( source.equals(lastTransctionSource) 
       && (lastTransctionDestination!=null && lastTransctionDestination.equals(destination) )
       && btcAmount.compareTo(lastTransctionBtcAmount)==0
       && fee.compareTo(lastTransctionFee)==0
       && (lastTransctionDataString!=null && lastTransctionDataString.equals(dataString))
       ){
      logger.error("Error for duplicate transaction request");
      return null;    
    }
    */
    
    lastTransctionSource=source;
    lastTransctionDestination=destination;
    lastTransctionBtcAmount=btcAmount;
    lastTransctionFee=fee;
    lastTransctionDataString=dataString;
    
    Transaction tx = new Transaction(params);

    if (!destination.equals("") && btcAmount.compareTo(BigInteger.valueOf(Config.dustSize))<0) {
      tx.verify();
      return tx;
    }

    byte[] data = null;
    List<Byte> dataArrayList = new ArrayList<Byte>();
    try {
      data = dataString.getBytes(Config.BINARY_DATA_CHARSET);
      dataArrayList = Util.toByteArrayList(data);
    } catch (UnsupportedEncodingException e) {
    }

    int odin_data_length = dataArrayList.size();

    BigInteger totalOutput = fee;
    BigInteger totalInput = BigInteger.ZERO;

    try {
      if (!destination.equals("") && btcAmount.compareTo(BigInteger.ZERO)>0) {
        totalOutput = totalOutput.add(btcAmount);
        tx.addOutput(Coin.valueOf(btcAmount.longValue()), new Address(params, destination));
      }
    } catch (AddressFormatException e) {
    }

    ECKey register_key=null; 
    for (ECKey key : wallet.getImportedKeys()) {
        try {
          if (key.toAddress(params).equals(new Address(params, source))) {
            register_key=key;
            break;
          }
        } catch (AddressFormatException e) {

        }
    }
    
    if(null==register_key)
       return null;

    //组织多重交易来嵌入所需存放的数据
    if(odin_data_length>0){
      int  max_tx_num = Config.MAX_MULTISIG_TX_NUM;
      int  max_multisig_n = Config.MAX_N;

      int from = 0;
      for (int tt=0; tt==0 || (tt<max_tx_num && from < odin_data_length - Config.MAX_OP_RETURN_LENGTH);tt++ ) {
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(register_key);
        
        if(tt==0){ //第一条多重交易的第二个公钥固定为指定特征公钥
          keys.add(new ECKey(null, Util.hexStringToBytes(markPubkeyHexStr)));
        }
        
        for(int mm=keys.size(); 
            mm<max_multisig_n && ( ( tt==0 && from < odin_data_length ) || ( tt>0 && from < odin_data_length - Config.MAX_OP_RETURN_LENGTH) );
            mm++,from += Config.PPK_PUBKEY_EMBED_DATA_MAX_LENGTH){
          int embed_data_length=Math.min(Config.PPK_PUBKEY_EMBED_DATA_MAX_LENGTH, odin_data_length-from); 
          
          List<Byte> chunk = new ArrayList<Byte>(dataArrayList.subList(from, from+embed_data_length ));
          
          byte[] tmp_pub_key=Util.generateValidPubkey(Util.toByteArray(chunk));
          
          if(tmp_pub_key==null){
            throw new Exception("Unable to generate valid pubkey for embedding data["+dataString+"].Please change your request contents!");
          }
          
          keys.add(new ECKey(null,tmp_pub_key));
        }

        Script script = ScriptBuilder.createMultiSigOutputScript(1, keys);
        tx.addOutput(Coin.valueOf(BigInteger.valueOf(Config.dustSize).longValue()), script);
        totalOutput = totalOutput.add(BigInteger.valueOf(Config.dustSize));
      }
    
      //使用op_return对应的备注脚本空间来嵌入剩余ODIN数据
      int last_data_length= odin_data_length-from;
      
      if(last_data_length>Config.MAX_OP_RETURN_LENGTH){
        throw new Exception("Too big embed data.(Should be less than "+Config.MAX_ODIN_DATA_LENGTH+" bytes)");
      }else if( last_data_length>0 ){
        List<Byte> chunk = new ArrayList<Byte>(dataArrayList.subList(from, odin_data_length));
        chunk.add(0,(byte) last_data_length);
        chunk.add(0,(byte) 0x6a);
        Script script = new Script(Util.toByteArray(chunk));
        tx.addOutput(Coin.valueOf(BigInteger.valueOf(0).longValue()), script);
      }
    }
    List<UnspentOutput> unspents = Util.getUnspents(source);
    List<Script> inputScripts = new ArrayList<Script>();      
    List<ECKey> inputKeys = new ArrayList<ECKey>();      

    Boolean atLeastOneRegularInput = false;
    Integer usedUnspents=0;
    for (UnspentOutput unspent : unspents) {
      String txHash = unspent.txid;

      byte[] scriptBytes = Hex.decode(unspent.scriptPubKeyHex.getBytes(Charset.forName(Config.BINARY_DATA_CHARSET)));
      Script script = new Script(scriptBytes);
      //if it's sent to an address and we don't yet have enough inputs or we don't yet have at least one regular input, or if it's sent to a multisig
      //in other words, we sweep up any unused multisig inputs with every transaction

      try {
        if ((script.isSentToAddress() && (totalOutput.compareTo(totalInput)>0 || !atLeastOneRegularInput)) 
          || (script.isSentToMultiSig() && ((usedUnspents<2 && !atLeastOneRegularInput)||(usedUnspents<3 && atLeastOneRegularInput ) || fee.compareTo(BigInteger.valueOf(Config.maxFee))==0 ) )) {
          //if we have this transaction in our wallet already, we need confirm that it is not already spent
          if (wallet.getTransaction(new Sha256Hash(txHash))==null || wallet.getTransaction(new Sha256Hash(txHash)).getOutput(unspent.vout).isAvailableForSpending()) {
            if (script.isSentToAddress()) {
              atLeastOneRegularInput = true;
            }
            /*
            //严格检查输入是否符合比特币协议标准要求 ,待生效
            boolean scriptIsStandard=true;
            if (script.isSentToMultiSig()) {
               System.out.println("Check UTXO script:"+script.toString());
               try{
                 List<ECKey> tmp_pubkeys=script.getPubKeys();
                 for (ECKey tmp_key : tmp_pubkeys){
                   byte[] tmp_byte_array= tmp_key.getPubKey();
                   if( tmp_byte_array[0]!=2 && tmp_byte_array[0]!=3 && tmp_byte_array[0]!=4  ){ 
                     //not standard pubkey
                     scriptIsStandard=false;
                     System.out.println("Ignore an UTXO for invalid script:"+script.toString());
                     break;
                   }
                 }
               }catch(Exception e){
                 scriptIsStandard=false;
                 System.out.println("Found invalid script :"+script.toString());
               }
            }
            
            if(!scriptIsStandard)
              continue;
            */
            Sha256Hash sha256Hash = new Sha256Hash(txHash);  
            TransactionOutPoint txOutPt = new TransactionOutPoint(params, unspent.vout, sha256Hash);
            for (ECKey key : wallet.getImportedKeys()) {
              try {
                if (key.toAddress(params).equals(new Address(params, source))) {
                  System.out.println("Spending "+sha256Hash+" "+unspent.vout);
                  totalInput = totalInput.add(BigDecimal.valueOf(unspent.amount*Config.btc_unit).toBigInteger());
                  TransactionInput input = new TransactionInput(params, tx, new byte[]{}, txOutPt);
                  tx.addInput(input);
                  inputScripts.add(script);
                  inputKeys.add(key);
                                      
                  usedUnspents++;
                  break;
                }
              } catch (AddressFormatException e) {
              }
            }
          }
        }
                  
        if( usedUnspents>=3 && totalInput.compareTo(totalOutput)>=0 )
          //use max 3 unspents  to lower transaction size if possible
          break;
      } catch (Exception e) {
        logger.error("Error during transaction creation: "+e.toString());
        e.printStackTrace();
      }
    }

    if (!atLeastOneRegularInput) {
      throw new Exception("Not enough standard unspent outputs to cover transaction.");
    }

    if (totalInput.compareTo(totalOutput)<0) {
      logger.info("Not enough inputs. Output: "+totalOutput.toString()+", input: "+totalInput.toString());
      throw new Exception("Not enough BTC to cover transaction of "+String.format("%.8f",totalOutput.doubleValue()/Config.btc_unit)+" BTC.");
    }
    BigInteger totalChange = totalInput.subtract(totalOutput);

    try {
      if (totalChange.compareTo(BigInteger.ZERO)>0) {
        tx.addOutput(Coin.valueOf(totalChange.longValue()), new Address(params, source));
      }
    } catch (AddressFormatException e) {
    }

    //sign inputs
    for (int i = 0; i<tx.getInputs().size(); i++) {
      Script script = inputScripts.get(i);
      ECKey key = inputKeys.get(i);
      TransactionInput input = tx.getInput(i);
      TransactionSignature txSig = tx.calculateSignature(i, key, script, SigHash.ALL, false);
      if (script.isSentToAddress()) {
        input.setScriptSig(ScriptBuilder.createInputScript(txSig, key));
      } else if (script.isSentToMultiSig()) {
        //input.setScriptSig(ScriptBuilder.createMultiSigInputScript(txSig));
        ScriptBuilder builder = new ScriptBuilder();
        builder.smallNum(0);
        builder.data(txSig.encodeToBitcoin());
        input.setScriptSig(builder.build());
      }
    }

    tx.verify();
    //Util.exportTextToFile(tx.toString(), "resources/db/last_transaction.log");
    //System.exit(0);
    return tx;
  }

  public Boolean sendTransaction(String source, Transaction tx) throws Exception {
    try {
      System.out.println("Try to send transaction:");
      System.out.println(tx.toString());

      byte[] rawTxBytes = tx.bitcoinSerialize();
      
      //for debug
      /*
      System.out.println("The Raw TX:");
      for(int kk=0;kk<rawTxBytes.length;kk++){
          System.out.printf("%02x",rawTxBytes[kk]);
      }
      */
      
      Blocks blocks = Blocks.getInstance();
      TransactionBroadcast future = null;
      try {
        logger.info("Broadcasting transaction: "+tx.getHashAsString());
        future = peerGroup.broadcastTransaction(tx);
        /*Not need waiting for the response,Deled by flyingsee,20160815
        int tries = 2; 
        Boolean success = false;
        while (tries>0 && !success) {
          tries--;
          List<UnspentOutput> unspents = Util.getUnspents(source);
          logger.info("unspents count: " + unspents.size() ); 
          for (UnspentOutput unspent : unspents) {
            if (unspent.txid.equals(tx.getHashAsString())) {
              success = true;
              break;
            }
          }
          //if (Util.getTransaction(tx.getHashAsString())!=null) {
          //  success = true;
          //}
          Thread.sleep(5000); 
        }

        if (!success) {
          throw new Exception(Language.getLangLabel("Transaction timed out. Please try again.")+"[1]");
        }
        */
        //future.get(60, TimeUnit.SECONDS);
        //} catch (TimeoutException e) {
        //  logger.error(e.toString());
        //  future.cancel(true);
      } catch (Exception e) {
        throw new Exception(Language.getLangLabel("Transaction timed out. Please try again.")+"[2]");
      }
      logger.info("Importing transaction (assigning block number -1)");
      blocks.importPPkTransaction(tx,null, null, null);
      return true;
    } catch (Exception e) {
      throw new Exception(e.getMessage());
    }    
  }

  public boolean importPPkTransaction(Transaction tx,Integer txSnInBlock,Block block, Integer blockHeight) {
    BigInteger fee = BigInteger.ZERO;
    String destination = "";
    BigInteger btcAmount = BigInteger.ZERO;
    List<Byte> dataArrayList = new ArrayList<Byte>();
    byte[] data = null;
    String source = "";

    Database db = Database.getInstance();

    boolean matched_ppk_odin_prefix=false;

    for (TransactionOutput out : tx.getOutputs()) {
      try {
        Script script = out.getScriptPubKey();
        List<ScriptChunk> asm = script.getChunks();
        int asm_num = asm.size();
        
        boolean isFirstMultiSigTx=false;
        if (asm_num>=5 && asm.get(0).equalsOpCode(0x51) && asm.get(asm_num-2).isOpCode() && asm.get(asm_num-1).equalsOpCode(0xAE)) { //MULTISIG
          int multisig_n=asm.get(asm_num-2).decodeOpN();
          
          if( !matched_ppk_odin_prefix){
             if(asm.get(2).data.length==Config.PPK_ODIN_MARK_PUBKEY_HEX.length()/2 ){
               String tmp_pubkey_hex=Util.bytesToHexString(asm.get(2).data);

               if(Config.PPK_ODIN_MARK_PUBKEY_HEX.equals(tmp_pubkey_hex)){
                 matched_ppk_odin_prefix=true;
                 isFirstMultiSigTx=true;
               }
             }
          }
          
          if(matched_ppk_odin_prefix){
            int from =  isFirstMultiSigTx ? 3 : 2;
            for(;from<multisig_n+1;from++){
              byte[] tmp_data=asm.get(from).data;
              byte embed_data_len=tmp_data[1];
              if(embed_data_len>0 && embed_data_len<=tmp_data.length-2)
                for (byte i=0; i<embed_data_len; i++) 
                    dataArrayList.add(tmp_data[2+i]);
            }
          }
        }else if( matched_ppk_odin_prefix && asm.get(0).equalsOpCode(0x6A) ){  //OP_RETURN
          System.out.println("asm_num="+asm_num+"  "+asm.toString());
          
          for (int i=0; i<asm.get(1).data.length; i++) 
                dataArrayList.add(asm.get(1).data[i]);
        }
        
        if (destination.equals("") && btcAmount==BigInteger.ZERO && dataArrayList.size()==0) {
          Address address = script.getToAddress(params);
          destination = address.toString();
          btcAmount = BigInteger.valueOf(out.getValue().getValue());          
        }
      } catch(ScriptException e) {        
      }
    }
     
    if (dataArrayList.size()>0) {
      data = Util.toByteArray(dataArrayList);  //截取特征前缀后的有效字节数据
    } else {
      return false;
    }
        
    for (TransactionInput in : tx.getInputs()) {
        if (in.isCoinBase()) return false;
        try {
            Script script = in.getScriptSig();
            Address address = script.getFromAddress(params);
            if (source.equals("")) {
                source = address.toString();
            }else if (!source.equals(address.toString()) ){ //require all sources to be the same
                return false;
            }
        } catch(ScriptException e) {
        }
    }
    
    logger.info("Incoming PPk transaction from "+source+" to "+destination+" ("+tx.getHashAsString()+")");

    if ( !source.equals("") && dataArrayList.size()>0 ) {
        String dataString = "";
        try {
            dataString = new String(data,Config.BINARY_DATA_CHARSET);
            logger.info("PPk dataString : ["+dataString+"] length="+dataString.length());
        } catch (UnsupportedEncodingException e) {
        }
        db.executeUpdate("delete from transactions where tx_hash='"+tx.getHashAsString()+"' and block_index<0");
        ResultSet rs = db.executeQuery("select * from transactions where tx_hash='"+tx.getHashAsString()+"';");
        try {
            if (!rs.next()) {
                if (block!=null) {
                    Integer newTxIndex=Util.getLastTxIndex()+1;
                    PreparedStatement ps = db.connection.prepareStatement("INSERT INTO transactions(tx_index, tx_hash, block_index, block_time, source, destination, btc_amount, fee, data,prefix_type,sn_in_block) VALUES('"+newTxIndex+"','"+tx.getHashAsString()+"','"+blockHeight+"','"+block.getTimeSeconds()+"','"+source+"','"+destination+"','"+btcAmount.toString()+"','"+fee.toString()+"',?,1,'"+txSnInBlock.toString()+"')");
                    ps.setString(1, dataString);
                    ps.execute();
                }else{
                    PreparedStatement ps = db.connection.prepareStatement("INSERT INTO transactions(tx_index, tx_hash, block_index, block_time, source, destination, btc_amount, fee, data,prefix_type,sn_in_block) VALUES('"+(Util.getLastTxIndex()+1)+"','"+tx.getHashAsString()+"','-1','"+Util.getNowTimestamp() +"','"+source+"','"+destination+"','"+btcAmount.toString()+"','"+fee.toString()+"',?,1,-1)");
                    ps.setString(1, dataString);
                    ps.execute();
                }
                
            }
        } catch (SQLException e) {
            logger.error(e.toString());
        }
    }
    
    return true;
  }
    
  public static Byte getPPkMessageTypeFromTransaction(String txDataString) {
    byte[] data;
    Byte messageType = null;
    try {
      data = txDataString.getBytes(Config.BINARY_DATA_CHARSET);
      messageType=data[0];
      return messageType;
    } catch (UnsupportedEncodingException e) {
    }
    return messageType;
  }  
    
  public static List<Byte> getPPkMessageFromTransaction(String txDataString) {
    byte[] data;
    List<Byte> message = null;
    try {
      data = txDataString.getBytes(Config.BINARY_DATA_CHARSET);
      List<Byte> dataArrayList = Util.toByteArrayList(data);

      message = dataArrayList.subList(1, dataArrayList.size());    
      return message;
    } catch (UnsupportedEncodingException e) {
    }
    return message;
  }
}
