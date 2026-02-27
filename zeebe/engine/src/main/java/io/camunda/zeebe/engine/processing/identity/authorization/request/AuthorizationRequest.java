/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.request;

import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record AuthorizationRequest(
    Map<String, Object> claims,
    AuthorizationResourceType resourceType,
    PermissionType permissionType,
    String tenantId,
    Set<String> resourceIds,
    boolean isNewResource,
    boolean isTenantOwnedResource,
    boolean isTriggeredByInternalCommand) {

  public String getForbiddenErrorMessage() {
    if (resourceIds.isEmpty()) {
      return AuthorizationCheckBehavior.FORBIDDEN_ERROR_MESSAGE.formatted(
          permissionType, resourceType);
    }

    return AuthorizationCheckBehavior.FORBIDDEN_ERROR_MESSAGE_WITH_RESOURCE.formatted(
        permissionType,
        resourceType,
        resourceIds.stream()
            .filter(resourceId -> resourceId != null && !resourceId.isEmpty())
            .sorted()
            .collect(Collectors.joining(", ")));
  }

  public String getTenantErrorMessage() {
    final var errorMsg =
        isNewResource
            ? AuthorizationCheckBehavior.FORBIDDEN_FOR_TENANT_ERROR_MESSAGE
            : AuthorizationCheckBehavior.NOT_FOUND_FOR_TENANT_ERROR_MESSAGE;
    return errorMsg.formatted(permissionType, resourceType, tenantId);
  }

  public static AuthorizationRequest.Builder builder() {
    return new AuthorizationRequest.Builder();
  }

  public static final class Builder {

    private TypedRecord<?> command;
    private Map<String, Object> authorizationClaims;
    private AuthorizationResourceType resourceType;
    private PermissionType permissionType;
    private Set<String> resourceIds;
    private String tenantId;
    private boolean isNewResource;

    public Builder() {
      authorizationClaims = new HashMap<>();
      resourceIds = new HashSet<>();
    }

    public Builder command(final TypedRecord<?> command) {
      this.command = command;
      return this;
    }

    public Builder authorizationClaims(final Map<String, Object> authorizationClaims) {
      this.authorizationClaims = authorizationClaims;
      return this;
    }

    public Builder resourceType(final AuthorizationResourceType resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    public Builder permissionType(final PermissionType permissionType) {
      this.permissionType = permissionType;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder isNewResource(final boolean isNewResource) {
      this.isNewResource = isNewResource;
      return this;
    }

    public Builder newResource() {
      return isNewResource(true);
    }

    public Builder addResourceId(final String resourceId) {
      resourceIds.add(resourceId);
      return this;
    }

    public Builder addAllResourceIds(final Collection<String> resourceIds) {
      resourceIds.forEach(this::addResourceId);
      return this;
    }

    public AuthorizationRequest build() {
      final var claims = resolveClaims();
      final var isTenantOwnedResource = tenantId != null && !tenantId.isEmpty();
      final var isTriggeredByInternalCommand = command != null && command.isInternalCommand();

      if (resourceIds instanceof HashSet<String>) {
        resourceIds = Collections.unmodifiableSet(resourceIds);
      }

      return new AuthorizationRequest(
          claims,
          resourceType,
          permissionType,
          tenantId,
          resourceIds,
          isNewResource,
          isTenantOwnedResource,
          isTriggeredByInternalCommand);
    }

    private Map<String, Object> resolveClaims() {
      final var claims = command != null ? command.getAuthorizations() : authorizationClaims;
      return Collections.unmodifiableMap(
          Objects.requireNonNullElse(claims, Collections.emptyMap()));
    }
  }
}
