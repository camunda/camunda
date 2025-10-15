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
import java.util.function.Function;

public record GroupMemberFilter(String groupId, EntityType memberType) implements FilterBase {

  public static GroupMemberFilter of(
      final Function<GroupMemberFilter.Builder, GroupMemberFilter.Builder> builderFunction) {
    return builderFunction.apply(new GroupMemberFilter.Builder()).build();
  }

  public Builder toBuilder() {
    return new Builder().groupId(groupId).memberType(memberType);
  }

  public static final class Builder implements ObjectBuilder<GroupMemberFilter> {
    private String joinParentId;
    private EntityType memberType;

    public Builder groupId(final String value) {
      joinParentId = value;
      return this;
    }

    public Builder memberType(final EntityType value) {
      memberType = value;
      return this;
    }

    @Override
    public GroupMemberFilter build() {
      return new GroupMemberFilter(joinParentId, memberType);
    }
  }
}
