/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Set;
import java.util.function.Function;

public class AuthorizationDbModel implements DbModel<AuthorizationDbModel> {

  private Long authorizationKey;
  private String ownerId;
  private String ownerType;
  private String resourceType;
  private Short resourceMatcher;
  private String resourceId;
  private Set<PermissionType> permissionTypes;

  public Long authorizationKey() {
    return authorizationKey;
  }

  public void authorizationKey(final Long authorizationKey) {
    this.authorizationKey = authorizationKey;
  }

  public String ownerId() {
    return ownerId;
  }

  public void ownerId(final String ownerId) {
    this.ownerId = ownerId;
  }

  public String ownerType() {
    return ownerType;
  }

  public void ownerType(final String ownerType) {
    this.ownerType = ownerType;
  }

  public String resourceType() {
    return resourceType;
  }

  public void resourceType(final String resourceType) {
    this.resourceType = resourceType;
  }

  public Short resourceMatcher() {
    return resourceMatcher;
  }

  public void resourceMatcher(final Short resourceMatcher) {
    this.resourceMatcher = resourceMatcher;
  }

  public String resourceId() {
    return resourceId;
  }

  public void resourceId(final String resourceId) {
    this.resourceId = resourceId;
  }

  public Set<PermissionType> permissionTypes() {
    return permissionTypes;
  }

  public void permissionTypes(final Set<PermissionType> permissionTypes) {
    this.permissionTypes = permissionTypes;
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

    public Builder permissionTypes(final Set<PermissionType> permissionTypes) {
      this.permissionTypes = permissionTypes;
      return this;
    }

    @Override
    public AuthorizationDbModel build() {
      final AuthorizationDbModel model = new AuthorizationDbModel();
      model.authorizationKey(authorizationKey);
      model.ownerId(ownerId);
      model.ownerType(ownerType);
      model.resourceMatcher(resourceMatcher);
      model.resourceId(resourceId);
      model.resourceType(resourceType);
      model.permissionTypes(permissionTypes);
      return model;
    }
  }
}
