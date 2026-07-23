/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.util.ObjectBuilder;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public record AuthorizationDbModel(
    Long authorizationKey,
    String ownerId,
    String ownerType,
    String resourceType,
    Short resourceMatcher,
    String resourceId,
    String resourcePropertyName,
    Set<PermissionType> permissionTypes)
    implements DbModel<AuthorizationDbModel> {

  public AuthorizationDbModel {
    // Must stay mutable: MyBatis appends to this via <collection> after construction.
    permissionTypes = permissionTypes != null ? permissionTypes : new HashSet<>();
  }

  // Matches authorizationResultMap's <constructor>, which omits permissionTypes -- populated
  // separately via the sibling <collection> element.
  public AuthorizationDbModel(
      final Long authorizationKey,
      final String ownerId,
      final String ownerType,
      final String resourceType,
      final Short resourceMatcher,
      final String resourceId,
      final String resourcePropertyName) {
    this(
        authorizationKey,
        ownerId,
        ownerType,
        resourceType,
        resourceMatcher,
        resourceId,
        resourcePropertyName,
        null);
  }

  @Override
  public AuthorizationDbModel copy(
      final Function<ObjectBuilder<AuthorizationDbModel>, ObjectBuilder<AuthorizationDbModel>>
          copyFunction) {
    return copyFunction
        .apply(
            new Builder()
                .authorizationKey(authorizationKey)
                .ownerId(ownerId)
                .ownerType(ownerType)
                .resourceMatcher(resourceMatcher)
                .resourceId(resourceId)
                .resourcePropertyName(resourcePropertyName)
                .resourceType(resourceType)
                .permissionTypes(permissionTypes))
        .build();
  }

  public static class Builder implements ObjectBuilder<AuthorizationDbModel> {

    private Long authorizationKey;
    private String ownerId;
    private String ownerType;
    private Short resourceMatcher;
    private String resourceId;
    private String resourcePropertyName;
    private String resourceType;
    private Set<PermissionType> permissionTypes;

    public Builder() {}

    public Builder authorizationKey(final Long authorizationKey) {
      this.authorizationKey = authorizationKey;
      return this;
    }

    public Builder ownerId(final String ownerId) {
      this.ownerId = ownerId;
      return this;
    }

    public Builder ownerType(final String ownerType) {
      this.ownerType = ownerType;
      return this;
    }

    public Builder resourceType(final String resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    public Builder resourceMatcher(final Short resourceMatcher) {
      this.resourceMatcher = resourceMatcher;
      return this;
    }

    public Builder resourceId(final String resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public Builder resourcePropertyName(final String resourcePropertyName) {
      this.resourcePropertyName = resourcePropertyName;
      return this;
    }

    public Builder permissionTypes(final Set<PermissionType> permissionTypes) {
      this.permissionTypes = permissionTypes;
      return this;
    }

    @Override
    public AuthorizationDbModel build() {
      return new AuthorizationDbModel(
          authorizationKey,
          ownerId,
          ownerType,
          resourceType,
          resourceMatcher,
          resourceId,
          resourcePropertyName,
          permissionTypes);
    }
  }
}
