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

public class GroupDbModel implements DbModel<GroupDbModel> {

  private Long groupKey;
  private String name;
  private List<GroupMemberDbModel> members;

  public Long groupKey() {
    return groupKey;
  }

  public void groupKey(final Long groupKey) {
    this.groupKey = groupKey;
  }

  public String name() {
    return name;
  }

  public void name(final String name) {
    this.name = name;
  }

  public List<GroupMemberDbModel> members() {
    return members;
  }

  public void members(final List<GroupMemberDbModel> members) {
    this.members = members;
  }

  @Override
  public GroupDbModel copy(
      final Function<ObjectBuilder<GroupDbModel>, ObjectBuilder<GroupDbModel>> copyFunction) {
    return copyFunction.apply(new Builder().groupKey(groupKey).name(name).members(members)).build();
  }

  @Override
  public String toString() {
    return "GroupDbModel{"
        + "groupKey="
        + groupKey
        + ", name='"
        + name
        + '\''
        + ", members="
        + members
        + '}';
  }

  public static class Builder implements ObjectBuilder<GroupDbModel> {

    private Long groupKey;
    private String name;
    private List<GroupMemberDbModel> members;

    public Builder() {}

    public Builder groupKey(final Long groupKey) {
      this.groupKey = groupKey;
      return this;
    }

    public Builder name(final String name) {
      this.name = name;
      return this;
    }

    public Builder members(final List<GroupMemberDbModel> members) {
      this.members = members;
      return this;
    }

    @Override
    public GroupDbModel build() {
      final GroupDbModel model = new GroupDbModel();
      model.groupKey(groupKey);
      model.name(name);
      model.members(members);
      return model;
    }
  }
}
