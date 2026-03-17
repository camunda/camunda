/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.adapter;

import io.camunda.gatekeeper.spi.SecurityPathProvider;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Default security path provider for Camunda applications. Defines the URL patterns for API
 * endpoints, unprotected paths, web application UI, and web component names. These paths match the
 * original WebSecurityConfig constants.
 */
@Component
public final class DefaultSecurityPathProvider implements SecurityPathProvider {

  @Override
  public Set<String> apiPaths() {
    return Set.of("/api/**", "/v1/**", "/v2/**", "/mcp/**");
  }

  @Override
  public Set<String> unprotectedApiPaths() {
    return Set.of("/v2/license", "/v2/setup/user", "/v2/status", "/v1/external/process/**");
  }

  @Override
  public Set<String> unprotectedPaths() {
    return Set.of(
        "/error",
        "/actuator/**",
        "/ready",
        "/health",
        "/startup",
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/rest-api.yaml",
        "/new/**",
        "/tasklist/new/**",
        "/favicon.ico");
  }

  @Override
  public Set<String> webappPaths() {
    return Set.of(
        "/login/**",
        "/logout",
        "/identity/**",
        "/admin/**",
        "/operate/**",
        "/tasklist/**",
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

  @Override
  public Set<String> webComponentNames() {
    return Set.of("identity", "admin", "operate", "tasklist");
  }
}
