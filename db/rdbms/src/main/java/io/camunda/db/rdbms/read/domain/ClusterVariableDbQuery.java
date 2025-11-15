/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.ClusterVariableEntity;
import io.camunda.search.filter.ClusterVariableFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record ClusterVariableDbQuery(
    ClusterVariableFilter filter,
    List<String> authorizedResourceIds,
    List<String> authorizedTenantIds,
    DbQuerySorting<ClusterVariableEntity> sort,
    DbQueryPage page) {

  public static ClusterVariableDbQuery of(
      final Function<ClusterVariableDbQuery.Builder, ObjectBuilder<ClusterVariableDbQuery>> fn) {
    return fn.apply(new ClusterVariableDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<ClusterVariableDbQuery> {

    private static final ClusterVariableFilter EMPTY_FILTER =
        FilterBuilders.clusterVariable().build();

    private ClusterVariableFilter filter;
    private List<String> authorizedResourceIds = List.of();
    private List<String> authorizedTenantIds = List.of();
    private DbQuerySorting<ClusterVariableEntity> sort;
    private DbQueryPage page;

    public Builder filter(final ClusterVariableFilter value) {
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

    public Builder sort(final DbQuerySorting<ClusterVariableEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(
        final Function<ClusterVariableFilter.Builder, ObjectBuilder<ClusterVariableFilter>> fn) {
      return filter(FilterBuilders.clusterVariable(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<ClusterVariableEntity>,
                ObjectBuilder<DbQuerySorting<ClusterVariableEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public ClusterVariableDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new ClusterVariableDbQuery(
          filter, authorizedResourceIds, authorizedTenantIds, sort, page);
    }
  }
}
