/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.GroupMemberFilter;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record GroupMemberDbQuery(
    GroupMemberFilter filter,
    List<String> authorizedResourceIds,
    DbQuerySorting<GroupMemberEntity> sort,
    DbQueryPage page) {

  public static GroupMemberDbQuery of(
      final Function<GroupMemberDbQuery.Builder, ObjectBuilder<GroupMemberDbQuery>> fn) {
    return fn.apply(new GroupMemberDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<GroupMemberDbQuery> {

    private static final GroupMemberFilter EMPTY_FILTER = FilterBuilders.groupMember().build();

    private GroupMemberFilter filter;
    private DbQuerySorting<GroupMemberEntity> sort;
    private DbQueryPage page;
    private List<String> authorizedResourceIds = Collections.emptyList();

    public Builder filter(final GroupMemberFilter value) {
      filter = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<GroupMemberEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder authorizedResourceIds(final List<String> authorizedResourceIds) {
      this.authorizedResourceIds = authorizedResourceIds;
      return this;
    }

    public Builder filter(
        final Function<GroupMemberFilter.Builder, ObjectBuilder<GroupMemberFilter>> fn) {
      return filter(FilterBuilders.groupMember(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<GroupMemberEntity>,
                ObjectBuilder<DbQuerySorting<GroupMemberEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public GroupMemberDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      return new GroupMemberDbQuery(filter, authorizedResourceIds, sort, page);
    }
  }
}
