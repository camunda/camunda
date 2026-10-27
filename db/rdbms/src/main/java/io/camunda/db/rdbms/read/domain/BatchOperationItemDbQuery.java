/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.filter.BatchOperationItemFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record BatchOperationItemDbQuery(
    BatchOperationItemFilter filter,
    DbQuerySorting<BatchOperationItemEntity> sort,
    DbQueryPage page) {

  public static BatchOperationItemDbQuery of(
      final Function<BatchOperationItemDbQuery.Builder, ObjectBuilder<BatchOperationItemDbQuery>>
          fn) {
    return fn.apply(new BatchOperationItemDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<BatchOperationItemDbQuery> {

    private static final BatchOperationItemFilter EMPTY_FILTER =
        FilterBuilders.batchOperationItem().build();

    private BatchOperationItemFilter filter;
    private DbQuerySorting<BatchOperationItemEntity> sort;
    private DbQueryPage page;

    public Builder filter(final BatchOperationItemFilter value) {
      filter = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<BatchOperationItemEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(
        final Function<BatchOperationItemFilter.Builder, ObjectBuilder<BatchOperationItemFilter>>
            fn) {
      return filter(FilterBuilders.batchOperationItem(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<BatchOperationItemEntity>,
                ObjectBuilder<DbQuerySorting<BatchOperationItemEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public BatchOperationItemDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      return new BatchOperationItemDbQuery(filter, sort, page);
    }
  }
}
