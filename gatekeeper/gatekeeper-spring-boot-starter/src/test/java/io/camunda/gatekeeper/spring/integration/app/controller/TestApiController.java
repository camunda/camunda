/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.integration.app.controller;

import io.camunda.gatekeeper.model.identity.CamundaUserInfo;
import io.camunda.gatekeeper.spi.CamundaAuthenticationProvider;
import io.camunda.gatekeeper.spi.CamundaUserProvider;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A test REST controller that exercises gatekeeper's authentication pipeline. Provides endpoints
 * under protected ({@code /v2/**}) and unprotected ({@code /v2/license}) API paths to verify filter
 * chain behavior.
 */
@RestController
public final class TestApiController {

  private final CamundaAuthenticationProvider authProvider;
  private final CamundaUserProvider userProvider;

  public TestApiController(
      final CamundaAuthenticationProvider authProvider, final CamundaUserProvider userProvider) {
    this.authProvider = authProvider;
    this.userProvider = userProvider;
  }

  /** Protected endpoint — requires authentication. Returns the resolved identity context. */
  @GetMapping("/v2/test/identity")
  public Map<String, Object> identity() {
    final var auth = authProvider.getCamundaAuthentication();
    return Map.of(
        "username", auth.authenticatedUsername() != null ? auth.authenticatedUsername() : "",
        "groups", auth.authenticatedGroupIds(),
        "tenants", auth.authenticatedTenantIds(),
        "roles", auth.authenticatedRoleIds(),
        "anonymous", auth.isAnonymous());
  }

  /** Protected endpoint — returns the full user info assembled by the CamundaUserProvider. */
  @GetMapping("/v2/test/user")
  public CamundaUserInfo currentUser() {
    return userProvider.getCurrentUser();
  }

  /** Unprotected API endpoint — accessible without authentication. */
  @GetMapping("/v2/license")
  public Map<String, String> license() {
    return Map.of("type", "test-license");
  }

  /** Protected webapp endpoint — requires session-based authentication (OIDC login flow). */
  @GetMapping("/app/test/identity")
  public Map<String, Object> webappIdentity() {
    final var auth = authProvider.getCamundaAuthentication();
    return Map.of(
        "username", auth.authenticatedUsername() != null ? auth.authenticatedUsername() : "",
        "groups", auth.authenticatedGroupIds(),
        "tenants", auth.authenticatedTenantIds(),
        "roles", auth.authenticatedRoleIds(),
        "anonymous", auth.isAnonymous());
  }

  /** Unprotected health endpoint — matches the actuator unprotected path. */
  @GetMapping("/actuator/health")
  public Map<String, String> health() {
    return Map.of("status", "UP");
  }
}
