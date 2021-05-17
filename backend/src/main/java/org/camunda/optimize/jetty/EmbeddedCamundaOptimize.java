/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.jetty;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.CamundaOptimize;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.importing.ImportSchedulerManagerService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.util.jetty.LoggingConfigurationReader;
import org.camunda.optimize.websocket.StatusWebSocket;
import org.eclipse.jetty.rewrite.handler.HeaderPatternRule;
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.rewrite.handler.RewriteRegexRule;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Slf4jRequestLogWriter;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.google.common.net.HttpHeaders.CONTENT_SECURITY_POLICY;
import static com.google.common.net.HttpHeaders.X_CONTENT_TYPE_OPTIONS;
import static com.google.common.net.HttpHeaders.X_XSS_PROTECTION;

/**
 * Jetty embedded server wrapping jersey servlet handler and loading properties from
 * service and environment property files.
 */
@Slf4j
public class EmbeddedCamundaOptimize implements CamundaOptimize {

  public static final String EXTERNAL_SUB_PATH = "/external";
  private static final String PROTOCOL = "http/1.1";
  private static final String EXTERNAL_SUB_PATH_PATTERN = "^" + EXTERNAL_SUB_PATH + "(/.*)$";

  static {
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();
  }

  private final SpringAwareServletConfiguration jerseyCamundaOptimize;
  private final Server jettyServer;

  public EmbeddedCamundaOptimize() {
    this(null);
  }

  public EmbeddedCamundaOptimize(String contextLocation) {
    jerseyCamundaOptimize = new SpringAwareServletConfiguration(contextLocation);
    jettyServer = setUpEmbeddedJetty(jerseyCamundaOptimize);
    disableServerSignature(jettyServer);
  }

  private void disableServerSignature(Server jettyServer) {
    for (Connector y : jettyServer.getConnectors()) {
      for (ConnectionFactory x : y.getConnectionFactories()) {
        if (x instanceof HttpConnectionFactory) {
          ((HttpConnectionFactory) x).getHttpConfiguration().setSendServerVersion(false);
        }
      }
    }
  }

  private Server setUpEmbeddedJetty(SpringAwareServletConfiguration jerseyCamundaOptimize) {
    final LoggingConfigurationReader loggingConfigurationReader = new LoggingConfigurationReader();
    loggingConfigurationReader.defineLogbackLoggingConfiguration();

    final ConfigurationService configurationService = constructConfigurationService();
    final Server newJettyServer = initServer(configurationService);
    final ServletContextHandler appServletContextHandler = jerseyCamundaOptimize.initServletContextHandler();

    final HandlerCollection handlerCollection = new HandlerCollection();
    handlerCollection.addHandler(createSecurityHeaderHandlers(configurationService));
    handlerCollection.addHandler(wrapWithExternalPathRewriteHandler(appServletContextHandler));
    newJettyServer.setHandler(handlerCollection);

    initWebSockets(appServletContextHandler);

    newJettyServer.setRequestLog(new CustomRequestLog(
      new Slf4jRequestLogWriter(), CustomRequestLog.EXTENDED_NCSA_FORMAT
    ));

    return newJettyServer;
  }

  private RewriteHandler wrapWithExternalPathRewriteHandler(final Handler handler) {
    RewriteHandler rewriteHandler = new RewriteHandler();
    rewriteHandler.setRewriteRequestURI(true);
    rewriteHandler.setRewritePathInfo(true);
    RewriteRegexRule shareUrlRewriteRule = new RewriteRegexRule(EXTERNAL_SUB_PATH_PATTERN, "$1");
    rewriteHandler.addRule(shareUrlRewriteRule);
    rewriteHandler.setHandler(handler);
    return rewriteHandler;
  }

  private RewriteHandler createSecurityHeaderHandlers(final ConfigurationService configurationService) {
    final RewriteHandler rewriteHandler = new RewriteHandler();
    HeaderPatternRule xssProtection =
      new HeaderPatternRule("*", X_XSS_PROTECTION, configurationService.getXXSSProtection());
    rewriteHandler.addRule(xssProtection);

    if (Boolean.TRUE.equals(configurationService.getXContentTypeOptions())) {
      final HeaderPatternRule xContentTypeOptions =
        new HeaderPatternRule("*", X_CONTENT_TYPE_OPTIONS, "nosniff");
      rewriteHandler.addRule(xContentTypeOptions);
    }

    final HeaderPatternRule contentSecurityPolicy =
      new HeaderPatternRule("*", CONTENT_SECURITY_POLICY, configurationService.getContentSecurityPolicy());
    rewriteHandler.addRule(contentSecurityPolicy);

    return rewriteHandler;
  }

  /**
   * Add javax.websocket support
   */
  private void initWebSockets(ServletContextHandler context) {
    WebSocketServerContainerInitializer.configure(
      context, (servletContext, serverContainer) -> serverContainer.addEndpoint(StatusWebSocket.class)
    );
  }

  protected ConfigurationService constructConfigurationService() {
    return ConfigurationServiceBuilder.createDefaultConfiguration();
  }

  private Server initServer(ConfigurationService configurationService) {
    String host = configurationService.getContainerHost();
    String keystorePass = configurationService.getContainerKeystorePassword();
    String keystoreLocation = configurationService.getContainerKeystoreLocation();
    Server server = new Server();

    List<Connector> connectors = new ArrayList<>();
    Optional<ServerConnector> connector = initHttpConnector(configurationService, host, server);
    connector.ifPresent(connectors::add);

    ServerConnector sslConnector =
      initHttpsConnector(configurationService, host, keystorePass, keystoreLocation, server);
    connectors.add(sslConnector);

    server.setConnectors(connectors.toArray(new Connector[]{}));

    return server;
  }

  private ServerConnector initHttpsConnector(ConfigurationService configurationService, String host,
                                             String keystorePass, String keystoreLocation, Server server) {
    HttpConfiguration https = new HttpConfiguration();
    https.setSendServerVersion(false);
    https.addCustomizer(new SecureRequestCustomizer(
      true,
      configurationService.getHTTPStrictTransportSecurityMaxAge(),
      configurationService.getHTTPStrictTransportSecurityIncludeSubdomains()
    ));
    https.setSecureScheme("https");
    SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
    sslContextFactory.setKeyStorePath(keystoreLocation);
    sslContextFactory.setKeyStorePassword(keystorePass);
    sslContextFactory.setKeyManagerPassword(keystorePass);
    // not relevant for server setup but otherwise we get a warning on startup
    // see https://github.com/eclipse/jetty.project/issues/3049
    sslContextFactory.setEndpointIdentificationAlgorithm("HTTPS");

    ServerConnector sslConnector = new ServerConnector(
      server,
      new SslConnectionFactory(sslContextFactory, PROTOCOL),
      new HttpConnectionFactory(https)
    );
    sslConnector.setPort(configurationService.getContainerHttpsPort());
    sslConnector.setHost(host);
    return sslConnector;
  }

  private Optional<ServerConnector> initHttpConnector(ConfigurationService configurationService,
                                                      String host,
                                                      Server server) {
    return configurationService.getContainerHttpPort()
      .map(
        httpPort -> {
          ServerConnector connector = new ServerConnector(server);
          connector.setPort(httpPort);
          connector.setHost(host);
          return connector;
        }
      );
  }

  public void startOptimize() throws Exception {
    this.jettyServer.start();
    scanPluginPackages();
  }

  private void scanPluginPackages() {
    final List<String> variableImportPluginBasePackages =
      constructConfigurationService().getVariableImportPluginBasePackages();
    if (!variableImportPluginBasePackages.isEmpty()) {
      ((AnnotationConfigWebApplicationContext) getOptimizeApplicationContext())
        .scan(
          variableImportPluginBasePackages
            .toArray(new String[0])
        );
    }
  }

  public boolean isOptimizeStarted() {
    return jettyServer.isStarted();
  }

  public void join() throws InterruptedException {
    jettyServer.join();
  }

  public void destroyOptimize() throws Exception {
    jettyServer.stop();
    jettyServer.destroy();
  }

  @Override
  public void startEngineImportSchedulers() {
    getImportSchedulerManager().startSchedulers();
  }

  @Override
  public void stopImportSchedulers() {
    getImportSchedulerManager().stopSchedulers();
  }

  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return getOptimizeApplicationContext().getBean(ElasticsearchImportJobExecutor.class);
  }

  protected ApplicationContext getOptimizeApplicationContext() {
    return jerseyCamundaOptimize.getApplicationContext();
  }

  private ImportSchedulerManagerService getImportSchedulerManager() {
    return getOptimizeApplicationContext().getBean(ImportSchedulerManagerService.class);
  }
}
