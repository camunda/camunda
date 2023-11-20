/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import jakarta.servlet.DispatcherType;
import org.camunda.optimize.jetty.NotFoundErrorHandler;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants;
import org.camunda.optimize.service.util.configuration.security.ResponseHeadersConfiguration;
import org.camunda.optimize.util.jetty.LoggingConfigurationReader;
import org.camunda.optimize.websocket.StatusWebSocketServlet;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Slf4jRequestLogWriter;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.server.JettyWebSocketServlet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.jetty.JettyServerCustomizer;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;

import static org.camunda.optimize.jetty.OptimizeResourceConstants.STATUS_WEBSOCKET_PATH;
import static org.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.CONTEXT_PATH;
import static org.eclipse.jetty.servlet.ServletContextHandler.getServletContextHandler;

@Configuration
public class JettyConfig {

  private static final String COMPRESSED_MIME_TYPES = "application/json," +
    "text/html," +
    "application/x-font-ttf," +
    "image/svg+xml";
  private static final String PROTOCOL = "http/1.1";

  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private Environment environment;

  @Bean
  public JettyServerCustomizer httpsJettyServerCustomizer() {
    return server ->
      server.addConnector(initHttpsConnector(configurationService, configurationService.getContainerHost(), server));
  }

  // We use the @Order annotation to make sure that this is modified after the http connector
  @Bean
  @Order
  public JettyServerCustomizer httpConfigurationCustomizer() {
    return server ->
      Arrays.stream(server.getConnectors())
        .flatMap(connector -> connector.getConnectionFactories().stream())
        .filter(
          connectionFactory ->
            HttpConnectionFactory.class.isAssignableFrom(connectionFactory.getClass()))
        .map(HttpConnectionFactory.class::cast)
        .forEach(httpConnectionFactory -> applyHeaderSizeConfiguration(httpConnectionFactory.getHttpConfiguration()));
  }

  @Bean
  public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> optimizeWebServerFactoryCustomizer() {
    return factory -> {
      getContextPath().ifPresent(factory::setContextPath);
      String host = configurationService.getContainerHost();
      try {
        factory.setAddress(InetAddress.getByName(host));
      } catch (UnknownHostException ex) {
        throw new OptimizeConfigurationException("Invalid container host specified");
      }
      factory.setPort(getPort(EnvironmentPropertiesConstants.HTTP_PORT_KEY));
    };
  }

  @Bean
  public ServletContextInitializer servletContextInitializer() {
    return servletContext -> {
      ServletContextHandler servletContextHandler = getServletContextHandler(servletContext);

      addStaticResources(servletContextHandler);
      addGzipHandler(servletContextHandler);
      servletContextHandler.setErrorHandler(new NotFoundErrorHandler());

      Server server = servletContextHandler.getServer();
      setUpRequestLogging(server);
    };
  }

  @Bean
  public ServletRegistrationBean<JettyWebSocketServlet> socketServlet() {
    return new ServletRegistrationBean<>(new StatusWebSocketServlet(), STATUS_WEBSOCKET_PATH);
  }

  private void setUpRequestLogging(Server server) {
    final LoggingConfigurationReader loggingConfigurationReader = new LoggingConfigurationReader();
    loggingConfigurationReader.defineLogbackLoggingConfiguration();
    server.setRequestLog(new CustomRequestLog(new Slf4jRequestLogWriter(), CustomRequestLog.EXTENDED_NCSA_FORMAT));
  }

  private ServerConnector initHttpsConnector(
    ConfigurationService configurationService, String host, Server server) {
    HttpConfiguration https = new HttpConfiguration();
    https.setSendServerVersion(false);
    final SecureRequestCustomizer customizer =
      new SecureRequestCustomizer(
        configurationService.getContainerEnableSniCheck(),
        configurationService
          .getSecurityConfiguration()
          .getResponseHeaders()
          .getHttpStrictTransportSecurityMaxAge(),
        getResponseHeadersConfiguration(configurationService).getHttpStrictTransportSecurityIncludeSubdomains()
      );

    https.addCustomizer(customizer);
    https.setSecureScheme("https");
    applyHeaderSizeConfiguration(https);
    SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
    sslContextFactory.setKeyStorePath(configurationService.getContainerKeystoreLocation());
    sslContextFactory.setKeyStorePassword(configurationService.getContainerKeystorePassword());
    sslContextFactory.setKeyManagerPassword(configurationService.getContainerKeystorePassword());
    // not relevant for server setup but otherwise we get a warning on startup
    // see https://github.com/eclipse/jetty.project/issues/3049
    sslContextFactory.setEndpointIdentificationAlgorithm("HTTPS");

    ServerConnector sslConnector =
      new ServerConnector(server, new SslConnectionFactory(sslContextFactory, PROTOCOL), new HttpConnectionFactory(https));
    sslConnector.setPort(getPort(EnvironmentPropertiesConstants.HTTPS_PORT_KEY));
    sslConnector.setHost(host);
    return sslConnector;
  }

  public int getPort(String portType) {
    String portProperty = environment.getProperty(portType);
    if (portProperty == null) {
      return portType.equals(EnvironmentPropertiesConstants.HTTPS_PORT_KEY)
        ? configurationService.getContainerHttpsPort()
        : configurationService.getContainerHttpPort().orElse(8090);
    }
    return Integer.parseInt(portProperty);
  }

  public Optional<String> getContextPath() {
    // If the property is set by env var (the case when starting a new Optimize in ITs), this takes
    // precedence over config
    Optional<String> contextPath = Optional.ofNullable(environment.getProperty(CONTEXT_PATH));
    if (contextPath.isEmpty()) {
      return configurationService.getContextPath();
    }
    return contextPath;
  }

  private void applyHeaderSizeConfiguration(HttpConfiguration configuration) {
    configuration.setRequestHeaderSize(configurationService.getMaxRequestHeaderSizeInBytes());
    configuration.setResponseHeaderSize(configurationService.getMaxResponseHeaderSizeInBytes());
  }

  private void addStaticResources(ServletContextHandler servletContextHandler) {
    final URL webappURL = this.getClass().getClassLoader().getResource("webapp");
    if (webappURL != null) {
      servletContextHandler.setResourceBase(webappURL.toExternalForm());
    }
    final ServletHolder holderPwd = new ServletHolder("default", DefaultServlet.class);
    holderPwd.setInitParameter("dirAllowed", "false");
    ServletHolder externalHome = new ServletHolder("external-home", DefaultServlet.class);
    if (webappURL != null) {
      externalHome.setInitParameter("resourceBase", webappURL.toExternalForm());
    }
    externalHome.setInitParameter("dirAllowed", "true");
    // Use request pathInfo, don't calculate from contextPath
    externalHome.setInitParameter("pathInfoOnly", "true");
    servletContextHandler.addServlet(externalHome, "/external/*"); // must end in "/*" for pathInfo to work
    // Root path needs to be added last, otherwise it won't work
    servletContextHandler.addServlet(holderPwd, "/");
  }

  private void addGzipHandler(ServletContextHandler context) {
    GzipHandler gzipHandler = new GzipHandler();
    gzipHandler.setMinGzipSize(23);
    gzipHandler.setIncludedMimeTypes(COMPRESSED_MIME_TYPES);
    gzipHandler.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
    gzipHandler.setIncludedPaths("/*");
    context.insertHandler(gzipHandler);
  }

  public static ResponseHeadersConfiguration getResponseHeadersConfiguration(final ConfigurationService configurationService) {
    return configurationService.getSecurityConfiguration().getResponseHeaders();
  }
}
