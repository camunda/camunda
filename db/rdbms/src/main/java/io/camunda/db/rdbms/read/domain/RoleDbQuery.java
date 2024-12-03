/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.RoleEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.RoleFilter;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record RoleDbQuery(RoleFilter filter, DbQuerySorting<RoleEntity> sort, DbQueryPage page) {

  public static RoleDbQuery of(final Function<Builder, ObjectBuilder<RoleDbQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<RoleDbQuery> {

    private static final RoleFilter EMPTY_FILTER = FilterBuilders.role().build();

    private RoleFilter filter;
    private DbQuerySorting<RoleEntity> sort;
    private DbQueryPage page;

    public RoleDbQuery.Builder filter(final RoleFilter value) {
      filter = value;
      return this;
    }

    public RoleDbQuery.Builder sort(final DbQuerySorting<RoleEntity> value) {
      sort = value;
      return this;
    }

    public RoleDbQuery.Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public RoleDbQuery.Builder filter(
        final Function<RoleFilter.Builder, ObjectBuilder<RoleFilter>> fn) {
      return filter(FilterBuilders.role(fn));
    }

    public RoleDbQuery.Builder sort(
        final Function<
                DbQuerySorting.Builder<RoleEntity>, ObjectBuilder<DbQuerySorting<RoleEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public RoleDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      return new RoleDbQuery(filter, sort, page);
    }
  }
}
