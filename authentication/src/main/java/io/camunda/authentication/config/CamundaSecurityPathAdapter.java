/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.core.adapter.SecurityPathAdapter;
import java.util.Set;

/**
 * {@link SecurityPathAdapter} implementation declaring the camunda-monorepo-specific HTTP path
 * patterns the central security filter chains operate on.
 */
public final class CamundaSecurityPathAdapter implements SecurityPathAdapter {

  private static final Set<String> API_PATHS =
      Set.of("/api/**", "/v1/**", "/v2/**", "/mcp/**", "/.well-known/oauth-protected-resource/**");

  private static final Set<String> UNPROTECTED_API_PATHS =
      Set.of(
          // these v2 endpoints are public
          "/v2/license",
          "/v2/setup/user",
          "/v2/status",
          // deprecated Tasklist v1 Public Endpoints
          "/v1/external/process/**",
          // OAuth2 Protected Resource Metadata endpoint (RFC 9728)
          "/.well-known/oauth-protected-resource/**");

  private static final Set<String> UNPROTECTED_PATHS =
      Set.of(
          // endpoint for failure forwarding
          "/error",
          // all actuator endpoints
          "/actuator/**",
          // endpoints defined in BrokerHealthRoutes
          "/ready",
          "/health",
          "/startup",
          // post logout decision endpoint
          "/post-logout",
          // swagger-ui endpoint
          "/swagger/**",
          "/swagger-ui/**",
          "/v3/api-docs/**",
          "/v2/rest-api.yaml",
          // deprecated Tasklist v1 Public Endpoints
          "/new/**",
          "/tasklist/new/**",
          "/favicon.ico");

  private static final String SPRING_DEFAULT_UI_CSS = "/default-ui.css";

  private static final Set<String> WEBAPP_PATHS =
      Set.of(
          "/login/**",
          "/logout",
          "/identity/**",
          "/admin/**",
          "/operate/**",
          "/tasklist/**",
          "/",
          "/sso-callback/**",
          "/oauth2/authorization/**",
          // old Tasklist and Operate webapps routes
          "/processes",
          "/processes/*",
          "/{regex:[\\d]+}", // user task id
          "/processes/*/start",
          "/new/*",
          "/decisions",
          "/decisions/*",
          "/instances",
          "/instances/*",
          SPRING_DEFAULT_UI_CSS);

  private static final Set<String> UNAUTHENTICATED_WEBAPP_PATHS =
      Set.of(
          SPRING_DEFAULT_UI_CSS,
          "/tasklist/assets/**",
          "/tasklist/client-config.js",
          "/tasklist/custom.css",
          "/tasklist/favicon.ico");

  private static final Set<String> WEB_COMPONENT_NAMES =
      Set.of("identity", "admin", "operate", "tasklist");

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
  public Set<String> unauthenticatedWebappPaths() {
    return UNAUTHENTICATED_WEBAPP_PATHS;
  }

  @Override
  public Set<String> webComponentNames() {
    return WEB_COMPONENT_NAMES;
  }
}
