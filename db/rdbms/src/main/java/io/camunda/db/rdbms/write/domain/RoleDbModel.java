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
  private String roleId;
  private String name;
  private String description;
  private List<RoleMemberDbModel> members;
  private boolean allTenantsAccess;

  public Long roleKey() {
    return roleKey;
  }

  public void roleKey(final Long roleKey) {
    this.roleKey = roleKey;
  }

  public boolean isAllTenantsAccess() {
    return allTenantsAccess;
  }

  public void setAllTenantsAccess(final boolean allTenantsAccess) {
    this.allTenantsAccess = allTenantsAccess;
  }

  public String roleId() {
    return roleId;
  }

  public void roleId(final String roleId) {
    this.roleId = roleId;
  }

  public String name() {
    return name;
  }

  public void name(final String name) {
    this.name = name;
  }

  public String description() {
    return description;
  }

  public void description(final String description) {
    this.description = description;
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
        + ", roleId='"
        + roleId
        + ", name='"
        + name
        + ", description='"
        + description
        + '\''
        + ", members="
        + members
        + '}';
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
      final RoleDbModel model = new RoleDbModel();
      model.roleKey(roleKey);
      model.roleId(roleId);
      model.name(name);
      model.description(description);
      model.members(members);
      return model;
    }
  }
}
