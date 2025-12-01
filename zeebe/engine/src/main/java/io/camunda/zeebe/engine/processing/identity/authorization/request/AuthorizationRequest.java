/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.request;

import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AuthorizationRequest {

  private TypedRecord<?> command;
  private Map<String, Object> authorizationClaims;
  private AuthorizationResourceType resourceType;
  private PermissionType permissionType;
  private final Set<AuthorizationScope> authorizationScopes;
  private String tenantId;
  private boolean isNewResource;
  private boolean isTenantOwnedResource;

  public AuthorizationRequest() {
    authorizationScopes = new HashSet<>();
    authorizationScopes.add(AuthorizationScope.WILDCARD);
    tenantId = null;
    isNewResource = false;
    isTenantOwnedResource = true;
  }

  public AuthorizationRequest(
      final TypedRecord<?> command,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final String tenantId,
      final boolean isNewResource,
      final boolean isTenantOwnedResource) {
    this.command = command;
    this.resourceType = resourceType;
    this.permissionType = permissionType;
    authorizationScopes = new HashSet<>();
    authorizationScopes.add(AuthorizationScope.WILDCARD);
    this.tenantId = tenantId;
    this.isNewResource = isNewResource;
    this.isTenantOwnedResource = isTenantOwnedResource;
  }

  public AuthorizationRequest(
      final TypedRecord<?> command,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final String tenantId,
      final boolean isNewResource) {
    this(command, resourceType, permissionType, tenantId, isNewResource, true);
  }

  public AuthorizationRequest(
      final TypedRecord<?> command,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final String tenantId) {
    this(command, resourceType, permissionType, tenantId, false, true);
  }

  public AuthorizationRequest(
      final TypedRecord<?> command,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    this(command, resourceType, permissionType, null, false, false);
  }

  public AuthorizationRequest command(final TypedRecord<?> command) {
    this.command = command;
    return this;
  }

  public AuthorizationRequest authorizationClaims(final Map<String, Object> authorizationClaims) {
    this.authorizationClaims = authorizationClaims;
    return this;
  }

  public AuthorizationRequest resourceType(final AuthorizationResourceType resourceType) {
    this.resourceType = resourceType;
    return this;
  }

  public AuthorizationRequest permissionType(final PermissionType permissionType) {
    this.permissionType = permissionType;
    return this;
  }

  public AuthorizationRequest tenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public AuthorizationRequest isNewResource(final boolean isNewResource) {
    this.isNewResource = isNewResource;
    return this;
  }

  public AuthorizationRequest isTenantOwnedResource(final boolean isTenantOwnedResource) {
    this.isTenantOwnedResource = isTenantOwnedResource;
    return this;
  }

  public AuthorizationRequest addAuthorizationScope(final AuthorizationScope authorizationScope) {
    authorizationScopes.add(authorizationScope);
    return this;
  }

  public AuthorizationRequest addResourceId(final String resourceId) {
    authorizationScopes.add(AuthorizationScope.of(resourceId));
    return this;
  }

  public AuthorizationRequestMetadata build() {
    if (command != null) {
      authorizationClaims = command.getAuthorizations();
      return new AuthorizationRequestMetadata(
          command.getAuthorizations(),
          resourceType,
          permissionType,
          isNewResource,
          isTenantOwnedResource,
          tenantId,
          Collections.unmodifiableSet(authorizationScopes));
    }
    return new AuthorizationRequestMetadata(
        authorizationClaims,
        resourceType,
        permissionType,
        isNewResource,
        isTenantOwnedResource,
        tenantId,
        Collections.unmodifiableSet(authorizationScopes));
  }

  public TypedRecord<?> getCommand() {
    return command;
  }

  public static AuthorizationRequest builder() {
    return new AuthorizationRequest();
  }
}
