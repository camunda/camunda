/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import static org.camunda.optimize.jetty.OptimizeResourceConstants.STATUS_WEBSOCKET_PATH;
import static org.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.CONTEXT_PATH;
import static org.eclipse.jetty.servlet.ServletContextHandler.getServletContextHandler;

import jakarta.servlet.DispatcherType;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.Security;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import org.camunda.optimize.jetty.NotFoundErrorHandler;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants;
import org.camunda.optimize.service.util.configuration.security.ResponseHeadersConfiguration;
import org.camunda.optimize.util.jetty.LoggingConfigurationReader;
import org.camunda.optimize.websocket.StatusWebSocketServlet;
import org.conscrypt.OpenSSLProvider;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
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

@Configuration
public class JettyConfig {

  private static final String COMPRESSED_MIME_TYPES =
      "application/json," + "text/html," + "application/x-font-ttf," + "image/svg+xml";
  private static final String PROTOCOL = "http/1.1";

  @Autowired private ConfigurationService configurationService;
  @Autowired private Environment environment;

  @Bean
  public JettyServerCustomizer httpsJettyServerCustomizer() {
    return server -> {
      if (configurationService.getContainerHttp2Enabled()) {
        server.addConnector(initHttp2Connector(server));
      } else {
        server.addConnector(initHttpsConnector(server));
      }
    };
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
            .forEach(
                httpConnectionFactory ->
                    applyHeaderSizeConfiguration(httpConnectionFactory.getHttpConfiguration()));
  }

  @Bean
  public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory>
      optimizeWebServerFactoryCustomizer() {
    return factory -> {
      getContextPath().ifPresent(factory::setContextPath);
      final String host = configurationService.getContainerHost();
      try {
        factory.setAddress(InetAddress.getByName(host));
      } catch (final UnknownHostException ex) {
        throw new OptimizeConfigurationException("Invalid container host specified");
      }
      factory.setPort(getPort(EnvironmentPropertiesConstants.HTTP_PORT_KEY));
    };
  }

  @Bean
  public ServletContextInitializer servletContextInitializer() {
    return servletContext -> {
      final ServletContextHandler servletContextHandler = getServletContextHandler(servletContext);

      addStaticResources(servletContextHandler);
      addGzipHandler(servletContextHandler);
      servletContextHandler.setErrorHandler(new NotFoundErrorHandler());

      final Server server = servletContextHandler.getServer();
      setUpRequestLogging(server);
    };
  }

  @Bean
  public ServletRegistrationBean<JettyWebSocketServlet> socketServlet() {
    return new ServletRegistrationBean<>(new StatusWebSocketServlet(), STATUS_WEBSOCKET_PATH);
  }

  private ServerConnector initHttp2Connector(final Server server) {
    final HttpConfiguration https = getHttpsConfiguration(configurationService);

    final HttpConnectionFactory http11 = new HttpConnectionFactory(https);
    final HTTP2ServerConnectionFactory http2 = new HTTP2ServerConnectionFactory(https);
    final ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory();
    // If no ALPN support, use http/1.1 as a fallback. http2 will be preferred protocol still
    alpn.setDefaultProtocol(http11.getProtocol());

    final SslContextFactory.Server sslContextFactory = setupSslContextFactory(true);

    final SslConnectionFactory tls =
        new SslConnectionFactory(sslContextFactory, alpn.getProtocol());

    final ServerConnector sslConnector = new ServerConnector(server, tls, alpn, http2, http11);
    setPortAndHost(configurationService.getContainerHost(), sslConnector);
    return sslConnector;
  }

  private ServerConnector initHttpsConnector(final Server server) {
    final HttpConfiguration https = getHttpsConfiguration(configurationService);

    final SslContextFactory.Server sslContextFactory = setupSslContextFactory(false);

    final ServerConnector sslConnector =
        new ServerConnector(
            server,
            new SslConnectionFactory(sslContextFactory, PROTOCOL),
            new HttpConnectionFactory(https));
    setPortAndHost(configurationService.getContainerHost(), sslConnector);
    return sslConnector;
  }

  private HttpConfiguration getHttpsConfiguration(final ConfigurationService configurationService) {
    final HttpConfiguration https = new HttpConfiguration();
    https.setSendServerVersion(false);
    final SecureRequestCustomizer customizer =
        new SecureRequestCustomizer(
            configurationService.getContainerEnableSniCheck(),
            configurationService
                .getSecurityConfiguration()
                .getResponseHeaders()
                .getHttpStrictTransportSecurityMaxAge(),
            getResponseHeadersConfiguration(configurationService)
                .getHttpStrictTransportSecurityIncludeSubdomains());

    https.addCustomizer(customizer);
    https.setSecureScheme("https");
    applyHeaderSizeConfiguration(https);

    return https;
  }

  private void setUpRequestLogging(final Server server) {
    final LoggingConfigurationReader loggingConfigurationReader = new LoggingConfigurationReader();
    loggingConfigurationReader.defineLogbackLoggingConfiguration();
    server.setRequestLog(
        new CustomRequestLog(new Slf4jRequestLogWriter(), CustomRequestLog.EXTENDED_NCSA_FORMAT));
  }

  private void setPortAndHost(final String host, final ServerConnector sslConnector) {
    sslConnector.setPort(getPort(EnvironmentPropertiesConstants.HTTPS_PORT_KEY));
    sslConnector.setHost(host);
  }

  private SslContextFactory.Server setupSslContextFactory(final boolean isHttp2) {
    final SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
    sslContextFactory.setKeyStorePath(configurationService.getContainerKeystoreLocation());
    sslContextFactory.setKeyStorePassword(configurationService.getContainerKeystorePassword());
    sslContextFactory.setKeyManagerPassword(configurationService.getContainerKeystorePassword());
    // not relevant for server setup but otherwise we get a warning on startup
    // see https://github.com/eclipse/jetty.project/issues/3049
    sslContextFactory.setEndpointIdentificationAlgorithm("HTTPS");

    if (isHttp2) {
      // Configure the JDK with the Conscrypt provider.
      Security.addProvider(new OpenSSLProvider());

      sslContextFactory.setProvider("Conscrypt");
    }

    return sslContextFactory;
  }

  public int getPort(final String portType) {
    final String portProperty = environment.getProperty(portType);
    if (portProperty == null) {
      return portType.equals(EnvironmentPropertiesConstants.HTTPS_PORT_KEY)
          ? configurationService.getContainerHttpsPort()
          : configurationService.getContainerHttpPort().orElse(8090);
    }
    try {
      return Integer.parseInt(portProperty);
    } catch (final NumberFormatException exception) {
      throw new OptimizeConfigurationException("Error while determining container port");
    }
  }

  public Optional<String> getContextPath() {
    // If the property is set by env var (the case when starting a new Optimize in ITs), this takes
    // precedence over config
    final Optional<String> contextPath = Optional.ofNullable(environment.getProperty(CONTEXT_PATH));
    if (contextPath.isEmpty()) {
      return configurationService.getContextPath();
    }
    return contextPath;
  }

  private void applyHeaderSizeConfiguration(final HttpConfiguration configuration) {
    configuration.setRequestHeaderSize(configurationService.getMaxRequestHeaderSizeInBytes());
    configuration.setResponseHeaderSize(configurationService.getMaxResponseHeaderSizeInBytes());
  }

  private void addStaticResources(final ServletContextHandler servletContextHandler) {
    final URL webappURL = getClass().getClassLoader().getResource("webapp");
    if (webappURL != null) {
      servletContextHandler.setResourceBase(webappURL.toExternalForm());
    }
    final ServletHolder holderPwd = new ServletHolder("default", DefaultServlet.class);
    holderPwd.setInitParameter("dirAllowed", "false");
    final ServletHolder externalHome = new ServletHolder("external-home", DefaultServlet.class);
    if (webappURL != null) {
      externalHome.setInitParameter("resourceBase", webappURL.toExternalForm());
    }
    externalHome.setInitParameter("dirAllowed", "true");
    // Use request pathInfo, don't calculate from contextPath
    externalHome.setInitParameter("pathInfoOnly", "true");
    servletContextHandler.addServlet(
        externalHome, "/external/*"); // must end in "/*" for pathInfo to work
    // Root path needs to be added last, otherwise it won't work
    servletContextHandler.addServlet(holderPwd, "/");
  }

  private void addGzipHandler(final ServletContextHandler context) {
    final GzipHandler gzipHandler = new GzipHandler();
    gzipHandler.setMinGzipSize(23);
    gzipHandler.setIncludedMimeTypes(COMPRESSED_MIME_TYPES);
    gzipHandler.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
    gzipHandler.setIncludedPaths("/*");
    context.insertHandler(gzipHandler);
  }

  public static ResponseHeadersConfiguration getResponseHeadersConfiguration(
      final ConfigurationService configurationService) {
    return configurationService.getSecurityConfiguration().getResponseHeaders();
  }
}
