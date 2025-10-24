/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.filter.BatchOperationFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record BatchOperationDbQuery(
    BatchOperationFilter filter,
    List<String> authorizedResourceIds,
    List<String> authorizedTenantIds,
    DbQuerySorting<BatchOperationEntity> sort,
    DbQueryPage page) {

  public static BatchOperationDbQuery of(
      final Function<BatchOperationDbQuery.Builder, ObjectBuilder<BatchOperationDbQuery>> fn) {
    return fn.apply(new BatchOperationDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<BatchOperationDbQuery> {

    private static final BatchOperationFilter EMPTY_FILTER =
        FilterBuilders.batchOperation().build();

    private BatchOperationFilter filter;
    private DbQuerySorting<BatchOperationEntity> sort;
    private DbQueryPage page;
    private List<String> authorizedResourceIds = java.util.Collections.emptyList();
    private List<String> authorizedTenantIds = java.util.Collections.emptyList();

    public Builder filter(final BatchOperationFilter value) {
      filter = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<BatchOperationEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(
        final Function<BatchOperationFilter.Builder, ObjectBuilder<BatchOperationFilter>> fn) {
      return filter(FilterBuilders.batchOperation(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<BatchOperationEntity>,
                ObjectBuilder<DbQuerySorting<BatchOperationEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    public Builder authorizedResourceIds(final List<String> authorizedResourceIds) {
      this.authorizedResourceIds = authorizedResourceIds;
      return this;
    }

    public Builder authorizedTenantIds(final List<String> authorizedTenantIds) {
      this.authorizedTenantIds = authorizedTenantIds;
      return this;
    }

    @Override
    public BatchOperationDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new BatchOperationDbQuery(
          filter, authorizedResourceIds, authorizedTenantIds, sort, page);
    }
  }
}
