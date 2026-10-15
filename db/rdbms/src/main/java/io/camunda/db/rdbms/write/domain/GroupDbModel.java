/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record GroupDbModel(
    Long groupKey,
    String groupId,
    String name,
    String description,
    String externalId,
    String attributes,
    OffsetDateTime createdAt,
    OffsetDateTime lastModifiedAt,
    List<GroupMemberDbModel> members)
    implements DbModel<GroupDbModel> {

  public GroupDbModel {
    // Must stay mutable: MyBatis appends to this via <collection> after construction.
    members = Objects.requireNonNullElse(members, new ArrayList<>());
  }

  // Matches groupResultMap's <constructor>, which omits members -- populated separately via the
  // sibling <collection> element.
  public GroupDbModel(
      final Long groupKey,
      final String groupId,
      final String name,
      final String description,
      final String externalId,
      final String attributes,
      final OffsetDateTime createdAt,
      final OffsetDateTime lastModifiedAt) {
    this(
        groupKey,
        groupId,
        name,
        description,
        externalId,
        attributes,
        createdAt,
        lastModifiedAt,
        null);
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
                .externalId(externalId)
                .attributes(attributes)
                .createdAt(createdAt)
                .lastModifiedAt(lastModifiedAt)
                .members(members))
        .build();
  }

  public static class Builder implements ObjectBuilder<GroupDbModel> {

    private Long groupKey;
    private String groupId;
    private String name;
    private String description;
    private String externalId;
    private String attributes;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastModifiedAt;
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

    public Builder externalId(final String externalId) {
      this.externalId = externalId;
      return this;
    }

    public Builder attributes(final String attributes) {
      this.attributes = attributes;
      return this;
    }

    public Builder createdAt(final OffsetDateTime createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder lastModifiedAt(final OffsetDateTime lastModifiedAt) {
      this.lastModifiedAt = lastModifiedAt;
      return this;
    }

    public Builder members(final List<GroupMemberDbModel> members) {
      this.members = members;
      return this;
    }

    @Override
    public GroupDbModel build() {
      return new GroupDbModel(
          groupKey,
          groupId,
          name,
          description,
          externalId,
          attributes,
          createdAt,
          lastModifiedAt,
          members);
    }
  }
}
