/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler;

import io.camunda.authentication.entity.CamundaUser;
import io.camunda.service.AuthorizationServices;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class CustomMethodSecurityExpressionRoot extends SecurityExpressionRoot
    implements MethodSecurityExpressionOperations {

  private static final Set<String> READ_ACCESS_AUTHORITIES = Set.of(PermissionType.READ.name());
  private static final Set<String> WRITE_ACCESS_AUTHORITIES =
      Set.of(
          PermissionType.UPDATE.name(), PermissionType.CREATE.name(), PermissionType.DELETE.name());

  private final AuthorizationServices<AuthorizationRecord> authorizationServices;
  private Object filterObject;
  private Object returnObject;

  public CustomMethodSecurityExpressionRoot(
      final Authentication authentication,
      final AuthorizationServices<AuthorizationRecord> authorizationServices) {
    super(authentication);
    this.authorizationServices = authorizationServices;
  }

  public boolean hasReadAccess(final String resourceType) {
    return hasReadAccess(resourceType, "*");
  }

  public boolean hasReadAccess(final String resourceType, final String entity) {
    return hasPermissions(resourceType, entity, READ_ACCESS_AUTHORITIES);
  }

  public boolean hasWriteAccess(final String resourceType) {
    return hasWriteAccess(resourceType, "*");
  }

  public boolean hasWriteAccess(final String resourceType, final String entity) {
    return hasPermissions(resourceType, entity, WRITE_ACCESS_AUTHORITIES);
  }

  public boolean hasPermissions(
      final String resourceType, final String entity, final Set<String> permissions) {
    return extractOwner()
        .map(
            owner ->
                authorizationServices
                    .fetchAssignedPermissions(
                        owner, AuthorizationResourceType.valueOf(resourceType), entity)
                    .containsAll(permissions))
        .orElse(false);
  }

  private Optional<String> extractOwner() {
    final var authentication = getAuthentication();

    if (authentication != null) {
      if (authentication.getPrincipal() instanceof final CamundaUser authenticatedPrincipal) {
        return Optional.of(authenticatedPrincipal.getUserKey().toString());
      }
      if (authentication instanceof final JwtAuthenticationToken token) {
        // TODO extract mapping rule id
      }
    }
    return Optional.empty();
  }

  @Override
  public Object getFilterObject() {
    return filterObject;
  }

  @Override
  public Object getReturnObject() {
    return returnObject;
  }

  @Override
  public Object getThis() {
    return this;
  }

  @Override
  public void setFilterObject(final Object obj) {
    filterObject = obj;
  }

  @Override
  public void setReturnObject(final Object obj) {
    returnObject = obj;
  }
}
