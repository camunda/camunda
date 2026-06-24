/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.CorrelatedMessageSubscriptionFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.CorrelatedMessageSubscriptionSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record CorrelatedMessageSubscriptionQuery(
    CorrelatedMessageSubscriptionFilter filter,
    CorrelatedMessageSubscriptionSort sort,
    SearchQueryPage page)
    implements TypedSearchQuery<
        CorrelatedMessageSubscriptionFilter, CorrelatedMessageSubscriptionSort> {

  public static CorrelatedMessageSubscriptionQuery of(
      final Function<
              CorrelatedMessageSubscriptionQuery.Builder,
              ObjectBuilder<CorrelatedMessageSubscriptionQuery>>
          fn) {
    return fn.apply(new CorrelatedMessageSubscriptionQuery.Builder()).build();
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<CorrelatedMessageSubscriptionQuery.Builder>
      implements TypedSearchQueryBuilder<
          CorrelatedMessageSubscriptionQuery,
          CorrelatedMessageSubscriptionQuery.Builder,
          CorrelatedMessageSubscriptionFilter,
          CorrelatedMessageSubscriptionSort> {

    private static final CorrelatedMessageSubscriptionFilter EMPTY_FILTER =
        FilterBuilders.correlatedMessageSubscription().build();
    private static final CorrelatedMessageSubscriptionSort EMPTY_SORT =
        SortOptionBuilders.correlatedMessageSubscription().build();

    private CorrelatedMessageSubscriptionFilter filter;
    private CorrelatedMessageSubscriptionSort sort;

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public Builder filter(final CorrelatedMessageSubscriptionFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final CorrelatedMessageSubscriptionSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<
                CorrelatedMessageSubscriptionFilter.Builder,
                ObjectBuilder<CorrelatedMessageSubscriptionFilter>>
            fn) {
      return filter(FilterBuilders.correlatedMessageSubscription(fn));
    }

    public Builder sort(
        final Function<
                CorrelatedMessageSubscriptionSort.Builder,
                ObjectBuilder<CorrelatedMessageSubscriptionSort>>
            fn) {
      return sort(SortOptionBuilders.correlatedMessageSubscription(fn));
    }

    @Override
    public CorrelatedMessageSubscriptionQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new CorrelatedMessageSubscriptionQuery(filter, sort, page());
    }
  }
}
