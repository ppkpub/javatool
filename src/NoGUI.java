import java.util.Locale;

public class NoGUI  {
  public static void main(String[] args) throws Exception {
    Config.loadUserDefined();
    
    Language.setLang(Config.defaultLang);
    System.out.println(Language.getLangLabel("PPkPub"));
    System.out.println("Loading "+Config.appName+" V"+Config.version+"  PTTP service ...");

    Locale.setDefault(new Locale("en", "US"));

    while(true){    
      // Start Blocks
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

      GuiServer gs = new GuiServer();
      Thread serverThread = new Thread(gs);
      serverThread.setDaemon(true);
      serverThread.start(); 
      
      progressUpdateThread.start();
      blocks.init();
      blocks.versionCheck();
      blocks.follow();

      Thread.sleep(8000);
    }
   }

}
