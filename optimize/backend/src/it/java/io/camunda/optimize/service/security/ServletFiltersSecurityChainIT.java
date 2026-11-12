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

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

/**
 * Observes the servlet filters in front of the security chains ({@code URLRedirectFilter}, {@code
 * NoCachingFilter}) and the actuator over real HTTP. Their unit tests call the filters directly, so
 * they cannot show how the filters interact with the responses produced by the security chains.
 * Security header values are deliberately not asserted: under CSL the security chains own them.
 */
public class ServletFiltersSecurityChainIT extends AbstractRequestAdjustmentSecurityChainIT {

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
  void shouldRedirectToHashRouteWhenUnknownPublicPageIsRequested() {
    // given an unauthenticated client
    // when it requests a publicly permitted page that is not a server resource
    final Response response = get("/unknown-page.html");

    // then it is redirected to the hash route of the app root
    assertThat(response.getStatus()).isEqualTo(Response.Status.FOUND.getStatusCode());
    assertThat(response.getHeaderString(HttpHeaders.LOCATION)).endsWith("/#");
  }

  @Test
  void shouldServeActuatorHealthWhenRequestIsUnauthenticated() {
    // given the actuator runs on the management port
    // when an unauthenticated client requests its health endpoint
    final Response response = getActuatorHealth();

    // then it is served
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }
}
