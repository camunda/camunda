/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SecurityPathAdapterTest {

  // No webapp-enabled property set, so the adapter resolves CSL's default (enabled) and the
  // webappPaths() assertion below pins the enabled set.
  private final SecurityPathAdapter port =
      SecurityPathAdapter.fromEnvironment(new MockEnvironment());

  @Test
  void shouldExposeApiPaths() {
    // Tenant-prefixed paths are intentionally absent — per-tenant scoped chains own them; listing
    // them here would let the cluster chain shadow a scoped chain and break audience isolation.
    assertThat(port.apiPaths())
        .containsExactlyInAnyOrder(
            "/api/**", "/v1/**", "/v2/**", "/mcp/**", "/.well-known/oauth-protected-resource/**");
  }

  @Test
  void shouldExposeUnprotectedApiPaths() {
    assertThat(port.unprotectedApiPaths())
        .containsExactlyInAnyOrder(
            "/v2/license",
            "/v2/setup/user",
            "/v2/status",
            "/v1/external/process/**",
            "/.well-known/oauth-protected-resource/**");
  }

  @Test
  void shouldExposeUnprotectedPaths() {
    // /cluster/v2/status is here rather than in unprotectedApiPaths() on purpose: it must be served
    // by a chain with no authentication filter, so a credential the cluster-admin chain would
    // reject
    // is ignored instead of turning a health check into a 401.
    assertThat(port.unprotectedPaths())
        .containsExactlyInAnyOrder(
            "/error",
            "/actuator/**",
            "/ready",
            "/health",
            "/startup",
            "/favicon.ico",
            "/cluster/v2/status");
  }

  @Test
  void shouldExposeWebappPaths() {
    assertThat(port.webappPaths())
        .containsExactlyInAnyOrder(
            "/login/**",
            "/logout",
            "/admin/**",
            "/operate/**",
            "/tasklist/**",
            "/assets/**",
            "/custom.css",
            "/favicon.ico",
            "/",
            "/sso-callback/**",
            "/oauth2/authorization/**",
            "/post-logout",
            "/processes",
            "/processes/*",
            "/{regex:[\\d]+}",
            "/processes/*/start",
            "/new/*",
            "/decisions",
            "/decisions/*",
            "/instances",
            "/instances/*",
            "/default-ui.css",
            "/swagger/**",
            "/swagger-ui/**",
            "/v3/api-docs/**");
  }

  @Test
  void shouldExposeWebComponentNames() {
    assertThat(port.webComponentNames()).containsExactlyInAnyOrder("admin", "operate", "tasklist");
  }

  @Test
  void shouldExposeUnauthenticatedWebappPaths() {
    assertThat(port.unauthenticatedWebappPaths())
        .containsExactlyInAnyOrder(
            "/post-logout",
            "/default-ui.css",
            "/operate/assets/**",
            "/operate/client-config.js",
            "/operate/custom.css",
            "/operate/favicon.ico",
            "/admin/assets/**",
            "/admin/favicon.ico",
            "/assets/**",
            "/custom.css",
            "/favicon.ico",
            "/swagger/**",
            "/swagger-ui/**",
            "/v3/api-docs/**");
  }

  @Test
  void shouldExposeAdminFilterBypassPaths() {
    assertThat(port.adminFilterBypassPaths())
        .containsExactlyInAnyOrder(
            "/login", "/logout", "/sso-callback", "/post-logout", "/admin/setup", "/admin/assets");
  }

  @Test
  void shouldReportNoWebappPathsWhenWebappDisabled() {
    // given
    final var environment =
        new MockEnvironment()
            .withProperty("camunda.security.authentication.webapp-enabled", "false");

    // when
    final var disabledPort = SecurityPathAdapter.fromEnvironment(environment);

    // then
    assertThat(disabledPort.webappPaths()).isEmpty();
  }
}
