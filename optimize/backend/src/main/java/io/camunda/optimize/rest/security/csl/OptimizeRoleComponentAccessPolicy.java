/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.Either;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * POC: replaces the Identity SDK's {@code write:*} check. A user may use Optimize when Optimize's
 * own stores give them at least one role, the way a user who matches no mapping rule gets no access
 * in the Hub POC.
 */
@Component
@Conditional(CCSMCondition.class)
@ConditionalOnProperty(
    name = OptimizeAccessRoles.ACCESS_ROLES_ENABLED_PROPERTY,
    havingValue = "true")
public class OptimizeRoleComponentAccessPolicy implements OptimizeComponentAccessPolicy {

  @Override
  public Either<String, Void> checkAccess(final CamundaAuthentication authentication) {
    final List<String> roleIds = authentication.authenticatedRoleIds();
    if (roleIds == null || roleIds.isEmpty()) {
      return Either.left("User holds no Optimize role");
    }
    return Either.right(null);
  }
}
