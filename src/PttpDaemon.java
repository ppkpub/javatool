import java.util.Locale;

public class PttpDaemon  {
  public static void main(String[] args) throws Exception {
    boolean bNoSyncBlocks=false;
    if(args!=null){
      System.out.println("args.length:"+args.length);
      
      for(int aa=0;aa<args.length;aa++){
        if(args[aa].equalsIgnoreCase("nosync"))
          bNoSyncBlocks=true;
      }
    }
    
    Config.loadUserDefined();
    
    Language.setLang(Config.defaultLang);
    System.out.println(Language.getLangLabel("PPkPub"));
    System.out.println("Loading "+Config.appName+" V"+Config.version+"  PTTP service ...");

    Locale.setDefault(new Locale("en", "US"));
    
    Database db = Database.getInstance(true);
    
    //Start PTTP service
    PttpServer PttpServer = new PttpServer();
    Thread serverThread = new Thread(PttpServer);
    serverThread.setDaemon(true);
    serverThread.start(); 

    while(true){   
      if(!bNoSyncBlocks){
        //Start blocks thread
        final Blocks blocks = Blocks.getInstanceFresh();
        
        Thread progressUpdateThread = new Thread(blocks) { 
          public void run() {
              Integer lastParsedBlock=Util.getLastParsedBlock();
              while(blocks.ppkBlock == 0  || blocks.working || blocks.parsing 
                      || lastParsedBlock<blocks.bitcoinBlock ) {
                  if (blocks.ppkBlock > 0) {
                      if( blocks.ppkBlock < blocks.bitcoinBlock )
                          System.out.println(Language.getLangLabel("Getting block")+" " + blocks.ppkBlock + "/" + blocks.bitcoinBlock);
                      else
                          System.out.println(Language.getLangLabel("Parsing")+" " + blocks.ppkBlock + "/" + blocks.bitcoinBlock);
                  } else {
                      System.out.println(blocks.statusMessage);    
                  }
                  try {
                      Thread.sleep(2000);
                  } catch (InterruptedException e) {
                      // TODO Auto-generated catch block
                      e.printStackTrace();
                  }
                  lastParsedBlock=Util.getLastParsedBlock();
              }
              
          }
        };
      
        progressUpdateThread.start();
        blocks.init();
        blocks.versionCheck();
        blocks.follow();
        
        System.out.println("Waiting a while for new block ......");
      }
      
      Thread.sleep(8000);
    }
  }

}
