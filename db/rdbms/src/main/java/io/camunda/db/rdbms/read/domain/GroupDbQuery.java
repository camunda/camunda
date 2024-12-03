/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.GroupFilter;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record GroupDbQuery(GroupFilter filter, DbQuerySorting<GroupEntity> sort, DbQueryPage page) {

  public static GroupDbQuery of(final Function<Builder, ObjectBuilder<GroupDbQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<GroupDbQuery> {

    private static final GroupFilter EMPTY_FILTER = FilterBuilders.group().build();

    private GroupFilter filter;
    private DbQuerySorting<GroupEntity> sort;
    private DbQueryPage page;

    public GroupDbQuery.Builder filter(final GroupFilter value) {
      filter = value;
      return this;
    }

    public GroupDbQuery.Builder sort(final DbQuerySorting<GroupEntity> value) {
      sort = value;
      return this;
    }

    public GroupDbQuery.Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public GroupDbQuery.Builder filter(
        final Function<GroupFilter.Builder, ObjectBuilder<GroupFilter>> fn) {
      return filter(FilterBuilders.group(fn));
    }

    public GroupDbQuery.Builder sort(
        final Function<
                DbQuerySorting.Builder<GroupEntity>, ObjectBuilder<DbQuerySorting<GroupEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public GroupDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      return new GroupDbQuery(filter, sort, page);
    }
  }
}
