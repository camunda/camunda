/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import io.camunda.authentication.clusteradmin.ClusterApiSettings;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import java.lang.reflect.Method;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.connector.Connector;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.ReflectionUtils;

/**
 * Serves the cluster API ({@code /cluster/v2/**}) on a dedicated port when {@value
 * ClusterApiSettings#PORT_PROPERTY} is set.
 *
 * <p>The port is an additional connector on the same embedded Tomcat, so the cluster API keeps the
 * one application context, {@code DispatcherServlet} and security filter chain it has on the main
 * port; {@link ClusterApiPortFilter} decides which paths each port serves. The connector gets its
 * own request thread pool, so a saturated main port does not starve cluster operations.
 *
 * <p>Spring Boot applies the {@code server.*} connector settings (TLS, HTTP/2, compression, URI
 * encoding, connector and protocol handler customizers such as header size limits, virtual threads
 * or encoded-slash handling) to the main connector only. The dedicated connector receives the same
 * settings, so it inherits the main port's TLS: cluster-admin credentials never travel in plain
 * text on a cluster whose REST API uses TLS. They are applied when the connector initializes, after
 * every {@link WebServerFactoryCustomizer} has run, so customizers registered later are not missed.
 * Without {@value ClusterApiSettings#ADDRESS_PROPERTY}, the connector binds to {@code
 * server.address} like the main one.
 *
 * <p>Only active with the REST gateway, and startup fails unless a cluster-admin security chain is
 * defined: the dedicated port never serves the cluster API unprotected.
 */
@NullMarked
@Configuration(proxyBeanMethods = false)
@Conditional(ClusterApiSettings.DedicatedPortCondition.class)
@ConditionalOnRestGatewayEnabled
public class ClusterApiServerConfiguration {

  // Before PhysicalTenantFilter (-101) and Spring Security (-100): a request on the wrong port is
  // rejected before any tenant resolution or authentication runs.
  private static final int CLUSTER_API_PORT_FILTER_ORDER = -102;

  private static final Logger LOG = LoggerFactory.getLogger(ClusterApiServerConfiguration.class);

  // Resolved eagerly so a Boot upgrade that removes the hook fails at startup, not at first bind.
  private static final Method CUSTOMIZE_CONNECTOR = findCustomizeConnector();

  @Bean
  public WebServerFactoryCustomizer<TomcatServletWebServerFactory> clusterApiConnectorCustomizer(
      final Environment environment, final ListableBeanFactory beanFactory) {
    final ClusterApiSettings settings = ClusterApiSettings.load(environment);
    requireClusterAdminChain(beanFactory);
    return factory -> {
      final Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
      connector.addLifecycleListener(
          event -> {
            if (Lifecycle.BEFORE_INIT_EVENT.equals(event.getType())) {
              configure(connector, factory, settings);
            }
          });
      factory.addAdditionalConnectors(connector);
    };
  }

  @Bean
  public FilterRegistrationBean<ClusterApiPortFilter> clusterApiPortFilter(
      final Environment environment) {
    final var settings = ClusterApiSettings.load(environment);
    final var registration =
        new FilterRegistrationBean<>(new ClusterApiPortFilter(settings.requireDedicatedPort()));
    registration.addUrlPatterns("/*");
    registration.setOrder(CLUSTER_API_PORT_FILTER_ORDER);
    return registration;
  }

  /**
   * Applies Spring Boot's own main-connector configuration to the dedicated connector, then
   * restores its port and address. Reusing {@code TomcatWebServerFactory#customizeConnector} (a
   * protected hook, stable across Boot versions) rather than mirroring it keeps the two connectors
   * from drifting apart as Boot adds settings: TLS including SNI bundles and certificate reload,
   * HTTP/2, compression, URI encoding, and every connector and protocol handler customizer.
   */
  private static void configure(
      final Connector connector,
      final TomcatServletWebServerFactory factory,
      final ClusterApiSettings settings) {
    ReflectionUtils.invokeMethod(CUSTOMIZE_CONNECTOR, factory, connector);

    connector.setPort(settings.requireDedicatedPort());
    // load() resolved the address, so Tomcat can only refuse it for a reason it would otherwise
    // hide by keeping the wildcard address it already has.
    if (settings.address() != null && !connector.setProperty("address", settings.address())) {
      throw new IllegalStateException(
          "Cannot bind the dedicated cluster API port to %s".formatted(settings.address()));
    }

    final Ssl ssl = factory.getSsl();
    LOG.info(
        "Serving {} on dedicated port {} ({})",
        ClusterApiSettings.CLUSTER_API_PATTERN,
        settings.requireDedicatedPort(),
        ssl != null && Ssl.isEnabled(ssl) ? "https" : "http");
  }

  /**
   * Refuses to open the dedicated port unless a cluster-admin security chain exists. The chains are
   * registered only in the consolidated authentication setup; without one, the port would serve the
   * cluster-wide admin API with no authentication at all, and an explicitly configured
   * cluster-admin authentication method would be silently ignored.
   */
  private static void requireClusterAdminChain(final ListableBeanFactory beanFactory) {
    if (ClusterApiSettings.CHAIN_BEANS.stream().noneMatch(beanFactory::containsBeanDefinition)) {
      throw new IllegalStateException(
          "%s is set, but no cluster-admin security chain is configured for this application; "
                  .formatted(ClusterApiSettings.PORT_PROPERTY)
              + "the dedicated cluster API port requires the consolidated authentication setup");
    }
  }

  private static Method findCustomizeConnector() {
    final Method method =
        ReflectionUtils.findMethod(
            TomcatServletWebServerFactory.class, "customizeConnector", Connector.class);
    if (method == null) {
      throw new IllegalStateException(
          "TomcatServletWebServerFactory#customizeConnector(Connector) not found; the dedicated"
              + " cluster API connector cannot inherit the server settings");
    }
    ReflectionUtils.makeAccessible(method);
    return method;
  }
}
