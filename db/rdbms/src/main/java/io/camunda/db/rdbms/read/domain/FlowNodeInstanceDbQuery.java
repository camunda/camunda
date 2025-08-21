/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record FlowNodeInstanceDbQuery(
    FlowNodeInstanceFilter filter,
    List<String> authorizedResourceIds,
    List<String> authorizedTenantIds,
    DbQuerySorting<FlowNodeInstanceEntity> sort,
    DbQueryPage page) {

  public static FlowNodeInstanceDbQuery of(
      final Function<FlowNodeInstanceDbQuery.Builder, ObjectBuilder<FlowNodeInstanceDbQuery>> fn) {
    return fn.apply(new FlowNodeInstanceDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<FlowNodeInstanceDbQuery> {

    private static final FlowNodeInstanceFilter EMPTY_FILTER =
        FilterBuilders.flowNodeInstance().build();

    private FlowNodeInstanceFilter filter;
    private List<String> authorizedResourceIds;
    private List<String> authorizedTenantIds;
    private DbQuerySorting<FlowNodeInstanceEntity> sort;
    private DbQueryPage page;

    public Builder filter(final FlowNodeInstanceFilter value) {
      filter = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<FlowNodeInstanceEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder authorizedResourceIds(final List<String> value) {
      authorizedResourceIds = value;
      return this;
    }

    public Builder authorizedTenantIds(final List<String> value) {
      authorizedTenantIds = value;
      return this;
    }

    public Builder filter(
        final Function<FlowNodeInstanceFilter.Builder, ObjectBuilder<FlowNodeInstanceFilter>> fn) {
      return filter(FilterBuilders.flowNodeInstance(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<FlowNodeInstanceEntity>,
                ObjectBuilder<DbQuerySorting<FlowNodeInstanceEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public FlowNodeInstanceDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new FlowNodeInstanceDbQuery(
          filter, authorizedResourceIds, authorizedTenantIds, sort, page);
    }
  }
}
