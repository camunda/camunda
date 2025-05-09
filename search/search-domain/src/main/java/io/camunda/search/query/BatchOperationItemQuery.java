/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.BatchOperationItemFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.BatchOperationItemSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record BatchOperationItemQuery(
    BatchOperationItemFilter filter, BatchOperationItemSort sort, SearchQueryPage page)
    implements TypedSearchQuery<BatchOperationItemFilter, BatchOperationItemSort> {

  public static BatchOperationItemQuery of(
      final Function<Builder, ObjectBuilder<BatchOperationItemQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          BatchOperationItemQuery,
          BatchOperationItemQuery.Builder,
          BatchOperationItemFilter,
          BatchOperationItemSort> {

    private static final BatchOperationItemFilter EMPTY_FILTER =
        FilterBuilders.batchOperationItem().build();
    private static final BatchOperationItemSort EMPTY_SORT =
        SortOptionBuilders.batchOperationItem().build();

    private BatchOperationItemFilter filter;
    private BatchOperationItemSort sort;

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public Builder filter(final BatchOperationItemFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final BatchOperationItemSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<BatchOperationItemFilter.Builder, ObjectBuilder<BatchOperationItemFilter>>
            fn) {
      return filter(FilterBuilders.batchOperationItem(fn));
    }

    public Builder sort(
        final Function<BatchOperationItemSort.Builder, ObjectBuilder<BatchOperationItemSort>> fn) {
      return sort(SortOptionBuilders.batchOperationItem(fn));
    }

    @Override
    public BatchOperationItemQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new BatchOperationItemQuery(filter, sort, page());
    }
  }
}
