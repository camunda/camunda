/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.function.Function;

public class RoleDbModel implements DbModel<RoleDbModel> {

  private Long roleKey;
  private String name;
  private List<RoleMemberDbModel> members;

  public Long roleKey() {
    return roleKey;
  }

  public void roleKey(final Long roleKey) {
    this.roleKey = roleKey;
  }

  public String name() {
    return name;
  }

  public void name(final String name) {
    this.name = name;
  }

  public List<RoleMemberDbModel> members() {
    return members;
  }

  public void members(final List<RoleMemberDbModel> members) {
    this.members = members;
  }

  @Override
  public RoleDbModel copy(
      final Function<ObjectBuilder<RoleDbModel>, ObjectBuilder<RoleDbModel>> copyFunction) {
    return copyFunction.apply(new Builder().roleKey(roleKey).name(name).members(members)).build();
  }

  @Override
  public String toString() {
    return "RoleDbModel{"
        + "roleKey="
        + roleKey
        + ", name='"
        + name
        + '\''
        + ", members="
        + members
        + '}';
  }

  public static class Builder implements ObjectBuilder<RoleDbModel> {

    private Long roleKey;
    private String name;
    private List<RoleMemberDbModel> members;

    public Builder() {}

    public Builder roleKey(final Long roleKey) {
      this.roleKey = roleKey;
      return this;
    }

    public Builder name(final String name) {
      this.name = name;
      return this;
    }

    public Builder members(final List<RoleMemberDbModel> members) {
      this.members = members;
      return this;
    }

    @Override
    public RoleDbModel build() {
      final RoleDbModel model = new RoleDbModel();
      model.roleKey(roleKey);
      model.name(name);
      model.members(members);
      return model;
    }
  }
}
