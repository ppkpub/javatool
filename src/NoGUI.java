import java.util.Locale;
/*
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
*/

public class NoGUI  {
  /*private Pane splashLayout;
  private ProgressBar loadProgress;
  private Label progressText;
  private Stage mainStage;
  private static final int SPLASH_WIDTH = 200;
  private static final int SPLASH_HEIGHT = 200;
    
    private static boolean bShowGui;
    */
  public static void main(String[] args) throws Exception { 
    System.out.println("Loading "+Config.appName+" V"+Config.version+"  server without GUI ...");

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

            progressUpdateThread.start();
            blocks.init();
            blocks.versionCheck();
            blocks.follow();
            
            Thread.sleep(8000);
    }
   }

}
