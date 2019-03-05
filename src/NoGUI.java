import java.util.Locale;

public class NoGUI  {
  public static void main(String[] args) throws Exception {
    if(args!=null){
      System.out.println("args.length:"+args.length);
      
      for(int aa=0;aa<args.length;aa++){
        if(args[aa].equalsIgnoreCase("nowallet"))
          Blocks.enableRemoteWalletMode();
      }
    }
    
    Config.loadUserDefined();
    
    Language.setLang(Config.defaultLang);
    System.out.println(Language.getLangLabel("PPkPub"));
    System.out.println("Loading "+Config.appName+" V"+Config.version+"  GuiServer service without wallet  ...");

    Locale.setDefault(new Locale("en", "US"));
        
    //Start GuiServer thread
    GuiServer server = new GuiServer();
    Thread serverThread = new Thread(server);
    serverThread.setDaemon(true);
    serverThread.start(); 
    
    //Start Blocks sync thread
    final Blocks blocks = Blocks.getInstanceFresh();
    Thread blocksThread = new Thread(blocks);
    blocksThread.setDaemon(true);
    blocksThread.start(); 

    while(true){    
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

      Thread.sleep(60000);
    }
   }

}
