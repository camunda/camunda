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
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
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
    final ConfigurationService configurationService =
        embeddedOptimizeExtension.getConfigurationService();
    originalContextPath = configurationService.getContextPath().orElse(null);
    if (!contextPath().isEmpty()) {
      configurationService.setContextPath(contextPath());
    }
    startAndUseNewOptimizeInstance();
    client = ClientBuilder.newClient();
  }

  @AfterEach
  void closeClient() {
    client.close();
    embeddedOptimizeExtension.getConfigurationService().setContextPath(originalContextPath);
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
    return client
        .target(serverRoot + path)
        .property("jersey.config.client.followRedirects", false)
        .request()
        .get();
  }
}
