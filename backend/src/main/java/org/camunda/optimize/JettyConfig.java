/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

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
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.servlet.DispatcherType;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
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
  public ServletWebServerFactory servletContainer() {
    JettyServletWebServerFactory jetty = getContextPath()
      .map(contextPath -> new JettyServletWebServerFactory(contextPath, getPort(EnvironmentPropertiesConstants.HTTP_PORT_KEY)))
      .orElseGet(() -> new JettyServletWebServerFactory(getPort(EnvironmentPropertiesConstants.HTTP_PORT_KEY)));
    String host = configurationService.getContainerHost();

    try {
      jetty.setAddress(InetAddress.getByName(host));
    } catch (UnknownHostException ex) {
      throw new OptimizeConfigurationException("Invalid container host specified");
    }
    jetty.addServerCustomizers(server -> server.addConnector(
      initHttpsConnector(
        configurationService, host, configurationService.getContainerKeystorePassword(),
        configurationService.getContainerKeystoreLocation(), server
      )
    ));
    return jetty;
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
  public ServletRegistrationBean<WebSocketServlet> socketServlet() {
    return new ServletRegistrationBean<>(new StatusWebSocketServlet(), STATUS_WEBSOCKET_PATH);
  }

  private void setUpRequestLogging(Server server) {
    final LoggingConfigurationReader loggingConfigurationReader = new LoggingConfigurationReader();
    loggingConfigurationReader.defineLogbackLoggingConfiguration();

    server.setRequestLog(new CustomRequestLog(
      new Slf4jRequestLogWriter(), CustomRequestLog.EXTENDED_NCSA_FORMAT
    ));
  }

  private ServerConnector initHttpsConnector(ConfigurationService configurationService, String host,
                                             String keystorePass, String keystoreLocation, Server server) {
    HttpConfiguration https = new HttpConfiguration();
    https.setSendServerVersion(false);
    https.addCustomizer(new SecureRequestCustomizer(
      true,
      configurationService.getSecurityConfiguration().getResponseHeaders().getHttpStrictTransportSecurityMaxAge(),
      getResponseHeadersConfiguration(configurationService).getHttpStrictTransportSecurityIncludeSubdomains()
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

    sslConnector.setPort(getPort(EnvironmentPropertiesConstants.HTTPS_PORT_KEY));
    sslConnector.setHost(host);
    return sslConnector;
  }

  public int getPort(String portType) {
    String portProperty = environment.getProperty(portType);
    if (portProperty == null) {
      return portType.equals(EnvironmentPropertiesConstants.HTTPS_PORT_KEY) ? configurationService.getContainerHttpsPort() :
        configurationService.getContainerHttpPort().orElse(8090);
    }
    return Integer.parseInt(portProperty);
  }

  public Optional<String> getContextPath() {
    // If the property is set by env var (the case when starting a new Optimize in ITs), this takes precedence over config
    Optional<String> contextPath = Optional.ofNullable(environment.getProperty(CONTEXT_PATH));
    if (contextPath.isEmpty()) {
      return configurationService.getContextPath();
    }
    return contextPath;
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
    gzipHandler.setCompressionLevel(9);
    gzipHandler.setMinGzipSize(23);
    gzipHandler.setIncludedMimeTypes(COMPRESSED_MIME_TYPES);
    gzipHandler.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
    gzipHandler.setIncludedPaths("/*");
    context.setGzipHandler(gzipHandler);
  }

  public static ResponseHeadersConfiguration getResponseHeadersConfiguration(final ConfigurationService configurationService) {
    return configurationService.getSecurityConfiguration().getResponseHeaders();
  }
}
