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
import java.util.function.Function;

public class AuthorizationDbModel implements DbModel<AuthorizationDbModel> {

  private Long ownerKey;
  private String ownerType;
  private String resourceType;
  private PermissionType permissionType;
  private String resourceId;

  public Long ownerKey() {
    return ownerKey;
  }

  public void ownerKey(final Long ownerKey) {
    this.ownerKey = ownerKey;
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

  public PermissionType permissionType() {
    return permissionType;
  }

  public void permissionType(final PermissionType permissionType) {
    this.permissionType = permissionType;
  }

  public String resourceId() {
    return resourceId;
  }

  public void resourceId(final String resourceId) {
    this.resourceId = resourceId;
  }

  @Override
  public AuthorizationDbModel copy(
      final Function<ObjectBuilder<AuthorizationDbModel>, ObjectBuilder<AuthorizationDbModel>>
          copyFunction) {
    return copyFunction
        .apply(
            new Builder()
                .ownerKey(ownerKey)
                .ownerType(ownerType)
                .resourceType(resourceType)
                .permissionType(permissionType)
                .resourceId(resourceId))
        .build();
  }

  public static class Builder implements ObjectBuilder<AuthorizationDbModel> {

    private Long ownerKey;
    private String ownerType;
    private String resourceType;
    private PermissionType permissionType;
    private String resourceId;

    public Builder() {}

    public Builder ownerKey(final Long ownerKey) {
      this.ownerKey = ownerKey;
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

    public Builder permissionType(final PermissionType permissionType) {
      this.permissionType = permissionType;
      return this;
    }

    public Builder resourceId(final String resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    @Override
    public AuthorizationDbModel build() {
      final AuthorizationDbModel model = new AuthorizationDbModel();
      model.ownerKey(ownerKey);
      model.ownerType(ownerType);
      model.resourceType(resourceType);
      model.permissionType(permissionType);
      model.resourceId(resourceId);
      return model;
    }
  }
}
