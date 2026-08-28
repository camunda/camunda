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

  private static final String DEFAULT_TENANT_PREFIX = "/physical-tenants/default";

  // Neither sets webapp-enabled, so both get CSL's default (enabled). The only difference is
  // whether a physical tenant is configured — the gate on the prefixed paths.
  private final SecurityPathAdapter defaultTenantPort =
      SecurityPathAdapter.fromEnvironment(new MockEnvironment());

  private final SecurityPathAdapter clusterOnlyPort =
      SecurityPathAdapter.fromEnvironment(
          new MockEnvironment()
              .withProperty(
                  "camunda.physical-tenants.tenanta.security.authentication.method", "oidc"));

  @Test
  void shouldExposeApiPathsWithoutDefaultTenantPrefixWhenTenantConfigured() {
    // A scoped chain owns /physical-tenants/<id>/**, deriving its matchers as basePath + these.
    assertThat(clusterOnlyPort.apiPaths())
        .containsExactlyInAnyOrder(
            "/api/**", "/v1/**", "/v2/**", "/mcp/**", "/.well-known/oauth-protected-resource/**");
  }

  @Test
  void shouldAddDefaultTenantPrefixToApiPathsWhenNoTenantConfigured() {
    // No scoped chain exists to serve /physical-tenants/default, so the cluster chain carries it.
    assertThat(defaultTenantPort.apiPaths())
        .containsExactlyInAnyOrder(
            "/api/**",
            "/v1/**",
            "/v2/**",
            "/mcp/**",
            "/.well-known/oauth-protected-resource/**",
            DEFAULT_TENANT_PREFIX + "/api/**",
            DEFAULT_TENANT_PREFIX + "/v1/**",
            DEFAULT_TENANT_PREFIX + "/v2/**",
            DEFAULT_TENANT_PREFIX + "/mcp/**",
            DEFAULT_TENANT_PREFIX + "/.well-known/oauth-protected-resource/**");
  }

  @Test
  void shouldExposeUnprotectedApiPathsWithoutDefaultTenantPrefixWhenTenantConfigured() {
    assertThat(clusterOnlyPort.unprotectedApiPaths())
        .containsExactlyInAnyOrder(
            "/v2/license",
            "/v2/setup/user",
            "/v2/status",
            "/v1/external/process/**",
            "/.well-known/oauth-protected-resource/**");
  }

  @Test
  void shouldAddDefaultTenantPrefixToUnprotectedApiPathsWhenNoTenantConfigured() {
    // Parity for the prefixed unprotected surface: /physical-tenants/default/v2/status must be
    // reachable unauthenticated exactly as /v2/status is, which PhysicalTenantStatusScopeFilter
    // deliberately admits for the default tenant.
    assertThat(defaultTenantPort.unprotectedApiPaths())
        .containsExactlyInAnyOrder(
            "/v2/license",
            "/v2/setup/user",
            "/v2/status",
            "/v1/external/process/**",
            "/.well-known/oauth-protected-resource/**",
            DEFAULT_TENANT_PREFIX + "/v2/license",
            DEFAULT_TENANT_PREFIX + "/v2/setup/user",
            DEFAULT_TENANT_PREFIX + "/v2/status",
            DEFAULT_TENANT_PREFIX + "/v1/external/process/**",
            DEFAULT_TENANT_PREFIX + "/.well-known/oauth-protected-resource/**");
  }

  @Test
  void shouldExposeUnprotectedPaths() {
    // /cluster/v2/status is here rather than in unprotectedApiPaths() on purpose: it must be served
    // by a chain with no authentication filter, so a credential the cluster-admin chain would
    // reject is ignored instead of turning a health check into a 401.
    assertThat(defaultTenantPort.unprotectedPaths())
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
  void shouldExposeWebappPathsWithoutDefaultTenantPrefixWhenTenantConfigured() {
    assertThat(clusterOnlyPort.webappPaths())
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
  void shouldAddDefaultTenantPrefixToEveryWebappPathWhenNoTenantConfigured() {
    final var base = clusterOnlyPort.webappPaths();

    assertThat(defaultTenantPort.webappPaths())
        .hasSize(base.size() * 2)
        .containsAll(base)
        .contains(
            DEFAULT_TENANT_PREFIX + "/login/**",
            DEFAULT_TENANT_PREFIX + "/operate/**",
            DEFAULT_TENANT_PREFIX + "/v3/api-docs/**");
  }

  @Test
  void shouldPrefixWebappRootToATrailingSlashPattern() {
    // "/" prefixes to /physical-tenants/default/ — matching that root only WITH a trailing
    // slash, never without. Mirrors what CSL's scoped webapp chain already produces, so this pins
    // existing behaviour rather than new. The matching consequence is characterised at chain level.
    assertThat(defaultTenantPort.webappPaths()).contains(DEFAULT_TENANT_PREFIX + "/");
  }

  @Test
  void shouldPrefixRegexWebappPattern() {
    // Regex patterns survive prefixing; only "/" is special.
    assertThat(defaultTenantPort.webappPaths()).contains(DEFAULT_TENANT_PREFIX + "/{regex:[\\d]+}");
  }

  @Test
  void shouldExposeWebComponentNames() {
    assertThat(defaultTenantPort.webComponentNames())
        .containsExactlyInAnyOrder("admin", "operate", "tasklist");
  }

  @Test
  void shouldExposeUnauthenticatedWebappPathsWithoutDefaultTenantPrefixWhenTenantConfigured() {
    assertThat(clusterOnlyPort.unauthenticatedWebappPaths())
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
  void shouldAddDefaultTenantPrefixToEveryUnauthenticatedWebappPathWhenNoTenantConfigured() {
    final var base = clusterOnlyPort.unauthenticatedWebappPaths();

    assertThat(defaultTenantPort.unauthenticatedWebappPaths())
        .hasSize(base.size() * 2)
        .containsAll(base)
        .contains(DEFAULT_TENANT_PREFIX + "/assets/**", DEFAULT_TENANT_PREFIX + "/post-logout");
  }

  @Test
  void shouldExposeAdminFilterBypassPaths() {
    assertThat(defaultTenantPort.adminFilterBypassPaths())
        .containsExactlyInAnyOrder(
            "/login", "/logout", "/sso-callback", "/post-logout", "/admin/setup", "/admin/assets");
  }

  @Test
  void shouldReportNoWebappPathsWhenWebappDisabledEvenWithoutPhysicalTenants() {
    // given
    // No physical tenant configured, so the prefixed variants would otherwise apply — the
    // webapp gate must win over them.
    final var environment =
        new MockEnvironment()
            .withProperty("camunda.security.authentication.webapp-enabled", "false");

    // when
    final var disabledPort = SecurityPathAdapter.fromEnvironment(environment);

    // then
    assertThat(disabledPort.webappPaths()).isEmpty();
  }
}
