/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.VariableFilter;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record VariableDbQuery(
    VariableFilter filter,
    List<String> authorizedResourceIds,
    List<String> authorizedTenantIds,
    DbQuerySorting<VariableEntity> sort,
    DbQueryPage page) {

  public static VariableDbQuery of(
      final Function<VariableDbQuery.Builder, ObjectBuilder<VariableDbQuery>> fn) {
    return fn.apply(new VariableDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<VariableDbQuery> {

    private static final VariableFilter EMPTY_FILTER = FilterBuilders.variable().build();

    private VariableFilter filter;
    private List<String> authorizedResourceIds = List.of();
    private List<String> authorizedTenantIds = List.of();
    private DbQuerySorting<VariableEntity> sort;
    private DbQueryPage page;

    public Builder filter(final VariableFilter value) {
      filter = value;
      return this;
    }

    public Builder authorizedResourceIds(final List<String> authorizedResourceIds) {
      this.authorizedResourceIds = authorizedResourceIds;
      return this;
    }

    public Builder authorizedTenantIds(final List<String> authorizedTenantIds) {
      this.authorizedTenantIds = authorizedTenantIds;
      return this;
    }

    public Builder sort(final DbQuerySorting<VariableEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(
        final Function<VariableFilter.Builder, ObjectBuilder<VariableFilter>> fn) {
      return filter(FilterBuilders.variable(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<VariableEntity>,
                ObjectBuilder<DbQuerySorting<VariableEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public VariableDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new VariableDbQuery(filter, authorizedResourceIds, authorizedTenantIds, sort, page);
    }
  }
}
