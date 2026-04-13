/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

<<<<<<< HEAD
public record ConfiguredAuthorization(
    AuthorizationOwnerType ownerType,
    String ownerId,
    AuthorizationResourceType resourceType,
    String resourceId,
    Set<PermissionType> permissions) {}
=======
public class ConfiguredAuthorization {

  private AuthorizationOwnerType ownerType;
  private String ownerId;
  private AuthorizationResourceType resourceType;
  private String resourceId;
  private String resourcePropertyName;
  private Set<PermissionType> permissions;

  /** Default constructor for Spring Boot binding */
  @SuppressWarnings("unused")
  public ConfiguredAuthorization() {}

  private ConfiguredAuthorization(
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final String resourceId,
      final String resourcePropertyName,
      final Set<PermissionType> permissions) {
    this.ownerType = ownerType;
    this.ownerId = ownerId;
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.resourcePropertyName = resourcePropertyName;
    this.permissions = permissions;
  }

  public static ConfiguredAuthorization idBased(
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final String resourceId,
      final Set<PermissionType> permissions) {
    return new ConfiguredAuthorization(
        ownerType, ownerId, resourceType, resourceId, null, permissions);
  }

  public static ConfiguredAuthorization wildcard(
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final Set<PermissionType> permissions) {
    return idBased(ownerType, ownerId, resourceType, AuthorizationScope.WILDCARD_CHAR, permissions);
  }

  public static ConfiguredAuthorization propertyBased(
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final String resourcePropertyName,
      final Set<PermissionType> permissions) {
    return new ConfiguredAuthorization(
        ownerType, ownerId, resourceType, null, resourcePropertyName, permissions);
  }

  public AuthorizationOwnerType ownerType() {
    return ownerType;
  }

  public String ownerId() {
    return ownerId;
  }

  public AuthorizationResourceType resourceType() {
    return resourceType;
  }

  public String resourceId() {
    return resourceId;
  }

  public String resourcePropertyName() {
    return resourcePropertyName;
  }

  public Set<PermissionType> permissions() {
    return permissions;
  }

  public boolean hasResourceId() {
    return resourceId != null && !resourceId.isBlank();
  }

  public boolean hasResourcePropertyName() {
    return resourcePropertyName != null && !resourcePropertyName.isBlank();
  }

  // --- Spring Boot setters ---
  public void setOwnerType(final AuthorizationOwnerType ownerType) {
    this.ownerType = ownerType;
  }

  public void setOwnerId(final String ownerId) {
    this.ownerId = ownerId;
  }

  public void setResourceType(final AuthorizationResourceType resourceType) {
    this.resourceType = resourceType;
  }

  public void setResourceId(final String resourceId) {
    this.resourceId = resourceId;
  }

  public void setResourcePropertyName(final String resourcePropertyName) {
    this.resourcePropertyName = resourcePropertyName;
  }

  /**
   * Accepts raw permission strings from Spring Boot property binding, filters out null and blank
   * entries (e.g. from trailing commas or whitespace), and converts valid entries to {@link
   * PermissionType}. See <a href="https://github.com/camunda/camunda/issues/42323">#42323</a>.
   */
  public void setPermissions(final Set<String> permissions) {
    if (permissions == null) {
      this.permissions = Set.of();
      return;
    }
    this.permissions =
        permissions.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(PermissionType::valueOf)
            .collect(Collectors.toUnmodifiableSet());
  }
}
>>>>>>> 0528368d (fix: prevent startup failure when permissions config has trailing comma or whitespace)
