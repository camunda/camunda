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
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public final class AuthorizationRequest {

  private final Map<String, Object> claims;
  private final AuthorizationResourceType resourceType;
  private final PermissionType permissionType;
  private final String tenantId;
  private final Set<String> resourceIds;
  private final boolean isNewResource;
  private final boolean isTenantOwnedResource;
  private final boolean isTriggeredByInternalCommand;

  private AuthorizationRequest(final Builder builder) {
    claims = resolveClaims(builder);
    resourceType = builder.resourceType;
    permissionType = builder.permissionType;
    tenantId = builder.tenantId;
    resourceIds = Collections.unmodifiableSet(builder.resourceIds);
    isNewResource = builder.isNewResource;
    isTenantOwnedResource = deriveTenantOwnedResource(builder);
    isTriggeredByInternalCommand = deriveTriggeredByInternalCommand(builder);
  }

  private static Map<String, Object> resolveClaims(final Builder builder) {
    final var claims =
        builder.command != null ? builder.command.getAuthorizations() : builder.authorizationClaims;
    return Collections.unmodifiableMap(Objects.requireNonNullElse(claims, Collections.emptyMap()));
  }

  private static boolean deriveTenantOwnedResource(final Builder builder) {
    return builder.tenantId != null && !builder.tenantId.isEmpty();
  }

  private static boolean deriveTriggeredByInternalCommand(final Builder builder) {
    return builder.command != null && builder.command.isInternalCommand();
  }

  public Map<String, Object> claims() {
    return claims;
  }

  public AuthorizationResourceType resourceType() {
    return resourceType;
  }

  public PermissionType permissionType() {
    return permissionType;
  }

  public String tenantId() {
    return tenantId;
  }

  public Set<String> resourceIds() {
    return resourceIds;
  }

  public boolean isNewResource() {
    return isNewResource;
  }

  public boolean isTenantOwnedResource() {
    return isTenantOwnedResource;
  }

  public boolean isTriggeredByInternalCommand() {
    return isTriggeredByInternalCommand;
  }

  public String getForbiddenErrorMessage() {
    if (resourceIds.isEmpty()) {
      return AuthorizationCheckBehavior.FORBIDDEN_ERROR_MESSAGE.formatted(
          permissionType, resourceType);
    }

    return AuthorizationCheckBehavior.FORBIDDEN_ERROR_MESSAGE_WITH_RESOURCE.formatted(
        permissionType,
        resourceType,
        resourceIds.stream().sorted().collect(Collectors.joining(", ")));
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
    private final Set<String> resourceIds = new HashSet<>();
    private String tenantId;
    private boolean isNewResource;

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
      if (StringUtils.isNotEmpty(resourceId)) {
        resourceIds.add(resourceId);
      }
      return this;
    }

    public Builder addAllResourceIds(final Collection<String> resourceIds) {
      resourceIds.forEach(this::addResourceId);
      return this;
    }

    public AuthorizationRequest build() {
      if (resourceType == null) {
        throw new IllegalStateException("resourceType must be set");
      }
      if (permissionType == null) {
        throw new IllegalStateException("permissionType must be set");
      }

      if (command == null && authorizationClaims == null) {
        throw new IllegalStateException("command or authorizationClaims must be provided");
      }
      if (command != null && authorizationClaims != null) {
        throw new IllegalStateException("command and authorizationClaims are mutually exclusive");
      }

      return new AuthorizationRequest(this);
    }
  }
}
