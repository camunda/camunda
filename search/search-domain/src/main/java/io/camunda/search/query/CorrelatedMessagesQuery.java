/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.CorrelatedMessagesFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.CorrelatedMessagesSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record CorrelatedMessagesQuery(
    CorrelatedMessagesFilter filter, CorrelatedMessagesSort sort, SearchQueryPage page)
    implements TypedSearchQuery<CorrelatedMessagesFilter, CorrelatedMessagesSort> {

  public static CorrelatedMessagesQuery of(
      final Function<CorrelatedMessagesQuery.Builder, ObjectBuilder<CorrelatedMessagesQuery>>
          fn) {
    return fn.apply(new CorrelatedMessagesQuery.Builder()).build();
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<CorrelatedMessagesQuery.Builder>
      implements TypedSearchQueryBuilder<
          CorrelatedMessagesQuery,
          CorrelatedMessagesQuery.Builder,
          CorrelatedMessagesFilter,
          CorrelatedMessagesSort> {

    private static final CorrelatedMessagesFilter EMPTY_FILTER =
        FilterBuilders.correlatedMessages().build();
    private static final CorrelatedMessagesSort EMPTY_SORT =
        SortOptionBuilders.correlatedMessages().build();

    private CorrelatedMessagesFilter filter;
    private CorrelatedMessagesSort sort;

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public Builder filter(final CorrelatedMessagesFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final CorrelatedMessagesSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<CorrelatedMessagesFilter.Builder, ObjectBuilder<CorrelatedMessagesFilter>>
            fn) {
      return filter(FilterBuilders.correlatedMessages(fn));
    }

    public Builder sort(
        final Function<CorrelatedMessagesSort.Builder, ObjectBuilder<CorrelatedMessagesSort>>
            fn) {
      return sort(SortOptionBuilders.correlatedMessages(fn));
    }

    @Override
    public CorrelatedMessagesQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new CorrelatedMessagesQuery(filter, sort, page());
    }
  }
}