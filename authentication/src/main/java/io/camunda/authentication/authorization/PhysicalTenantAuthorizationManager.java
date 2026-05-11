/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.authorization;

import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * URL-shape authorization rule for the {@code /v2/physical-tenants/{ptId}/**} endpoint family.
 * Grants only when the request's authenticated principal carries the {@code PT_<ptId>} authority
 * matching the {@code ptId} path variable.
 *
 * <p>Fails closed: missing variable, missing authentication, or absent authority all deny. HTTP
 * basic-auth principals never receive {@code PT_*} authorities, so they are always denied — this is
 * the explicit non-goal pinned by {@code OidcApiSecurityPhysicalTenantTest}.
 */
public class PhysicalTenantAuthorizationManager
    implements AuthorizationManager<RequestAuthorizationContext> {

  static final String PATH_VARIABLE = "ptId";
  static final String AUTHORITY_PREFIX = "PT_";

  @Override
  public AuthorizationDecision authorize(
      final Supplier<? extends Authentication> authentication,
      final RequestAuthorizationContext context) {
    final String ptId = context.getVariables().get(PATH_VARIABLE);
    if (ptId == null || ptId.isBlank()) {
      return new AuthorizationDecision(false);
    }
    final Authentication auth = authentication.get();
    if (auth == null || !auth.isAuthenticated() || auth.getAuthorities() == null) {
      return new AuthorizationDecision(false);
    }
    final String required = AUTHORITY_PREFIX + ptId;
    final boolean granted =
        auth.getAuthorities().stream()
            .anyMatch(authority -> required.equals(authority.getAuthority()));
    return new AuthorizationDecision(granted);
  }
}
