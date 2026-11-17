/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import io.camunda.authentication.clusteradmin.ClusterApiSettings;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Serves the cluster API ({@code /cluster/v2/**}) on a dedicated port when {@value
 * ClusterApiSettings#PORT_PROPERTY} is set.
 *
 * <p>The port is an additional connector on the same embedded Tomcat, so the cluster API keeps the
 * one application context, {@code DispatcherServlet} and security filter chain it has on the main
 * port; {@link ClusterApiPortFilter} decides which paths each port serves, and the cluster-admin
 * chains match on the port as well (see {@link ClusterApiSettings#clusterApiMatcher}). The
 * connector gets its own request thread pool, so a saturated main port does not starve cluster
 * operations.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ClusterApiSettings.PORT_PROPERTY)
public class ClusterApiServerConfiguration {

  // Before PhysicalTenantFilter (-101) and Spring Security (-100): a request on the wrong port is
  // rejected before any tenant resolution or authentication runs.
  private static final int CLUSTER_API_PORT_FILTER_ORDER = -102;

  private static final Logger LOG = LoggerFactory.getLogger(ClusterApiServerConfiguration.class);

  @Bean
  public WebServerFactoryCustomizer<TomcatServletWebServerFactory> clusterApiConnectorCustomizer(
      final Environment environment) {
    final int port = clusterPort(environment);
    final String address = environment.getProperty(ClusterApiSettings.ADDRESS_PROPERTY);
    return factory -> {
      final Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
      connector.setPort(port);
      if (address != null && !address.isBlank()) {
        connector.setProperty("address", address);
      }
      // Spring Boot's connector customizers only reach the main connector; mirror the settings
      // the REST API depends on (see TomcatEncodedSlashConfig).
      connector.setEncodedSolidusHandling(EncodedSolidusHandling.DECODE.getValue());
      factory.addAdditionalConnectors(connector);
      LOG.info("Serving {} on dedicated port {}", ClusterApiSettings.CLUSTER_API_PATTERN, port);
    };
  }

  @Bean
  public FilterRegistrationBean<ClusterApiPortFilter> clusterApiPortFilter(
      final Environment environment) {
    final var registration =
        new FilterRegistrationBean<>(new ClusterApiPortFilter(clusterPort(environment)));
    registration.addUrlPatterns("/*");
    registration.setOrder(CLUSTER_API_PORT_FILTER_ORDER);
    return registration;
  }

  private static int clusterPort(final Environment environment) {
    return ClusterApiSettings.dedicatedPort(environment)
        .orElseThrow(() -> new IllegalStateException("cluster API port is not configured"));
  }
}
