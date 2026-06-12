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

class SecurityPathAdapterTest {

  private final SecurityPathAdapter port = new SecurityPathAdapter();

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
    assertThat(port.unprotectedPaths())
        .containsExactlyInAnyOrder(
            "/error",
            "/actuator/**",
            "/ready",
            "/health",
            "/startup",
            "/post-logout",
            "/swagger/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/v2/rest-api.yaml",
            "/new/**",
            "/tasklist/new/**",
            "/favicon.ico");
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
            "/webapp/**",
            "/",
            "/sso-callback/**",
            "/oauth2/authorization/**",
            "/processes",
            "/processes/*",
            "/{regex:[\\d]+}",
            "/processes/*/start",
            "/new/*",
            "/decisions",
            "/decisions/*",
            "/instances",
            "/instances/*",
            "/default-ui.css");
  }

  @Test
  void shouldExposeWebComponentNames() {
    assertThat(port.webComponentNames()).containsExactlyInAnyOrder("admin", "operate", "tasklist");
  }

  @Test
  void shouldExposeUnauthenticatedWebappPaths() {
    assertThat(port.unauthenticatedWebappPaths())
        .containsExactlyInAnyOrder(
            "/default-ui.css",
            "/tasklist/assets/**",
            "/tasklist/client-config.js",
            "/tasklist/custom.css",
            "/tasklist/favicon.ico",
            "/webapp/assets/**",
            "/webapp/custom.css",
            "/webapp/favicon.ico");
  }

  @Test
  void shouldExposeAdminFilterBypassPaths() {
    assertThat(port.adminFilterBypassPaths())
        .containsExactlyInAnyOrder(
            "/login", "/logout", "/sso-callback", "/post-logout", "/admin/setup", "/admin/assets");
  }
}
