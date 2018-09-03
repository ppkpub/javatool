import java.util.Locale;

public class PttpDaemon  {
  public static void main(String[] args) throws Exception {
    Config.loadUserDefined();
    
    Language.setLang(Config.defaultLang);
    System.out.println(Language.getLangLabel("PPkPub"));
    System.out.println("Loading "+Config.appName+" V"+Config.version+"  PTTP service ...");

    Locale.setDefault(new Locale("en", "US"));
    
    Database db = Database.getInstance(true);

    PttpServer PttpServer = new PttpServer();
    Thread serverThread = new Thread(PttpServer);
    serverThread.setDaemon(true);
    serverThread.start(); 
    
    while(true){    
      Thread.sleep(8000);
    }
   }

}
