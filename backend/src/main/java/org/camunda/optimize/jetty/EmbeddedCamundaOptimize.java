package org.camunda.optimize.jetty;

import org.camunda.optimize.CamundaOptimize;
import org.camunda.optimize.service.es.ElasticSearchSchemaInitializer;
import org.camunda.optimize.service.importing.ImportJobExecutor;
import org.camunda.optimize.service.importing.ImportScheduler;
import org.camunda.optimize.service.importing.ImportServiceProvider;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Jetty embedded server wrapping jersey servlet handler and loading properties from
 * service and environment property files.
 *
 * @author Askar Akhmerov
 */
public class EmbeddedCamundaOptimize implements CamundaOptimize {

  private static final String PROTOCOL = "http/1.1";

  static {
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();
  }

  private static final Logger logger = LoggerFactory.getLogger(EmbeddedCamundaOptimize.class);

  private SpringAwareServletConfiguration jerseyCamundaOptimize;
  private Server jettyServer;

  public EmbeddedCamundaOptimize() {
    jerseyCamundaOptimize = new SpringAwareServletConfiguration();
    jettyServer = setUpEmbeddedJetty(jerseyCamundaOptimize);
  }

  public EmbeddedCamundaOptimize(String contextLocation) {
    jerseyCamundaOptimize = new SpringAwareServletConfiguration(contextLocation);
    jettyServer = setUpEmbeddedJetty(jerseyCamundaOptimize);
  }

  private Server setUpEmbeddedJetty(SpringAwareServletConfiguration jerseyCamundaOptimize) {
    Properties properties = getProperties();

    Server jettyServer = initServer(properties);

    ServletContextHandler context = jerseyCamundaOptimize.getServletContextHandler();

    jettyServer.setHandler(context);
    return jettyServer;
  }



  private Server initServer(Properties properties) {
    String host = properties.getProperty("camunda.optimize.container.host");
    String keystorePass = properties.getProperty("camunda.optimize.container.keystore.password");
    String keystoreLocation = properties.getProperty("camunda.optimize.container.keystore.location");
    Server server = new Server();

    ServerConnector connector = initHttpConnector(properties, host, server);

    ServerConnector sslConnector = initHttpsConnector(properties, host, keystorePass, keystoreLocation, server);

    server.setConnectors(new Connector[] { connector, sslConnector });

    return server;
  }

  private ServerConnector initHttpsConnector(Properties properties, String host, String keystorePass, String keystoreLocation, Server server) {
    HttpConfiguration https = new HttpConfiguration();
    https.addCustomizer(new SecureRequestCustomizer());
    SslContextFactory sslContextFactory = new SslContextFactory();
    sslContextFactory.setKeyStorePath(this.getClass().getClassLoader().getResource(keystoreLocation).toExternalForm());
    sslContextFactory.setKeyStorePassword(keystorePass);
    sslContextFactory.setKeyManagerPassword(keystorePass);

    ServerConnector sslConnector = new ServerConnector(server,
        new SslConnectionFactory(sslContextFactory, PROTOCOL),
        new HttpConnectionFactory(https));
    sslConnector.setPort(Integer.parseInt(properties.getProperty("camunda.optimize.container.https.port")));
    sslConnector.setHost(host);
    return sslConnector;
  }

  private ServerConnector initHttpConnector(Properties properties, String host, Server server) {
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(Integer.parseInt(properties.getProperty("camunda.optimize.container.http.port")));
    connector.setHost(host);
    return connector;
  }

  /**
   * Since spring context is not loaded yet, just hook up properties manually.
   * @return
   */
  private Properties getProperties() {
    Properties result = new Properties();
    try {
      result.load(this.getClass().getClassLoader().getResourceAsStream("service.properties"));
      InputStream environmentProperties = this.getClass().getClassLoader().getResourceAsStream("environment.properties");
      if (environmentProperties != null) {
        //overwrites previously loaded properties
        result.load(environmentProperties);
      }
    } catch (IOException e) {
      logger.error("cant read properties", e);
    }
    return result;
  }

  public void start() throws Exception {
    this.jettyServer.start();
  }

  public void join() throws InterruptedException {
    jettyServer.join();
  }

  public void destroy() throws Exception {
    jettyServer.stop();
    jettyServer.destroy();
  }

  public ImportJobExecutor getImportJobExecutor() {
    return jerseyCamundaOptimize.getApplicationContext().getBean(ImportJobExecutor.class);
  }

  public ImportServiceProvider getImportServiceProvider() {
    return jerseyCamundaOptimize.getApplicationContext().getBean(ImportServiceProvider.class);
  }

  @Override
  public void startImportScheduler() {
    getImportScheduler().start();
  }

  private ImportScheduler getImportScheduler() {
    return jerseyCamundaOptimize.getApplicationContext().getBean(ImportScheduler.class);
  }

  @Override
  public void disableImportScheduler() {
    getImportScheduler().disable();
  }

  public void initializeIndex() {
    jerseyCamundaOptimize.getApplicationContext().getBean(ElasticSearchSchemaInitializer.class).initializeSchema();
  }
}
