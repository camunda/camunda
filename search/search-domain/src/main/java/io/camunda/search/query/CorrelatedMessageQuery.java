/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.CorrelatedMessageFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.CorrelatedMessageSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record CorrelatedMessageQuery(
    CorrelatedMessageFilter filter, CorrelatedMessageSort sort, SearchQueryPage page)
    implements TypedSearchQuery<CorrelatedMessageFilter, CorrelatedMessageSort> {

  public static CorrelatedMessageQuery of(
      final Function<CorrelatedMessageQuery.Builder, ObjectBuilder<CorrelatedMessageQuery>> fn) {
    return fn.apply(new CorrelatedMessageQuery.Builder()).build();
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<CorrelatedMessageQuery.Builder>
      implements TypedSearchQueryBuilder<
          CorrelatedMessageQuery,
          CorrelatedMessageQuery.Builder,
          CorrelatedMessageFilter,
          CorrelatedMessageSort> {

    private static final CorrelatedMessageFilter EMPTY_FILTER =
        FilterBuilders.correlatedMessage().build();
    private static final CorrelatedMessageSort EMPTY_SORT =
        SortOptionBuilders.correlatedMessage().build();

    private CorrelatedMessageFilter filter;
    private CorrelatedMessageSort sort;

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public Builder filter(final CorrelatedMessageFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final CorrelatedMessageSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<CorrelatedMessageFilter.Builder, ObjectBuilder<CorrelatedMessageFilter>>
            fn) {
      return filter(FilterBuilders.correlatedMessage(fn));
    }

    public Builder sort(
        final Function<CorrelatedMessageSort.Builder, ObjectBuilder<CorrelatedMessageSort>> fn) {
      return sort(SortOptionBuilders.correlatedMessage(fn));
    }

    @Override
    public CorrelatedMessageQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new CorrelatedMessageQuery(filter, sort, page());
    }
  }
}
