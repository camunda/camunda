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

public record GroupDbModel(
    Long groupKey,
    String groupId,
    String name,
    String description,
    List<GroupMemberDbModel> members)
    implements DbModel<GroupDbModel> {

  public GroupDbModel {
    // Must stay mutable: MyBatis appends to this via <collection> after construction.
    members = members != null ? members : new ArrayList<>();
  }

  // Matches groupResultMap's <constructor>, which omits members -- populated separately via the
  // sibling <collection> element.
  public GroupDbModel(
      final Long groupKey, final String groupId, final String name, final String description) {
    this(groupKey, groupId, name, description, null);
  }

  @Override
  public GroupDbModel copy(
      final Function<ObjectBuilder<GroupDbModel>, ObjectBuilder<GroupDbModel>> copyFunction) {
    return copyFunction
        .apply(
            new Builder()
                .groupKey(groupKey)
                .groupId(groupId)
                .name(name)
                .description(description)
                .members(members))
        .build();
  }

  public static class Builder implements ObjectBuilder<GroupDbModel> {

    private Long groupKey;
    private String groupId;
    private String name;
    private String description;
    private List<GroupMemberDbModel> members;

    public Builder() {}

    public Builder groupKey(final Long groupKey) {
      this.groupKey = groupKey;
      return this;
    }

    public Builder groupId(final String groupId) {
      this.groupId = groupId;
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

    public Builder members(final List<GroupMemberDbModel> members) {
      this.members = members;
      return this;
    }

    @Override
    public GroupDbModel build() {
      return new GroupDbModel(groupKey, groupId, name, description, members);
    }
  }
}
