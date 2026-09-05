/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.CONTEXT_PATH;
import static io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.HTTP_PORT_KEY;

import io.camunda.optimize.rest.HealthRestService;
import io.camunda.optimize.rest.LocalizationRestService;
import io.camunda.optimize.rest.UIConfigurationRestService;
import io.camunda.optimize.rest.constants.RestConstants;
import io.camunda.optimize.rest.security.cloud.CCSaasAuth0WebSecurityConfig;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.util.PanelNotificationConstants;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants;
import io.camunda.optimize.tomcat.OptimizeResourceConstants;
import io.camunda.optimize.tomcat.ResponseSecurityHeaderFilter;
import io.camunda.optimize.tomcat.ResponseTimezoneFilter;
import io.camunda.optimize.tomcat.URLRedirectFilter;
import java.util.Optional;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.valves.rewrite.RewriteValve;
import org.apache.commons.lang3.StringUtils;
import org.apache.coyote.http2.Http2Protocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class OptimizeTomcatConfig {

  public static final String EXTERNAL_SUB_PATH = "/external";
  private static final Logger LOG = LoggerFactory.getLogger(OptimizeTomcatConfig.class);

  private static final String[] COMPRESSED_MIME_TYPES = {
    "application/json", "text/html", "application/x-font-ttf", "image/svg+xml"
  };

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
            OptimizeTomcatConfig.EXTERNAL_SUB_PATH,
            OptimizeResourceConstants.REST_API_PATH,
            OptimizeResourceConstants.STATIC_RESOURCE_PATH,
            OptimizeResourceConstants.ACTUATOR_ENDPOINT,
            PanelNotificationConstants.SEND_NOTIFICATION_TO_ALL_ORG_USERS_ENDPOINT,
            RestConstants.BACKUP_ENDPOINT,
            UIConfigurationRestService.UI_CONFIGURATION_PATH,
            "/favicon.ico",
            "/index.html"
          });

  /**
   * Extra allowed suffixes for CSL mode ({@code optimize.security.csl.enabled=true}), appended to
   * {@link #ALLOWED_URL_EXTENSION}. CSL serves login initiation from {@code
   * /oauth2/authorization/{registrationId}} and logout from {@code /logout}; neither exists in the
   * legacy stack, so both would otherwise be rewritten to the SPA home instead of reaching the
   * security chain. See <a
   * href="https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0038-optimize-reuses-stateful-oidc-webapp-chain.md">ADR-0038</a>.
   *
   * <p>The CSL OIDC callback needs no entry of its own: it is {@code /api/authentication/callback}
   * on CCSM and {@code /sso-callback} on CCSaaS, both already covered above.
   */
  private static final String CSL_ALLOWED_URL_EXTENSION =
      String.join("|", new String[] {"/oauth2", "/logout"});

  /** Readiness endpoint, relative to the servlet context path. */
  private static final String READYZ_ENDPOINT =
      OptimizeResourceConstants.REST_API_PATH + HealthRestService.READYZ_PATH;

  private static final String HTTP11_NIO_PROTOCOL = "org.apache.coyote.http11.Http11Nio2Protocol";

  @Autowired private ConfigurationService configurationService;
  @Autowired private Environment environment;

  @Bean
  WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatFactoryCustomizer() {
    LOG.debug("Setting up server connectors...");
    return new WebServerFactoryCustomizer<TomcatServletWebServerFactory>() {
      @Override
      public void customize(final TomcatServletWebServerFactory factory) {
        final Optional<String> contextPath = getContextPath();
        if (contextPath.isPresent()) {
          factory.setContextPath(contextPath.get());
          if (isCslEnabled() && servesUnderSubPath(contextPath.get())) {
            factory.addEngineValves(readyzAtRootValve(contextPath.get()));
          }
        }

        // NOTE: With the current implementation, we install the mandatory HTTPS connector first,
        // which can be HTTP/1.1 or HTTP/2 depending on the configuration, and then optionally add
        // an HTTP connector if the HTTP port is configured.
        factory.addConnectorCustomizers(
            connector -> {
              configureHttpsConnector(connector);
            });

        if (getPort(HTTP_PORT_KEY) >= 0) {
          factory.addAdditionalConnectors(
              new Connector() {
                {
                  configureHttpConnector(this);
                }
              });
        } else {
          LOG.info(
              "HTTP port is not configured. HTTP connector will not be started. Only HTTPS will be available.");
        }
      }
    };
  }

  @Bean
  /* redirect to /# when the endpoint is not valid. do this rather than showing an error page */
  FilterRegistrationBean<URLRedirectFilter> urlRedirector() {
    LOG.debug("Registering filter 'urlRedirector'...");

    final String contextPath = getContextPath().orElse("");
    final String regex = buildRedirectExclusionRegex(contextPath, isCslEnabled());

    final URLRedirectFilter filter = new URLRedirectFilter(regex, contextPath + URL_BASE);
    final FilterRegistrationBean<URLRedirectFilter> registration = new FilterRegistrationBean<>();
    registration.addUrlPatterns("/*");
    registration.setFilter(filter);
    return registration;
  }

  /**
   * Builds the regex matching every request URI the SPA-routing filter should rewrite to {@code
   * /#}, that is everything which is not one of the allowed suffixes. The home page URL is not
   * covered here; {@link URLRedirectFilter} handles it explicitly.
   *
   * @param cslEnabled when {@code true}, the CSL auth endpoints are allowed through as well
   */
  static String buildRedirectExclusionRegex(final String contextPath, final boolean cslEnabled) {
    final String allowed =
        cslEnabled
            ? ALLOWED_URL_EXTENSION + "|" + CSL_ALLOWED_URL_EXTENSION
            : ALLOWED_URL_EXTENSION;
    return "^(?!" + "(" + contextPath + allowed + ")).+";
  }

  private boolean isCslEnabled() {
    return environment.getProperty("optimize.security.csl.enabled", Boolean.class, true);
  }

  @Bean
  FilterRegistrationBean<ResponseSecurityHeaderFilter> responseHeadersInjector() {
    LOG.debug("Registering filter 'responseHeadersInjector'...");
    final ResponseSecurityHeaderFilter responseSecurityHeaderFilter =
        new ResponseSecurityHeaderFilter(configurationService);
    final FilterRegistrationBean<ResponseSecurityHeaderFilter> registrationBean =
        new FilterRegistrationBean<>();
    registrationBean.addUrlPatterns("/*");
    registrationBean.setFilter(responseSecurityHeaderFilter);
    return registrationBean;
  }

  @Bean
  FilterRegistrationBean<ResponseTimezoneFilter> responseTimezoneFilter() {
    LOG.debug("Registering filter 'responseTimezoneFilter'...");
    final ResponseTimezoneFilter filter = new ResponseTimezoneFilter();
    final FilterRegistrationBean<ResponseTimezoneFilter> registrationBean =
        new FilterRegistrationBean<>();
    registrationBean.addUrlPatterns("/*");
    registrationBean.setFilter(filter);
    return registrationBean;
  }

  public int getPort(final String portType) {
    final String portProperty = environment.getProperty(portType);
    if (StringUtils.isNotBlank(portProperty)) {
      try {
        return Integer.parseInt(portProperty);
      } catch (final NumberFormatException exception) {
        throw new OptimizeConfigurationException("Error while determining container port");
      }
    }

    if (portType.equals(EnvironmentPropertiesConstants.HTTPS_PORT_KEY)) {
      return configurationService.getContainerHttpsPort();
    }

    final Optional<Integer> httpPort = configurationService.getContainerHttpPort();
    return httpPort.filter(port -> port > 0).orElse(-1);
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

  private SSLHostConfig getSslHostConfig() {
    final SSLHostConfig sslHostConfig = new SSLHostConfig();

    final SSLHostConfigCertificate cert =
        new SSLHostConfigCertificate(sslHostConfig, Type.UNDEFINED);
    cert.setCertificateKeystoreFile(configurationService.getContainerKeystoreLocation());
    cert.setCertificateKeystorePassword(configurationService.getContainerKeystorePassword());
    sslHostConfig.addCertificate(cert);

    return sslHostConfig;
  }

  private void enableGzipSupport(final Connector connector) {
    connector.setProperty("compression", "on");
    connector.setProperty("compressionMinSize", "23");
    connector.setProperty("compressionNoCompressionMethods", ""); // all methods
    connector.setProperty("useSendfile", "false");
    connector.setProperty("compressableMimeType", String.join(",", COMPRESSED_MIME_TYPES));
  }

  private void applyCommonConfiguration(final Connector connector) {
    connector.setXpoweredBy(false); // do not send server version header
    enableGzipSupport(connector);
    connector.setProperty(
        "maxHttpRequestHeaderSize",
        String.valueOf(configurationService.getMaxRequestHeaderSizeInBytes()));
    connector.setProperty(
        "maxHttpResponseHeaderSize",
        String.valueOf(configurationService.getMaxResponseHeaderSizeInBytes()));
  }

  private void configureHttpConnector(final Connector connector) {
    applyCommonConfiguration(connector);
    connector.setPort(getPort(EnvironmentPropertiesConstants.HTTP_PORT_KEY));
    connector.setScheme("http");
    connector.setSecure(false);
  }

  public void configureHttpsConnector(final Connector connector) {
    applyCommonConfiguration(connector);
    connector.setPort(getPort(EnvironmentPropertiesConstants.HTTPS_PORT_KEY));
    connector.setScheme("https");
    connector.setProperty("SSLEnabled", "true");
    connector.setSecure(true);

    connector.setProperty("protocol", HTTP11_NIO_PROTOCOL);
    if (configurationService.getContainerHttp2Enabled()) {
      connector.addUpgradeProtocol(new Http2Protocol());
    }

    connector.addSslHostConfig(getSslHostConfig());
  }

  /**
   * True when the context path actually moves the app off the root. A blank or {@code "/"} context
   * path already serves the readiness endpoint at the root, so rewriting would be a no-op.
   */
  private static boolean servesUnderSubPath(final String contextPath) {
    return StringUtils.isNotBlank(contextPath) && !"/".equals(contextPath.trim());
  }

  /** Builds the readiness rewrite valve for the given servlet context path. */
  static RewriteValve readyzAtRootValve(final String contextPath) {
    return new ReadyzAtRootValve(contextPath);
  }

  /**
   * Serves {@link #READYZ_ENDPOINT} at the root path in addition to the servlet context path, by
   * rewriting {@code /api/readyz} to {@code <contextPath>/api/readyz} in the Tomcat engine
   * pipeline, which runs before a request is mapped to a context.
   *
   * <p>CSL mode derives a {@code /<clusterId>} servlet context path on CCSaaS, but the SaaS
   * readiness and liveness probes target {@code /api/readyz} on the main connector without that
   * prefix, so they would 404 and the pod would never become ready. Rewriting keeps those probes
   * working without a deployment change.
   *
   * <p>Only the readiness endpoint is rewritten. It is an unprotected path, so this grants no
   * access that the context-path-prefixed URL does not already grant.
   */
  private static final class ReadyzAtRootValve extends RewriteValve {

    private final String rule;

    private ReadyzAtRootValve(final String contextPath) {
      rule = "RewriteRule ^" + READYZ_ENDPOINT + "$ " + contextPath + READYZ_ENDPOINT;
    }

    @Override
    protected void startInternal() throws LifecycleException {
      // The superclass looks for a rewrite.config file, finds none and leaves the rules untouched;
      // it also initialises the logger that setConfiguration needs, so ours must be applied after.
      super.startInternal();
      try {
        setConfiguration(rule);
      } catch (final Exception e) {
        throw new LifecycleException("Could not apply the readiness rewrite rule: " + rule, e);
      }
    }
  }
}
