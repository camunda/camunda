/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.Either;
import io.camunda.security.api.model.authz.AuthorizationRejection;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers CSL's component access check from the Optimize access policy. Optimize stores no
 * authorizations of its own, so every other requirement is granted: the permissions Optimize
 * enforces on its data are resolved by its own services, not through this port.
 */
public final class OptimizeComponentAuthorizationAdapter implements AuthorizationCheckPort {

  private static final Logger LOG =
      LoggerFactory.getLogger(OptimizeComponentAuthorizationAdapter.class);

  private final OptimizeComponentAccessPolicy policy;

  public OptimizeComponentAuthorizationAdapter(final OptimizeComponentAccessPolicy policy) {
    this.policy = policy;
  }

  @Override
  public <T> Either<AuthorizationRejection, Void> check(
      final CamundaAuthentication authentication, final RequiredAuthorization<T> authorization) {
    if (authorization.resourceType() != AuthorizationResourceType.COMPONENT
        || authorization.permissionType() != PermissionType.ACCESS) {
      return Either.right(null);
    }
    final Either<String, Void> access = policy.checkAccess(authentication);
    if (access.isRight()) {
      return Either.right(null);
    }
    // The rejection carries the required permission, not the reason, so log it here or it is lost.
    LOG.debug("Denying access to the Optimize component: {}", access.leftValue());
    return Either.left(
        new AuthorizationRejection.Permission(
            authorization.resourceType(),
            authorization.permissionType(),
            resourceId(authorization)));
  }

  @Override
  public <T> Either<AuthorizationRejection, Void> check(
      final Map<String, Object> claims, final RequiredAuthorization<T> authorization) {
    return check(CamundaAuthentication.of(builder -> builder.claims(claims)), authorization);
  }

  @Override
  public <T> Either<AuthorizationRejection, Void> check(
      final CamundaAuthentication authentication,
      final RequiredAuthorization<T> authorization,
      final T resource) {
    return check(authentication, authorization);
  }

  private static String resourceId(final RequiredAuthorization<?> authorization) {
    return authorization.hasAnyResourceIds() ? authorization.resourceIds().getFirst() : null;
  }
}
