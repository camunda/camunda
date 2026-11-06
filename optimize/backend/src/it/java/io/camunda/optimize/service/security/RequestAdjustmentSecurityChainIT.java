/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

/**
 * Checks, over real HTTP and without a servlet context path, that the request rewriting in front of
 * the security chains and the chains themselves agree on the path. Regression guard for #47004,
 * where security matchers stopped seeing the rewritten path.
 */
public class RequestAdjustmentSecurityChainIT extends AbstractRequestAdjustmentSecurityChainIT {

  @Test
  void shouldServeUiConfigurationWhenRequestedViaExternalApiPrefix() {
    // given an unauthenticated client
    // when it requests /external/api/ui-configuration, which is rewritten to /api/external/...
    final Response response = get("/external/api/ui-configuration");

    // then the public chain serves it
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  void shouldServeLocalizationWhenRequestedViaExternalApiPrefix() {
    // given an unauthenticated client
    // when it requests /external/api/localization, which is rewritten to /api/external/...
    final Response response = get("/external/api/localization");

    // then the public chain serves it
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  void shouldReturn401WhenUnauthenticatedRequestHitsProtectedEndpoint() {
    // given an unauthenticated client
    // when it requests the protected /api/report
    final Response response = get("/api/report");

    // then it gets 401 instead of a redirect to the identity provider
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  void shouldRedirectToOidcLoginWhenUnauthenticatedRequestHitsRoot() {
    // given an unauthenticated browser
    // when it requests /, which Spring forwards to /index.html
    final Response response = get("/");

    // then it is redirected to the OIDC authorization endpoint
    assertThat(response.getStatus()).isEqualTo(Response.Status.FOUND.getStatusCode());
    assertThat(response.getHeaderString("Location")).contains("/oauth2/authorization/");
  }
}
