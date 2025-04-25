/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import io.camunda.util.ObjectBuilder;
import java.util.Set;

public record GroupFilter(
    Long groupKey, String groupId, String name, String description, Set<String> memberIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<GroupFilter> {
    private Long groupKey;
    private String groupId;
    private String name;
    private String description;
    private Set<String> memberIds;

    public Builder groupKey(final Long value) {
      groupKey = value;
      return this;
    }

    public Builder groupId(final String value) {
      groupId = value;
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

    public Builder memberId(final String value) {
      return memberIds(Set.of(value));
    }

    public Builder memberIds(final Set<String> value) {
      memberIds = value;
      return this;
    }

    @Override
    public GroupFilter build() {
      return new GroupFilter(groupKey, groupId, name, description, memberIds);
    }
  }
}
