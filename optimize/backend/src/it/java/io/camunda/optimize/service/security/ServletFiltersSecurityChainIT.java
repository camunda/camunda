/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import static io.camunda.optimize.rest.constants.RestConstants.CACHE_CONTROL_NO_STORE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.tomcat.OptimizeResourceConstants;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Observes the servlet filters in front of the security chains ({@code URLRedirectFilter}, {@code
 * NoCachingFilter}, {@code ResponseSecurityHeaderFilter}) and the actuator over real HTTP. Their
 * unit tests call the filters directly, so they cannot show how the filters interact with the
 * responses produced by the security chains.
 */
public class ServletFiltersSecurityChainIT extends AbstractRequestAdjustmentSecurityChainIT {

  @Test
  void shouldAddConfiguredSecurityHeadersWhenEndpointIsPublic() {
    // given the security headers are configured
    final Map<String, String> expectedHeaders =
        embeddedOptimizeExtension
            .getConfigurationService()
            .getSecurityConfiguration()
            .getResponseHeaders()
            .getHeadersWithValues();
    assertThat(expectedHeaders).isNotEmpty();

    // when an unauthenticated client requests a public endpoint
    final Response response = get("/api/readyz");

    // then every configured header is on the response
    expectedHeaders.forEach(
        (name, value) -> assertThat(response.getHeaderString(name)).as(name).isEqualTo(value));
  }

  @Test
  void shouldSetNoStoreCacheControlWhenApiRespondsSuccessfully() {
    // given an unauthenticated client
    // when it requests a public API endpoint
    final Response response = get("/api/readyz");

    // then the response must not be cached
    assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL))
        .isEqualTo(CACHE_CONTROL_NO_STORE);
  }

  @Test
  void shouldSetNoStoreCacheControlWhenApiRequestIsRejected() {
    // given an unauthenticated client
    // when it requests a protected API endpoint
    final Response response = get("/api/report");

    // then the 401 must not be cached either (Spring Security adds further directives)
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
    assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL))
        .contains(CACHE_CONTROL_NO_STORE);
  }

  @Test
  void shouldRedirectToHashRouteWhenUnknownSpaPathIsRequested() {
    // given an unauthenticated client
    // when it requests a client-side route that is not a server resource
    final Response response = get("/some/spa/route");

    // then it is redirected
    assertThat(response.getStatus()).isEqualTo(Response.Status.FOUND.getStatusCode());
    assertThat(response.getHeaderString(HttpHeaders.LOCATION)).isNotBlank();
  }

  @Test
  void shouldServeActuatorHealthWhenRequestIsUnauthenticated() {
    // given the actuator runs on the management port
    final String healthUrl =
        "http://localhost:"
            + OptimizeResourceConstants.ACTUATOR_PORT
            + OptimizeResourceConstants.ACTUATOR_ENDPOINT
            + "/health";

    // when an unauthenticated client requests it
    try (final Client client = ClientBuilder.newClient()) {
      final Response response = client.target(healthUrl).request().get();

      // then it is served
      assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    }
  }
}
