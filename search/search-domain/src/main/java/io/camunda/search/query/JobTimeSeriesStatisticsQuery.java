/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.JobTimeSeriesStatisticsFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.NoSort;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

/**
 * Query for retrieving time-bucketed job statistics for a given job type.
 *
 * @param filter the filter criteria for the time-series statistics
 * @param page pagination parameters
 */
public record JobTimeSeriesStatisticsQuery(
    JobTimeSeriesStatisticsFilter filter, SearchQueryPage page)
    implements TypedSearchQuery<JobTimeSeriesStatisticsFilter, NoSort> {

  public static JobTimeSeriesStatisticsQuery of(
      final Function<Builder, ObjectBuilder<JobTimeSeriesStatisticsQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  @Override
  public NoSort sort() {
    return NoSort.NO_SORT;
  }

  @Override
  public io.camunda.search.aggregation.AggregationBase aggregation() {
    return null;
  }

  @Override
  public SearchQueryPage page() {
    return page;
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<JobTimeSeriesStatisticsQuery.Builder>
      implements TypedSearchQueryBuilder<
          JobTimeSeriesStatisticsQuery,
          JobTimeSeriesStatisticsQuery.Builder,
          JobTimeSeriesStatisticsFilter,
          NoSort> {

    private JobTimeSeriesStatisticsFilter filter;
    private SearchQueryPage page;

    @Override
    public Builder filter(final JobTimeSeriesStatisticsFilter filter) {
      this.filter = filter;
      return this;
    }

    @Override
    public Builder sort(final NoSort value) {
      return this;
    }

    public Builder filter(
        final Function<
                JobTimeSeriesStatisticsFilter.Builder, ObjectBuilder<JobTimeSeriesStatisticsFilter>>
            fn) {
      return filter(FilterBuilders.jobTimeSeriesStatistics(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public Builder page(final SearchQueryPage page) {
      this.page = page;
      return this;
    }

    @Override
    public Builder page(
        final Function<SearchQueryPage.Builder, ObjectBuilder<SearchQueryPage>> fn) {
      return page(SearchQueryPage.of(fn));
    }

    @Override
    public JobTimeSeriesStatisticsQuery build() {
      return new JobTimeSeriesStatisticsQuery(
          Objects.requireNonNull(filter, "filter must not be null"), page);
    }
  }
}
