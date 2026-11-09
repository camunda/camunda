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
import java.net.URI;
import org.junit.jupiter.api.Test;

/**
 * The CCSaaS shape of {@link RequestAdjustmentSecurityChainIT}: in CSL mode the cluster id is the
 * servlet context path, so every request arrives as {@code /<clusterId>/...}. The legacy SaaS
 * filter that stripped the cluster id itself only runs with {@code
 * optimize.security.csl.enabled=false} and is not covered here.
 */
public class RequestAdjustmentContextPathSecurityChainIT
    extends AbstractRequestAdjustmentSecurityChainIT {

  private static final String CLUSTER_ID_CONTEXT_PATH = "/test-cluster-id";

  @Override
  protected String contextPath() {
    return CLUSTER_ID_CONTEXT_PATH;
  }

  @Test
  void shouldServeUiConfigurationViaExternalApiPrefixWhenAppRunsUnderContextPath() {
    // given the app runs under /<clusterId> and the client is unauthenticated
    // when it requests /<clusterId>/external/api/ui-configuration
    final Response response = get("/external/api/ui-configuration");

    // then the public chain serves it
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  void shouldReturn401ForProtectedEndpointWhenAppRunsUnderContextPath() {
    // given the app runs under /<clusterId> and the client is unauthenticated
    // when it requests /<clusterId>/api/report
    final Response response = get("/api/report");

    // then it gets 401
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  void shouldRedirectToOidcLoginForRootWhenAppRunsUnderContextPath() {
    // given the app runs under /<clusterId> and the browser is unauthenticated
    // when it requests /<clusterId>/
    final Response response = get("/");

    // then it is redirected to the OIDC authorization endpoint inside the context path, so a
    // redirect that drops the cluster id cannot pass
    assertThat(response.getStatus()).isEqualTo(Response.Status.FOUND.getStatusCode());
    assertThat(URI.create(response.getHeaderString("Location")).getPath())
        .startsWith(CLUSTER_ID_CONTEXT_PATH + "/oauth2/authorization/");
  }

  @Test
  void shouldServeReadyzUnderContextPath() {
    // given the app runs under /<clusterId>
    // when an unauthenticated probe requests /<clusterId>/api/readyz
    final Response response = get("/api/readyz");

    // then it is served
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  void shouldRedirectToHashRouteUnderContextPathWhenUnknownPublicPageIsRequested() {
    // given the app runs under /<clusterId> and the client is unauthenticated
    // when it requests a publicly permitted page that is not a server resource
    final Response response = get("/unknown-page.html");

    // then it is redirected to the hash route under the context path
    assertThat(response.getStatus()).isEqualTo(Response.Status.FOUND.getStatusCode());
    assertThat(response.getHeaderString("Location")).endsWith(CLUSTER_ID_CONTEXT_PATH + "/#");
  }

  @Test
  void shouldServeActuatorHealthWhenAppRunsUnderContextPath() {
    // given the app runs under /<clusterId>
    // when an unauthenticated client requests the actuator health endpoint
    final Response response = getActuatorHealth();

    // then it is served
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  void shouldServeReadyzAtServerRootWhenAppRunsUnderContextPath() {
    // given the app runs under /<clusterId>, but SaaS probes omit the prefix
    // when an unauthenticated probe requests /api/readyz at the server root
    final Response response = getAbsolute("/api/readyz");

    // then it is served
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }
}
