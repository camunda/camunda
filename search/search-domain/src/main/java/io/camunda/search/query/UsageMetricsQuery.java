/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.UsageMetricsFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.search.sort.UsageMetricsSort;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record UsageMetricsQuery(
    UsageMetricsFilter filter, UsageMetricsSort sort, SearchQueryPage page)
    implements TypedSearchQuery<UsageMetricsFilter, UsageMetricsSort> {

  public static UsageMetricsQuery of(final Function<Builder, ObjectBuilder<UsageMetricsQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          UsageMetricsQuery, Builder, UsageMetricsFilter, UsageMetricsSort> {
    private static final UsageMetricsFilter EMPTY_FILTER = FilterBuilders.usageMetrics().build();
    private static final UsageMetricsSort EMPTY_SORT = SortOptionBuilders.usageMetrics().build();

    private UsageMetricsFilter filter;
    private UsageMetricsSort sort;

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public Builder filter(final UsageMetricsFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final UsageMetricsSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<UsageMetricsFilter.Builder, ObjectBuilder<UsageMetricsFilter>> fn) {
      return filter(FilterBuilders.usageMetrics(fn));
    }

    public Builder sort(
        final Function<UsageMetricsSort.Builder, ObjectBuilder<UsageMetricsSort>> fn) {
      return sort(SortOptionBuilders.usageMetrics(fn));
    }

    @Override
    public UsageMetricsQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new UsageMetricsQuery(filter, sort, page());
    }
  }
}
