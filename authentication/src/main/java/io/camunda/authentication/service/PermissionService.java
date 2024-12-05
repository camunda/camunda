/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.authentication.entity.CamundaUser;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.impl.AuthorizationChecker;
import org.springframework.security.core.context.SecurityContextHolder;

public class PermissionService {

  private final AuthorizationChecker authorizationChecker;
  private final SecurityConfiguration securityConfiguration;

  public PermissionService(
      final SecurityConfiguration securityConfiguration,
      final AuthorizationChecker authorizationChecker) {
    this.authorizationChecker = authorizationChecker;
    this.securityConfiguration = securityConfiguration;
  }

  public boolean hasPermissionToAccessApplication(final String application) {
    if (!isAuthorizationEnabled()) {
      return true;
    }

    final var authorization = Authorization.of(a -> a.application().access());
    return authorizationChecker.isAuthorized(
        application, new SecurityContext(getAuthentication(), authorization));
  }

  private boolean isAuthorizationEnabled() {
    return securityConfiguration.getAuthorizations().isEnabled();
  }

  private Long getAuthenticatedUserKey() {
    final var requestAuthentication = SecurityContextHolder.getContext().getAuthentication();
    if (requestAuthentication != null) {
      final Object principal = requestAuthentication.getPrincipal();
      if (principal instanceof final CamundaUser authenticatedPrincipal) {
        return authenticatedPrincipal.getUserKey();
      }
    }
    return null;
  }

  private Authentication getAuthentication() {
    final Long authenticatedUserKey = getAuthenticatedUserKey();
    // groups and roles will come later
    return new Authentication.Builder().user(authenticatedUserKey).build();
  }
}
