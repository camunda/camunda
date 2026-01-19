/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.auth;

import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import java.util.ArrayList;
import java.util.List;

public final class Permissions {

  private final ResourceType resourceType;
  private final PermissionType permissionType;
  private final List<String> resourceIds;
  private final List<String> resourcePropertyNames;

  /** Constructor for backward compatibility - creates permissions with resource IDs only. */
  public Permissions(
      final ResourceType resourceType,
      final PermissionType permissionType,
      final List<String> resourceIds) {
    this(resourceType, permissionType, resourceIds, null);
  }

  private Permissions(
      final ResourceType resourceType,
      final PermissionType permissionType,
      final List<String> resourceIds,
      final List<String> resourcePropertyNames) {
    this.resourceType = resourceType;
    this.permissionType = permissionType;
    this.resourceIds = resourceIds != null ? resourceIds.stream().distinct().toList() : List.of();
    this.resourcePropertyNames =
        resourcePropertyNames != null
            ? resourcePropertyNames.stream().distinct().toList()
            : List.of();
  }

  public ResourceType resourceType() {
    return resourceType;
  }

  public PermissionType permissionType() {
    return permissionType;
  }

  public List<String> resourceIds() {
    return resourceIds;
  }

  public List<String> resourcePropertyNames() {
    return resourcePropertyNames;
  }

  /**
   * Creates a permission with a single property name.
   *
   * @param resourceType the resource type
   * @param permissionType the permission type
   * @param propertyName the property name
   * @return a new Permissions instance with the specified property name
   */
  public static Permissions withPropertyName(
      final ResourceType resourceType,
      final PermissionType permissionType,
      final String propertyName) {
    return Permissions.forResource(resourceType, permissionType).withProperty(propertyName).build();
  }

  /**
   * Creates a builder for defining permissions with resource IDs and/or property names.
   *
   * @param resourceType the resource type
   * @param permissionType the permission type
   * @return a new Builder instance
   */
  public static Builder forResource(
      final ResourceType resourceType, final PermissionType permissionType) {
    return new Builder(resourceType, permissionType);
  }

  public static final class Builder {

    private final ResourceType resourceType;
    private final PermissionType permissionType;
    private final List<String> resourceIds = new ArrayList<>();
    private final List<String> resourcePropertyNames = new ArrayList<>();

    private Builder(final ResourceType resourceType, final PermissionType permissionType) {
      this.resourceType = resourceType;
      this.permissionType = permissionType;
    }

    public Builder withResourceId(final String resourceId) {
      resourceIds.add(resourceId);
      return this;
    }

    public Builder withResourceIds(final List<String> resourceIds) {
      this.resourceIds.addAll(resourceIds);
      return this;
    }

    public Builder withProperty(final String propertyName) {
      resourcePropertyNames.add(propertyName);
      return this;
    }

    public Builder withProperties(final List<String> propertyNames) {
      resourcePropertyNames.addAll(propertyNames);
      return this;
    }

    public Permissions build() {
      if (resourceIds.isEmpty() && resourcePropertyNames.isEmpty()) {
        throw new IllegalStateException(
            "At least one resourceId or resourcePropertyName must be specified");
      }
      return new Permissions(resourceType, permissionType, resourceIds, resourcePropertyNames);
    }
  }
}
