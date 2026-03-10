/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.request;

import io.camunda.zeebe.engine.processing.identity.authorization.property.ResourceAuthorizationProperties;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Lazy;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public record AuthorizationRequest(
    Lazy<Map<String, Object>> lazyClaims,
    AuthorizationResourceType resourceType,
    PermissionType permissionType,
    String tenantId,
    Set<String> resourceIds,
    ResourceAuthorizationProperties resourceProperties,
    boolean isNewResource,
    boolean isTenantOwnedResource,
    boolean isTriggeredByInternalCommand) {

  private static final String FORBIDDEN_ERROR_MESSAGE =
      "Insufficient permissions to perform operation '%s' on resource '%s'";
  private static final String FORBIDDEN_ERROR_MESSAGE_WITH_RESOURCE_IDS =
      FORBIDDEN_ERROR_MESSAGE + ", required resource identifiers are one of '[*, %s]'";
  private static final String FORBIDDEN_ERROR_MESSAGE_WITH_RESOURCE_IDS_AND_PROPERTIES =
      FORBIDDEN_ERROR_MESSAGE_WITH_RESOURCE_IDS
          + " or resource must match property constraints '[%s]'";
  private static final String FORBIDDEN_ERROR_MESSAGE_WITH_RESOURCE_PROPERTIES =
      FORBIDDEN_ERROR_MESSAGE + ", resource did not match property constraints '[%s]'";
  private static final String FORBIDDEN_FOR_TENANT_ERROR_MESSAGE =
      "Expected to perform operation '%s' on resource '%s' for tenant '%s', but user is not assigned to this tenant";
  private static final String NOT_FOUND_FOR_TENANT_ERROR_MESSAGE =
      "Expected to perform operation '%s' on resource '%s', but no resource was found for tenant '%s'";

  /** Forces evaluation and returns the resolved claims map. */
  public Map<String, Object> claims() {
    return lazyClaims.get();
  }

  public boolean hasResourceProperties() {
    return resourceProperties != null && resourceProperties.hasProperties();
  }

  public String getForbiddenErrorMessage() {
    final boolean hasIds = !resourceIds.isEmpty();
    final boolean hasProps = hasResourceProperties();

    if (!hasIds && !hasProps) {
      return FORBIDDEN_ERROR_MESSAGE.formatted(permissionType, resourceType);
    }

    if (hasIds && hasProps) {
      return FORBIDDEN_ERROR_MESSAGE_WITH_RESOURCE_IDS_AND_PROPERTIES.formatted(
          permissionType,
          resourceType,
          resourceIds.stream().sorted().collect(Collectors.joining(", ")),
          resourceProperties.getPropertyNames().stream()
              .sorted()
              .collect(Collectors.joining(", ")));
    }

    if (hasIds) {
      return FORBIDDEN_ERROR_MESSAGE_WITH_RESOURCE_IDS.formatted(
          permissionType,
          resourceType,
          resourceIds.stream().sorted().collect(Collectors.joining(", ")));
    }

    return FORBIDDEN_ERROR_MESSAGE_WITH_RESOURCE_PROPERTIES.formatted(
        permissionType,
        resourceType,
        resourceProperties.getPropertyNames().stream().sorted().collect(Collectors.joining(", ")));
  }

  public String getTenantErrorMessage() {
    final var errorMsg =
        isNewResource ? FORBIDDEN_FOR_TENANT_ERROR_MESSAGE : NOT_FOUND_FOR_TENANT_ERROR_MESSAGE;
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
    private Set<String> resourceIds = new HashSet<>();
    private ResourceAuthorizationProperties resourceProperties;
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

    public Builder resourceProperties(final ResourceAuthorizationProperties resourceProperties) {
      this.resourceProperties = resourceProperties;
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

      final var lazyClaims = Lazy.of(this::resolveClaims);
      final var isTenantOwnedResource = tenantId != null && !tenantId.isEmpty();
      final var isTriggeredByInternalCommand = command != null && command.isInternalCommand();

      if (resourceIds instanceof HashSet<String>) {
        resourceIds = Collections.unmodifiableSet(resourceIds);
      }

      return new AuthorizationRequest(
          lazyClaims,
          resourceType,
          permissionType,
          tenantId,
          resourceIds,
          resourceProperties,
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
