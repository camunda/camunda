/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.spi;

import io.camunda.security.core.port.out.SecurityPathPort;
import java.util.Set;

/**
 * Host-supplied {@link SecurityPathPort} declaring the path patterns OC's filter chains operate on.
 * These values were previously inlined in {@code WebSecurityConfig}; they are lifted here so the
 * central library-owned chains can consume them.
 */
public class SecurityPathAdapter implements SecurityPathPort {

  public static final SecurityPathAdapter INSTANCE = new SecurityPathAdapter();

  private static final Set<String> API_PATHS =
      Set.of(
          "/api/**",
          "/v1/**",
          "/v2/**",
          "/physical-tenants/{physicalTenantId}/v2/**",
          "/mcp/**",
          "/physical-tenants/{physicalTenantId}/mcp/**",
          "/.well-known/oauth-protected-resource/**");

  private static final Set<String> UNPROTECTED_API_PATHS =
      Set.of(
          "/v2/license",
          "/v2/setup/user",
          "/v2/status",
          "/v1/external/process/**",
          "/.well-known/oauth-protected-resource/**");

  private static final Set<String> UNPROTECTED_PATHS =
      Set.of(
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

  private static final Set<String> WEBAPP_PATHS =
      Set.of(
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

  private static final Set<String> UNAUTHENTICATED_WEBAPP_PATHS =
      Set.of(
          "/default-ui.css",
          "/tasklist/assets/**",
          "/tasklist/client-config.js",
          "/tasklist/custom.css",
          "/tasklist/favicon.ico",
          "/webapp/assets/**",
          "/webapp/custom.css",
          "/webapp/favicon.ico");

  private static final Set<String> WEB_COMPONENT_NAMES = Set.of("admin", "operate", "tasklist");

  private static final Set<String> ADMIN_FILTER_BYPASS_PATHS =
      Set.of(
          "/login",
          "/logout",
          "/sso-callback",
          "/post-logout",
          "/admin/setup",
          // Setup-page assets (CSS, JS modules) must load before any admin user is provisioned;
          // without this prefix the filter redirects asset requests to /admin/setup, the SPA gets
          // text/html back for every <script>/<link> tag, and the browser refuses to evaluate the
          // setup page with MIME-type errors. Mirrors the explicit ASSETS_PATH bypass the
          // pre-CSL host AdminUserCheckFilter carried.
          "/admin/assets");

  @Override
  public Set<String> apiPaths() {
    return API_PATHS;
  }

  @Override
  public Set<String> unprotectedApiPaths() {
    return UNPROTECTED_API_PATHS;
  }

  @Override
  public Set<String> unprotectedPaths() {
    return UNPROTECTED_PATHS;
  }

  @Override
  public Set<String> webappPaths() {
    return WEBAPP_PATHS;
  }

  @Override
  public Set<String> webComponentNames() {
    return WEB_COMPONENT_NAMES;
  }

  @Override
  public Set<String> unauthenticatedWebappPaths() {
    return UNAUTHENTICATED_WEBAPP_PATHS;
  }

  @Override
  public Set<String> adminFilterBypassPaths() {
    return ADMIN_FILTER_BYPASS_PATHS;
  }

  // staticResourceSuffixes() inherits the SPI default which already matches OC's source set.
}
