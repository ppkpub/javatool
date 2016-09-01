import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;

public class JsonRpcServletEngine {
  public static final int PORT = Config.RPCPort;
  Server server;

  public void startup() throws Exception {
    server = new Server(PORT);
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
    context.setContextPath("/");
    if (!Config.RPCUsername.equals("") && !Config.RPCPassword.equals("")) {
      context.setSecurityHandler(basicAuth(Config.RPCUsername, Config.RPCPassword, Config.appName));
    }
    server.setHandler(context);
    context.addServlet(JsonRpcServlet.class, "/"+Config.appName.toLowerCase());
    server.start();
  }

  public void stop() throws Exception {
    server.stop();
  }
  
  public static void main(String args[]) {
    JsonRpcServletEngine engine = new JsonRpcServletEngine();
    try {
      engine.startup();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
    private static final SecurityHandler basicAuth(String username, String password, String realm) {
      HashLoginService l = new HashLoginService();
        l.putUser(username, Credential.getCredential(password), new String[] {"user"});
        l.setName(realm);
        
        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[]{"user"});
        constraint.setAuthenticate(true);
         
        ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(constraint);
        cm.setPathSpec("/*");
        
        ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
        csh.setAuthenticator(new BasicAuthenticator());
        csh.setRealmName("myrealm");
        csh.addConstraintMapping(cm);
        csh.setLoginService(l);
        
        return csh;      
    }  
}
