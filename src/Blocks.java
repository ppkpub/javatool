import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
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
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.text.NumberFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import org.spongycastle.util.encoders.Hex;

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


public class Blocks implements Runnable {
  public static NetworkParameters params;
  public Logger logger = LoggerFactory.getLogger(Blocks.class);
  public static boolean bRemoteWalletMode = false;
  
  public Wallet wallet=null;
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
  public String  statusMessage = "";
 
  private static Blocks instance = null;
  private static HashMap<String,List<UnspentOutput>> cachedLastUnspentList=new HashMap<String,List<UnspentOutput>>();

  public static Blocks getInstanceSkipVersionCheck() {
    System.out.println("aaaaa");
    if(instance == null) {
      System.out.println("bbbbb");
      instance = new Blocks();
    } 
    System.out.println("ccccc");
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
        if(!bRemoteWalletMode){  //本地钱包模式
            File localWalletFile=new File(Config.walletFile);
            if (localWalletFile.exists()) {
              statusMessage = Language.getLangLabel("Found wallet file"); 
              logger.info(statusMessage);
              wallet = Wallet.loadFromFile(localWalletFile);
            } else {
              statusMessage = Language.getLangLabel("Creating new wallet file"); 
              logger.info(statusMessage);
              wallet = new Wallet(params);

              //ForTest,20190616
              String testBatchImportPrvFileName="resources/db/batch_prv_list.txt";
              File testBatchImportPrvFile=new File(testBatchImportPrvFileName);
              if(testBatchImportPrvFile.exists()) { //存在指定的批量私钥文件，则批量导入初始化钱包
                try {
                    InputStreamReader read = new InputStreamReader (new FileInputStream(testBatchImportPrvFileName),"ISO-8859-1");
                    BufferedReader reader=new BufferedReader(read);
                    String line;
                    int imported_counter=0;
                    while ((line = reader.readLine()) != null) {
                        if( line.startsWith("L") || line.startsWith("K") || line.startsWith("5") ){
                            try{
                                importPrivateKey(line);
                                imported_counter++;
                            }catch(Exception e){
                                logger.error( "Blocks.init() testBatchImportPrvFile meet invalid prvkey:" + line);
                            }
                        }
                    }
                    reader.close();
                    read.close();
                    logger.info("Batch imported "+ imported_counter +" addresses. ");
                }catch(Exception e){
                  logger.error( "Blocks.init() testBatchImportPrvFile failed:"+e.toString());
                }
              }else{ //创建默认的单个地址初始钱包
                ECKey newKey = new ECKey();
                importPrivateKey(newKey);
              }
              //newKey.setCreationTimeSeconds(Config.ppkToolCreationTime);
              //wallet.addKey(newKey);
              //wallet.saveToFile(localWalletFile);
            }
        }
        String fileBTCdb = Config.dbDirPrefix+Config.appName.toLowerCase()+".h2.db";
        String fileODINdb = Config.defaultSqliteFile;
        if (!new File(fileODINdb).exists()) {
          statusMessage = "Downloading ODIN database"; 
          logger.info(statusMessage);
          Util.downloadToFile(Config.downloadURL+Config.appName.toLowerCase()+"-"+Config.majorVersionDB.toString()+".db", fileODINdb);
        }
        statusMessage = Language.getLangLabel("Downloading Bitcoin blocks");
        blockStore = new H2FullPrunedBlockStore(params, Config.dbDirPrefix+Config.appName.toLowerCase(), 2000);
        
        if(wallet==null){
            blockChain = new BlockChain(params,  blockStore);
            peerGroup = new PeerGroup(params, blockChain);
            peerGroup.setFastCatchupTimeSecs(Config.ppkToolCreationTime);
        }else{
            blockChain = new BlockChain(params, wallet, blockStore);
            peerGroup = new PeerGroup(params, blockChain);
            peerGroup.setFastCatchupTimeSecs(Config.ppkToolCreationTime);
            //peerGroup.addWallet(wallet); //不需要同步保存历史交易到wallet文件中，减少损坏可能
            //wallet.autosaveToFile(new File(Config.walletFile), 1, TimeUnit.MINUTES, null);
        }
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
        ODIN.init();
      } catch (Exception e) {
        logger.error("Error during init: "+e.toString());
        //e.printStackTrace();
        System.exit(-1);
      }
      initialized = true;
      initializing = false;
    }
    
  }

  public void deleteDatabases() {
    logger.info("Deleting Bitcoin and ODIN databases");
    String fileBTCdb = Config.dbDirPrefix+Config.appName.toLowerCase()+".h2.db";
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
    logger.info("initialized: "+initialized);
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
          Integer blockTime = rs.getInt("block_time"); 
          parseBlock(blockIndex,blockTime);
          
          Util.updateLastParsedBlock(blockIndex); 
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
      if (!force) {
        working = false;
      }
      parsing = false;
    }
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
        BigInteger amount_satoshi = BigInteger.valueOf(rsTx.getInt("btc_amount"));
        byte[] odin_data = Util.hexStringToBytes(rsTx.getString("data"));
        Integer prefix_type = rsTx.getInt("prefix_type");
        
        if(1==prefix_type){ //PPk ODIN
            Byte messageType = getPPkMessageTypeFromTransaction(odin_data);
            List<Byte> message = getPPkMessageFromTransaction(odin_data);
            
            logger.info("\n--------------------\n Parsing PPk txIndex "+txIndex.toString()+"\n------------\n");
            
            if (messageType!=null && message!=null) {
                logger.info("\n--------------------\n Parsing PPk messageType "+messageType.toString()+"\n------------\n");
                if (messageType==ODIN.id) {
                    ODIN.parse(txIndex, message);
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
    wallet.saveToFile(new File(Config.walletFile));
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
    try {
      dumpedPrivateKey = new DumpedPrivateKey(params, privateKey);
      key = dumpedPrivateKey.getKey();
      return importPrivateKey(key);
    } catch (AddressFormatException e) {
      throw new Exception("Blocks.importPrivateKey() failed:"+e.getMessage());
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

  public Transaction transaction(OdinTransctionData odin_tx_data) throws Exception {
      byte[] data= Util.hexStringToBytes(odin_tx_data.data_hex);
      return transaction(
        odin_tx_data.source,
        odin_tx_data.destination,
        odin_tx_data.amount_satoshi,
        odin_tx_data.fee_satoshi,
        odin_tx_data.mark_hex,
        data
      );
  }
  public Transaction transaction(String source, String destination, BigInteger amount_satoshi, BigInteger fee, String markPubkeyHexStr,byte[] data) throws Exception {
    Transaction tx = new Transaction(params);

    if (!destination.equals("") && amount_satoshi.compareTo(BigInteger.valueOf(Config.dustSize))<0) {
      tx.verify();
      return tx;
    }

    List<Byte> dataArrayList = (data==null)?
                               new ArrayList<Byte>() : Util.toByteArrayList(data);

    int odin_data_length = dataArrayList.size();
    boolean isOdinTransaction = odin_data_length>0;

    BigInteger totalOutput = fee;
    BigInteger totalInput = BigInteger.ZERO;

    try {
      if (!destination.equals("") && amount_satoshi.compareTo(BigInteger.ZERO)>0) {
        totalOutput = totalOutput.add(amount_satoshi);
        tx.addOutput(Coin.valueOf(amount_satoshi.longValue()), new Address(params, destination));
      }
    } catch (AddressFormatException e) {
        throw new Exception("Failed to generate output for "+destination);
    }

    ECKey source_key=null; 
    if(bRemoteWalletMode){
        //source_key=new ECKey(null,Util.hexStringToBytes(source_pubkey_hex));
    }else{
        for (ECKey key : wallet.getImportedKeys()) {
            try {
              if (key.toAddress(params).equals(new Address(params, source))) {
                source_key=key;
                break;
              }
            } catch (AddressFormatException e) {

            }
        }
    }
    
    if(null==source_key)
       return null;

    //组织多重交易来嵌入所需存放的数据
    if(isOdinTransaction){
      int  max_tx_num = Config.MAX_MULTISIG_TX_NUM;
      int  max_multisig_n = Config.MAX_N;

      int from = 0;
      for (int tt=0; tt==0 || (tt<max_tx_num && from < odin_data_length - Config.MAX_OP_RETURN_LENGTH);tt++ ) {
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(source_key);
        
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
            throw new Exception("Unable to generate valid pubkey for embedding data.Please change your request contents!");
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
    
    List<UnspentOutput> unspents=Util.getUnspents(source,isOdinTransaction);   
       
    List<Script> inputScripts = new ArrayList<Script>();      

    Boolean hadOneRegularInput = false;
    Integer usedUnspents=0;
    for (UnspentOutput unspent : unspents) {
      String txHash = unspent.txid;

      //byte[] scriptBytes = Hex.decode(unspent.scriptPubKeyHex.getBytes(Charset.forName(Config.BINARY_DATA_CHARSET)));
      byte[] scriptBytes = Util.hexStringToBytes(unspent.scriptPubKeyHex);
      Script script = new Script(scriptBytes);
      //if it's sent to an address and we don't yet have enough inputs or we don't yet have at least one regular input, or if it's sent to a multisig
      //in other words, we sweep up any unused multisig inputs with every transaction

      try {
        if(
          unspent.amt_satoshi.compareTo( BigInteger.valueOf(0) ) > 0 //Avoid TX data exception,20200307
          &&(
            (script.isSentToAddress() && (totalOutput.compareTo(totalInput)>0 || !hadOneRegularInput))  
            ||(script.isSentToMultiSig() && (
                      (!isOdinTransaction && totalOutput.compareTo(totalInput)>0 )
                      ||(isOdinTransaction && usedUnspents<2 && !hadOneRegularInput)
                      ||(isOdinTransaction && usedUnspents<3 && hadOneRegularInput )  
                   )
            )
          ) 
        ){        
            if (script.isSentToAddress()) {
              hadOneRegularInput = true;
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
            
            System.out.println("Spending "+sha256Hash+" "+unspent.vout);
            totalInput = totalInput.add(unspent.amt_satoshi);
            TransactionInput input = new TransactionInput(params, tx, new byte[]{}, txOutPt);
            tx.addInput(input);
            inputScripts.add(script);
            
            usedUnspents ++ ;  //20200307
        }
        
        if( isOdinTransaction && usedUnspents>=3 && totalInput.compareTo(totalOutput)>=0 ){
            //use max 3 unspents  to lower odin transaction size if possible
            break;
        }
      } catch (Exception e) {
        logger.error("Error during transaction creation: "+e.toString());
        e.printStackTrace();
        throw new Exception("Error during transaction creation: "+e.toString());
      }
    }

    if (!hadOneRegularInput && odin_data_length>0) {
      throw new Exception(Language.getLangLabel("Not enough standard unspent outputs to cover odin transaction."));
    }

    if (totalInput.compareTo(totalOutput)<0) {
      logger.info("Not enough inputs. Output: "+totalOutput.toString()+", input: "+totalInput.toString());
      throw new Exception("Not enough BTC to cover transaction of "+String.format("%.8f",totalOutput.doubleValue()/Config.btc_unit)+" BTC. Usable amount is  "+String.format("%.8f",totalInput.doubleValue()/Config.btc_unit));
    }
    BigInteger totalChange = totalInput.subtract(totalOutput);

    try {
      if (totalChange.compareTo(BigInteger.ZERO)>0) {
        tx.addOutput(Coin.valueOf(totalChange.longValue()), new Address(params, source));
      }
    } catch (AddressFormatException e) {
        throw new Exception("Failed to generate charge output for "+source);
    }

    if(!bRemoteWalletMode){
        //sign inputs while use local wallet
        for (int i = 0; i<tx.getInputs().size(); i++) {
          Script script = inputScripts.get(i);
          TransactionInput input = tx.getInput(i);
          TransactionSignature txSig = tx.calculateSignature(i, source_key, script, SigHash.ALL, false);
          if (script.isSentToAddress()) {
            input.setScriptSig(ScriptBuilder.createInputScript(txSig, source_key));
          } else if (script.isSentToMultiSig()) {
            //input.setScriptSig(ScriptBuilder.createMultiSigInputScript(txSig));
            ScriptBuilder builder = new ScriptBuilder();
            builder.smallNum(0);
            builder.data(txSig.encodeToBitcoin());
            input.setScriptSig(builder.build());
          }
        }
        
        tx.verify();
    }

    
    //Util.exportTextToFile(tx.toString(), "resources/db/last_transaction.log");
    //System.exit(0);
    return tx;
  }

  public Boolean sendTransaction(String source, Transaction tx) throws Exception {
    try {
      System.out.println("Try to send ("+source+") transaction:");
      System.out.println(tx.toString());

      //for debug
      /*
      byte[] rawTxBytes = tx.bitcoinSerialize();
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
        
        if(source!=null)
            cacheLastUnspentTransaction(source,tx); 

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
  
  //缓存指定地址的最新未花费交易，优先作为下一次交易输入使用,20181220
  public static boolean cacheLastUnspentTransaction(String source,Transaction tx){
    List<UnspentOutput>  lastUnspents = new ArrayList<UnspentOutput> ();
    try{
        int vout=0;
        for (TransactionOutput out : tx.getOutputs()) {
            Script script = out.getScriptPubKey();
            List<ScriptChunk> asm = script.getChunks();
            int asm_num = asm.size();
            BigInteger amount_satoshi = BigInteger.valueOf(out.getValue().getValue());
            
            //System.out.println("vout:"+vout+"\nasm:"+asm.toString()+"\nbtcAmount:"+amount_satoshi);
        
            //如果金额大于0，且是多重签名输出或者是包含指定地址的普通输出，则是有效的UTXO
            boolean isValidOut=false;
            if(amount_satoshi!=BigInteger.ZERO){
                if (asm_num>=5 && asm.get(0).equalsOpCode(0x51) && asm.get(asm_num-2).isOpCode() && asm.get(asm_num-1).equalsOpCode(0xAE)) { 
                    //MULTISIG
                    isValidOut=true;
                }else{
                    Address dest_address = script.getToAddress(params);
                    String destination = dest_address.toString();
                    
                    if(source.equalsIgnoreCase(destination) )
                        isValidOut=true;
                }
                
            }
            
            if(isValidOut){
                UnspentOutput tempUnspentObj=new UnspentOutput();
                
                tempUnspentObj.amt_satoshi=amount_satoshi;
                tempUnspentObj.txid=tx.getHashAsString();
                tempUnspentObj.vout=vout;
                tempUnspentObj.scriptPubKeyHex=Util.bytesToHexString(script.getProgram());
                
                System.out.println("Cache["+source+"]'s utxo: " +tempUnspentObj.toString());
            
                if(tempUnspentObj.scriptPubKeyHex.length()>0){
                  lastUnspents.add(tempUnspentObj);
                }
            }
            
            vout++;
        }
        
        cachedLastUnspentList.put(source,lastUnspents);
        
        return true;
    }catch(Exception e){
        return false;
    }
  }
  
  //获取缓存的最近一次指定地址的未花费交易输出
  public static List<UnspentOutput> getCachedLastUnspents(String source){
    try{  
        return (List<UnspentOutput>) cachedLastUnspentList.get(source);
    }catch(Exception e){
        
    }
    return null;
  }
  
  //清空缓存的未花费交易输出
  public static boolean clearCachedLastUnspents(){
    try{  
        cachedLastUnspentList.clear();
        return true;
    }catch(Exception e){
        return false;
    }
    
  }

  public boolean importPPkTransaction(Transaction tx,Integer txSnInBlock,Block block, Integer blockHeight) {
    BigInteger fee = BigInteger.ZERO;
    String destination = "";
    BigInteger amount_satoshi = BigInteger.ZERO;
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
          //System.out.println("asm_num="+asm_num+"  "+asm.toString());
          
          for (int i=0; i<asm.get(1).data.length; i++) 
                dataArrayList.add(asm.get(1).data[i]);
        }
        
        if (destination.equals("") && amount_satoshi==BigInteger.ZERO && dataArrayList.size()==0) {
          Address address = script.getToAddress(params);
          destination = address.toString();
          amount_satoshi = BigInteger.valueOf(out.getValue().getValue());          
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
            if(address!=null && source.equals("") ){
                String tmp_str=address.toString();
                if( tmp_str.startsWith("1") ){
                    source = tmp_str;
                    break;
                }
            }
        } catch(ScriptException e) {
        }
    }
    
    logger.info("Incoming PPk transaction from "+source+" to "+destination+" ("+tx.getHashAsString()+")");

    if ( !source.equals("") && dataArrayList.size()>0 ) {
        String str_data_hex = "";

        str_data_hex = Util.bytesToHexString(data);
        //logger.info("PPk str_data_hex : ["+str_data_hex+"] length="+str_data_hex.length());

        db.executeUpdate("delete from transactions where tx_hash='"+tx.getHashAsString()+"' and block_index<0");
        ResultSet rs = db.executeQuery("select * from transactions where tx_hash='"+tx.getHashAsString()+"';");
        try {
            if (!rs.next()) {
                if (block!=null) {
                    Integer newTxIndex=Util.getLastTxIndex()+1;
                    PreparedStatement ps = db.connection.prepareStatement("INSERT INTO transactions(tx_index, tx_hash, block_index, block_time, source, destination, btc_amount, fee, data,prefix_type,sn_in_block) VALUES('"+newTxIndex+"','"+tx.getHashAsString()+"','"+blockHeight+"','"+block.getTimeSeconds()+"','"+source+"','"+destination+"','"+amount_satoshi.toString()+"','"+fee.toString()+"',?,1,'"+txSnInBlock.toString()+"')");
                    ps.setString(1, str_data_hex);
                    ps.execute();
                }else{
                    PreparedStatement ps = db.connection.prepareStatement("INSERT INTO transactions(tx_index, tx_hash, block_index, block_time, source, destination, btc_amount, fee, data,prefix_type,sn_in_block) VALUES('"+(Util.getLastTxIndex()+1)+"','"+tx.getHashAsString()+"','-1','"+Util.getNowTimestamp() +"','"+source+"','"+destination+"','"+amount_satoshi.toString()+"','"+fee.toString()+"',?,1,-1)");
                    ps.setString(1, str_data_hex);
                    ps.execute();
                }
                
            }
        } catch (SQLException e) {
            logger.error(e.toString());
        }
    }
    
    return true;
  }
    
  public static Byte getPPkMessageTypeFromTransaction(byte[] odin_data) {
    if(odin_data==null || odin_data.length==0 )
        return null;
    else
        return odin_data[0];
  }  
    
  public static List<Byte> getPPkMessageFromTransaction(byte[] odin_data) {
    List<Byte> message = null;
    try {
      List<Byte> dataArrayList = Util.toByteArrayList(odin_data);

      message = dataArrayList.subList(1, dataArrayList.size());    
      return message;
    } catch (Exception e) {
    }
    return message;
  }
  
  //打开远程钱包模式 2019-01-15
  public static void enableRemoteWalletMode() {
    bRemoteWalletMode=true;
  }
  
  public static boolean isRemoteWalletMode() {
    return bRemoteWalletMode;
  }
  
  //获取当前地址列表 2019-01-15
  public List<String> getAddresses() {
    List<String> addresses = new ArrayList<String>();
    if(!bRemoteWalletMode){//本地钱包模式
        List<ECKey> keys = wallet.getImportedKeys();
        
        for(ECKey key : keys) {
          addresses.add(key.toAddress(params).toString());
        }
    }
    return addresses;
  }
}
