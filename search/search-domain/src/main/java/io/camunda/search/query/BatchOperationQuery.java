/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.BatchOperationFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.BatchOperationSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record BatchOperationQuery(
    BatchOperationFilter filter, BatchOperationSort sort, SearchQueryPage page)
    implements TypedSearchQuery<BatchOperationFilter, BatchOperationSort> {

  public static BatchOperationQuery of(
      final Function<Builder, ObjectBuilder<BatchOperationQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          BatchOperationQuery,
          BatchOperationQuery.Builder,
          BatchOperationFilter,
          BatchOperationSort> {

    private static final BatchOperationFilter EMPTY_FILTER =
        FilterBuilders.batchOperation().build();
    private static final BatchOperationSort EMPTY_SORT =
        SortOptionBuilders.batchOperation().build();

    private BatchOperationFilter filter;
    private BatchOperationSort sort;

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public Builder filter(final BatchOperationFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final BatchOperationSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<BatchOperationFilter.Builder, ObjectBuilder<BatchOperationFilter>> fn) {
      return filter(FilterBuilders.batchOperation(fn));
    }

    public Builder sort(
        final Function<BatchOperationSort.Builder, ObjectBuilder<BatchOperationSort>> fn) {
      return sort(SortOptionBuilders.batchOperation(fn));
    }

    @Override
    public BatchOperationQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new BatchOperationQuery(filter, sort, page());
    }
  }
}
