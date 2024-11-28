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

public class AuthorizationPermissionDbModel implements DbModel<AuthorizationPermissionDbModel> {

  private PermissionType type;
  private Set<String> resourceIds;

  public PermissionType permissionType() {
    return type;
  }

  public void type(final PermissionType type) {
    this.type = type;
  }

  public Set<String> resourceIds() {
    return resourceIds;
  }

  public void resourceIds(final Set<String> resourceIds) {
    this.resourceIds = resourceIds;
  }

  @Override
  public AuthorizationPermissionDbModel copy(
      final Function<
              ObjectBuilder<AuthorizationPermissionDbModel>,
              ObjectBuilder<AuthorizationPermissionDbModel>>
          copyFunction) {
    return copyFunction.apply(new Builder().type(type).resourceIds(resourceIds)).build();
  }

  public static class Builder implements ObjectBuilder<AuthorizationPermissionDbModel> {

    private PermissionType type;
    private Set<String> resourceIds;

    public Builder type(final PermissionType type) {
      this.type = type;
      return this;
    }

    public Builder resourceIds(final Set<String> resourceIds) {
      this.resourceIds = resourceIds;
      return this;
    }

    @Override
    public AuthorizationPermissionDbModel build() {
      final AuthorizationPermissionDbModel permission = new AuthorizationPermissionDbModel();
      permission.type = type;
      permission.resourceIds = resourceIds;
      return permission;
    }
  }
}
