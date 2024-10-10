/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.CONTEXT_PATH;
import static org.eclipse.jetty.ee10.servlet.ServletContextHandler.getServletContextHandler;

import io.camunda.optimize.jetty.OptimizeResourceConstants;
import io.camunda.optimize.rest.HealthRestService;
import io.camunda.optimize.rest.LocalizationRestService;
import io.camunda.optimize.rest.UIConfigurationRestService;
import io.camunda.optimize.rest.constants.RestConstants;
import io.camunda.optimize.rest.security.cloud.CCSaasAuth0WebSecurityConfig;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.util.PanelNotificationConstants;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants;
import io.camunda.optimize.service.util.configuration.security.ResponseHeadersConfiguration;
import io.camunda.optimize.util.jetty.LoggingConfigurationReader;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Optional;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.rewrite.handler.RewriteRegexRule;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Slf4jRequestLogWriter;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.jetty.JettyServerCustomizer;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class JettyConfig {

  private static final String COMPRESSED_MIME_TYPES =
      "application/json," + "text/html," + "application/x-font-ttf," + "image/svg+xml";
  private static final String PROTOCOL = "http/1.1";
  private static final String LOGIN_ENDPOINT = "/login";
  private static final String METRICS_ENDPOINT = "/metrics";
  private static final String URL_BASE = "/#";

  public static final String ALLOWED_URL_EXTENSION =
      String.join(
          "|",
          new String[] {
            URL_BASE,
            LOGIN_ENDPOINT,
            METRICS_ENDPOINT,
            CCSaasAuth0WebSecurityConfig.OAUTH_AUTH_ENDPOINT,
            CCSaasAuth0WebSecurityConfig.OAUTH_REDIRECT_ENDPOINT,
            CCSaasAuth0WebSecurityConfig.AUTH0_JWKS_ENDPOINT,
            CCSaasAuth0WebSecurityConfig.AUTH0_AUTH_ENDPOINT,
            CCSaasAuth0WebSecurityConfig.AUTH0_TOKEN_ENDPOINT,
            CCSaasAuth0WebSecurityConfig.AUTH0_USERINFO_ENDPOINT,
            HealthRestService.READYZ_PATH,
            LocalizationRestService.LOCALIZATION_PATH,
            OptimizeJettyServerCustomizer.EXTERNAL_SUB_PATH,
            OptimizeResourceConstants.REST_API_PATH,
            OptimizeResourceConstants.STATIC_RESOURCE_PATH,
            OptimizeResourceConstants.ACTUATOR_ENDPOINT,
            PanelNotificationConstants.SEND_NOTIFICATION_TO_ALL_ORG_USERS_ENDPOINT,
            RestConstants.BACKUP_ENDPOINT,
            UIConfigurationRestService.UI_CONFIGURATION_PATH
          });

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

      addURLRedirects(servletContextHandler);

      addStaticResources(servletContextHandler);
      addGzipHandler(servletContextHandler);

      final Server server = servletContextHandler.getServer();
      setUpRequestLogging(server);
    };
  }

  /** redirect to /# when the endpoint is not valid. do this rather than showing an error page */
  private void addURLRedirects(final ServletContextHandler servletContextHandler) {
    final String regex =
        "^(?!" + getContextPath().orElse("") + "(" + ALLOWED_URL_EXTENSION + ")).+";

    final RewriteRegexRule badUrlRegex =
        new RewriteRegexRule(regex, getContextPath().orElse("") + "/#");
    final RewriteHandler rewrite = new RewriteHandler();
    rewrite.addRule(badUrlRegex);
    servletContextHandler.insertHandler(rewrite);
  }

  private ServerConnector initHttp2Connector(final Server server) {
    final HttpConfiguration https = getHttpsConfiguration(configurationService);

    final HttpConnectionFactory http11 = new HttpConnectionFactory(https);
    final HTTP2ServerConnectionFactory http2 = new HTTP2ServerConnectionFactory(https);
    final ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory();
    // If no ALPN support, use http/1.1 as a fallback. http2 will be preferred protocol still
    alpn.setDefaultProtocol(http11.getProtocol());

    final SslContextFactory.Server sslContextFactory = setupSslContextFactory();

    final SslConnectionFactory tls =
        new SslConnectionFactory(sslContextFactory, alpn.getProtocol());

    final ServerConnector sslConnector = new ServerConnector(server, tls, alpn, http2, http11);
    setPortAndHost(configurationService.getContainerHost(), sslConnector);
    return sslConnector;
  }

  private ServerConnector initHttpsConnector(final Server server) {
    final HttpConfiguration https = getHttpsConfiguration(configurationService);

    final SslContextFactory.Server sslContextFactory = setupSslContextFactory();

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

  private SslContextFactory.Server setupSslContextFactory() {
    final SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
    sslContextFactory.setKeyStorePath(configurationService.getContainerKeystoreLocation());
    sslContextFactory.setKeyStorePassword(configurationService.getContainerKeystorePassword());
    sslContextFactory.setKeyManagerPassword(configurationService.getContainerKeystorePassword());
    // not relevant for server setup but otherwise we get a warning on startup
    // see https://github.com/eclipse/jetty.project/issues/3049
    sslContextFactory.setEndpointIdentificationAlgorithm("HTTPS");

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

  private void addStaticResources(final ServletContextHandler servletContextHandler) {
    final URL webappURL = getClass().getClassLoader().getResource("webapp");
    if (webappURL != null) {
      final Resource resource =
          ResourceFactory.of(new ResourceHandler()).newResource(webappURL.toExternalForm());
      servletContextHandler.setBaseResource(resource);
    }
    final ServletHolder externalHome = new ServletHolder("external-home", DefaultServlet.class);
    if (webappURL != null) {
      externalHome.setInitParameter("resourceBase", webappURL.toExternalForm());
    }
    externalHome.setInitParameter("dirAllowed", "true");
    // Use request pathInfo, don't calculate from contextPath
    externalHome.setInitParameter("pathInfoOnly", "true");
    servletContextHandler.addServlet(
        externalHome, "/external/*"); // must end in "/*" for pathInfo to work
  }

  private void addGzipHandler(final ServletContextHandler context) {
    final GzipHandler gzipHandler = new GzipHandler();
    gzipHandler.setMinGzipSize(23);
    gzipHandler.setIncludedMimeTypes(COMPRESSED_MIME_TYPES);
    gzipHandler.addIncludedMethods(
        HttpMethod.GET.asString(),
        HttpMethod.POST.asString(),
        HttpMethod.PUT.asString(),
        HttpMethod.PATCH.asString(),
        HttpMethod.DELETE.asString());
    gzipHandler.setIncludedPaths("/*");
    context.insertHandler(gzipHandler);
  }

  public static ResponseHeadersConfiguration getResponseHeadersConfiguration(
      final ConfigurationService configurationService) {
    return configurationService.getSecurityConfiguration().getResponseHeaders();
  }
}
