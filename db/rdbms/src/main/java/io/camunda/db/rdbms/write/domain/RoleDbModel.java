/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public record RoleDbModel(
    Long roleKey, String roleId, String name, String description, List<RoleMemberDbModel> members)
    implements DbModel<RoleDbModel> {

  public RoleDbModel {
    // Mutable collections are required: MyBatis hydrates collection-mapped fields (e.g. from a
    // <collection> result map or a LEFT JOIN) by calling .add() on the existing instance.
    // Immutable defaults (e.g. List.of()) would cause UnsupportedOperationException at runtime.
    members = members != null ? members : new ArrayList<>();
  }

  @Override
  public RoleDbModel copy(
      final Function<ObjectBuilder<RoleDbModel>, ObjectBuilder<RoleDbModel>> copyFunction) {
    return copyFunction.apply(new Builder().roleKey(roleKey).name(name).members(members)).build();
  }

  public static class Builder implements ObjectBuilder<RoleDbModel> {

    private Long roleKey;
    private String roleId;
    private String name;
    private String description;
    private List<RoleMemberDbModel> members;

    public Builder() {}

    public Builder roleKey(final Long roleKey) {
      this.roleKey = roleKey;
      return this;
    }

    public Builder roleId(final String roleId) {
      this.roleId = roleId;
      return this;
    }

    public Builder name(final String name) {
      this.name = name;
      return this;
    }

    public Builder description(final String description) {
      this.description = description;
      return this;
    }

    public Builder members(final List<RoleMemberDbModel> members) {
      this.members = members;
      return this;
    }

    @Override
    public RoleDbModel build() {
      return new RoleDbModel(roleKey, roleId, name, description, members);
    }
  }
}
