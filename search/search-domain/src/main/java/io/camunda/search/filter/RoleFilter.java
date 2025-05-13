/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Set;

public record RoleFilter(
    Long roleKey,
    String roleId,
    String name,
    String description,
    String joinParentId,
    Set<String> memberIds,
    EntityType memberType,
    Set<String> roleIds,
    EntityType childMemberType,
    String tenantId)
    implements FilterBase {
  public Builder toBuilder() {
    return new Builder()
        .roleKey(roleKey)
        .roleId(roleId)
        .name(name)
        .description(description)
        .memberIds(memberIds)
        .memberType(memberType)
        .roleIds(roleIds)
        .childMemberType(childMemberType)
        .tenantId(tenantId);
  }

  public static final class Builder implements ObjectBuilder<RoleFilter> {
    private Long roleKey;
    private String roleId;
    private String name;
    private String description;
    private String joinParentId;
    private Set<String> memberIds;
    private EntityType memberType;
    private Set<String> roleIds;
    private EntityType childMemberType;
    private String tenantId;

    public Builder roleKey(final Long value) {
      roleKey = value;
      return this;
    }

    public Builder roleId(final String value) {
      roleId = value;
      return this;
    }

    public Builder name(final String value) {
      name = value;
      return this;
    }

    public Builder description(final String value) {
      description = value;
      return this;
    }

    public Builder joinParentId(final String value) {
      joinParentId = value;
      return this;
    }

    public Builder memberIds(final Set<String> value) {
      memberIds = value;
      return this;
    }

    public Builder memberId(final String... values) {
      return memberIds(Set.of(values));
    }

    public Builder memberType(final EntityType value) {
      memberType = value;
      return this;
    }

    public Builder roleIds(final Set<String> value) {
      roleIds = value;
      return this;
    }

    public Builder childMemberType(final EntityType value) {
      childMemberType = value;
      return this;
    }

    public Builder tenantId(final String value) {
      tenantId = value;
      return this;
    }

    @Override
    public RoleFilter build() {
      if (memberIds != null && childMemberType == null) {
        throw new IllegalArgumentException("If memberIds is set, childMemberType must be set too");
      }
      return new RoleFilter(
          roleKey,
          roleId,
          name,
          description,
          joinParentId,
          memberIds,
          memberType,
          roleIds,
          childMemberType,
          tenantId);
    }
  }
}
