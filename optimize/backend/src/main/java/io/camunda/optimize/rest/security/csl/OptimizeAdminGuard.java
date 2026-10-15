/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** POC: lets only holders of the {@code admin} role administer Optimize access and tenants. */
@Component
@Conditional(CCSMCondition.class)
@ConditionalOnProperty(
    name = OptimizeAccessRoles.ACCESS_ROLES_ENABLED_PROPERTY,
    havingValue = "true")
public class OptimizeAdminGuard {

  private final CamundaAuthenticationProvider authenticationProvider;

  public OptimizeAdminGuard(final CamundaAuthenticationProvider authenticationProvider) {
    this.authenticationProvider = authenticationProvider;
  }

  public void requireAdmin() {
    final CamundaAuthentication authentication = authenticationProvider.getCamundaAuthentication();
    if (authentication == null || authentication.isAnonymous()) {
      throw new NotAuthorizedException("Not authenticated.");
    }
    if (!authentication.authenticatedRoleIds().contains(OptimizeAccessRoles.ADMIN_ROLE_ID)) {
      throw new ForbiddenException("Only Optimize administrators may manage access.");
    }
  }
}
