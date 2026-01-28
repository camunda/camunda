/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.aggregation.GlobalJobStatisticsAggregation;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.GlobalJobStatisticsFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.NoSort;
import io.camunda.search.sort.SortOption;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record GlobalJobStatisticsQuery(GlobalJobStatisticsFilter filter)
    implements TypedSearchAggregationQuery<
        GlobalJobStatisticsFilter, SortOption, GlobalJobStatisticsAggregation> {

  public static GlobalJobStatisticsQuery of(
      final Function<Builder, ObjectBuilder<GlobalJobStatisticsQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  @Override
  public SortOption sort() {
    return NoSort.NO_SORT;
  }

  @Override
  public SearchQueryPage page() {
    return SearchQueryPage.NO_ENTITIES_QUERY;
  }

  public static final class Builder implements ObjectBuilder<GlobalJobStatisticsQuery> {
    private static final GlobalJobStatisticsFilter EMPTY_FILTER =
        FilterBuilders.globalJobStatistics().build();

    private GlobalJobStatisticsFilter filter;

    public Builder filter(final GlobalJobStatisticsFilter value) {
      filter = value;
      return this;
    }

    public Builder filter(
        final Function<GlobalJobStatisticsFilter.Builder, ObjectBuilder<GlobalJobStatisticsFilter>>
            fn) {
      return filter(FilterBuilders.globalJobStatistics(fn));
    }

    @Override
    public GlobalJobStatisticsQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      return new GlobalJobStatisticsQuery(filter);
    }
  }
}
