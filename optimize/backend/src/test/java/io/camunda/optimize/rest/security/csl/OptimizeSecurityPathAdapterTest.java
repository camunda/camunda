/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.ACTUATOR_ENDPOINT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.tomcat.OptimizeResourceConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OptimizeSecurityPathAdapterTest {

  // ACTUATOR_ENDPOINT is a mutable static bound from management.endpoints.web.base-path; capture
  // and restore it so a customized value in one test does not leak into others.
  private final String originalActuatorEndpoint = ACTUATOR_ENDPOINT;
  private final OptimizeSecurityPathAdapter pathAdapter = new OptimizeSecurityPathAdapter();

  @AfterEach
  void restoreActuatorEndpoint() {
    OptimizeResourceConstants.ACTUATOR_ENDPOINT = originalActuatorEndpoint;
  }

  @Test
  void shouldNotClaimTheAuthenticationPathsForTheApiChain() {
    // The CCSM OIDC callback is /api/authentication/callback and the API chain disables
    // oauth2Login, so claiming it here would stop the callback reaching the webapp chain and break
    // login. Guards against anyone widening this to /api/**.
    assertThat(pathAdapter.apiPaths())
        .noneSatisfy(path -> assertThat(path).startsWith("/api/authentication"))
        .doesNotContain("/api/**");
  }

  @Test
  void shouldClaimTheInternalApiSoBearerTokensWorkAlongsideTheSession() {
    assertThat(pathAdapter.apiPaths())
        .contains("/api/public/**", "/api/ingestion/variable")
        .contains("/api/dashboard/**", "/api/report/**", "/api/collection/**", "/api/entities/**");
  }

  @Test
  void shouldLeaveThePublicEndpointsToTheUnprotectedChain() {
    // These are matched by the order-0 unprotected chain, so listing them as API paths as well
    // would be misleading and would change which chain reports on them.
    assertThat(pathAdapter.apiPaths())
        .doesNotContain(
            "/api/readyz", "/api/ui-configuration", "/api/localization", "/api/external/**");
    assertThat(pathAdapter.unprotectedPaths())
        .contains("/api/readyz", "/api/ui-configuration", "/api/localization", "/api/external/**");
  }

  @Test
  void shouldUnprotectActuatorAtDefaultBasePath() {
    OptimizeResourceConstants.ACTUATOR_ENDPOINT = "/actuator";
    assertThat(pathAdapter.unprotectedPaths()).contains("/actuator/**");
  }

  @Test
  void shouldUnprotectActuatorAtCustomBasePath() {
    OptimizeResourceConstants.ACTUATOR_ENDPOINT = "/management";
    assertThat(pathAdapter.unprotectedPaths()).contains("/management/**");
  }
}
