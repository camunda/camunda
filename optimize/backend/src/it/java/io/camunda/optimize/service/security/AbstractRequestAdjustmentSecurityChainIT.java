/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CCSM_PROFILE;

import io.camunda.optimize.AbstractIT;
import io.camunda.optimize.CcsmOidcTestFixture;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import io.camunda.optimize.tomcat.OptimizeResourceConstants;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots Optimize with CSL pinned on (see {@link CslDefaultEnabledIT} for why this restart pattern
 * is used instead of {@code @TestPropertySource}) and exposes raw, unauthenticated HTTP access to
 * arbitrary paths. The test request executor is deliberately not used: it prefixes {@code /api} and
 * authenticates, whereas these tests must observe which security chain a rewritten or
 * context-path-prefixed request lands on.
 */
@Tag("ccsm-test")
@ActiveProfiles(CCSM_PROFILE)
abstract class AbstractRequestAdjustmentSecurityChainIT extends AbstractIT {

  private Client client;
  // The bean the context path is set on. Restarting Optimize swaps the extension's context, so the
  // reset must target this instance, not whatever the extension returns after the restart.
  private ConfigurationService configurationServiceBeforeRestart;
  private String originalContextPath;

  /** The servlet context path to boot with; empty for none. */
  protected String contextPath() {
    return "";
  }

  @Override
  protected void startAndUseNewOptimizeInstance() {
    final Map<String, String> properties =
        new HashMap<>(CcsmOidcTestFixture.STATIC_OIDC_PROPERTIES);
    properties.put("optimize.security.csl.enabled", "true");
    startAndUseNewOptimizeInstance(properties, CCSM_PROFILE);
  }

  @BeforeEach
  void bootWithCslEnabled() {
    configurationServiceBeforeRestart = embeddedOptimizeExtension.getConfigurationService();
    originalContextPath = configurationServiceBeforeRestart.getContextPath().orElse(null);
    if (!contextPath().isEmpty()) {
      configurationServiceBeforeRestart.setContextPath(contextPath());
    }
    startAndUseNewOptimizeInstance();
    client = ClientBuilder.newClient();
    client.property(
        ClientProperties.CONNECT_TIMEOUT, IntegrationTestConfigurationUtil.getHttpTimeoutMillis());
    client.property(
        ClientProperties.READ_TIMEOUT, IntegrationTestConfigurationUtil.getHttpTimeoutMillis());
    client.property(ClientProperties.FOLLOW_REDIRECTS, Boolean.FALSE);
  }

  @AfterEach
  void closeClient() {
    if (configurationServiceBeforeRestart != null) {
      configurationServiceBeforeRestart.setContextPath(originalContextPath);
    }
    if (client != null) {
      client.close();
    }
  }

  /** GET the actuator health endpoint, which is served on the management port. */
  protected Response getActuatorHealth() {
    return client
        .target(
            "http://localhost:"
                + OptimizeResourceConstants.ACTUATOR_PORT
                + OptimizeResourceConstants.ACTUATOR_ENDPOINT
                + "/health")
        .request()
        .get();
  }

  /** GET relative to the servlet context path, without following redirects. */
  protected Response get(final String path) {
    return getAbsolute(contextPath() + path);
  }

  /** GET relative to the server root, ignoring the servlet context path. */
  protected Response getAbsolute(final String path) {
    final String endpointWithContextPath =
        IntegrationTestConfigurationUtil.getEmbeddedOptimizeEndpoint(
            embeddedOptimizeExtension.getApplicationContext());
    final String serverRoot =
        endpointWithContextPath.substring(
            0, endpointWithContextPath.length() - contextPath().length());
    return client.target(serverRoot + path).request().get();
  }
}
