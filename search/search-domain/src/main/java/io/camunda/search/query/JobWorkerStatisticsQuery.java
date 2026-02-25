/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.aggregation.AggregationBase;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.JobWorkerStatisticsFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.NoSort;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

/**
 * Query for retrieving per-worker level statistics for a given job type.
 *
 * @param filter the filter criteria for job worker statistics
 * @param page pagination parameters
 */
public record JobWorkerStatisticsQuery(JobWorkerStatisticsFilter filter, SearchQueryPage page)
    implements TypedSearchQuery<JobWorkerStatisticsFilter, NoSort> {

  public static JobWorkerStatisticsQuery of(
      final Function<Builder, ObjectBuilder<JobWorkerStatisticsQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  @Override
  public NoSort sort() {
    return NoSort.NO_SORT;
  }

  @Override
  public AggregationBase aggregation() {
    // TODO enabled in a future PR, once the aggregation implementation is completed
    // return new JobWorkerStatisticsAggregation(page);
    return null;
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<JobWorkerStatisticsQuery.Builder>
      implements TypedSearchQueryBuilder<
          JobWorkerStatisticsQuery,
          JobWorkerStatisticsQuery.Builder,
          JobWorkerStatisticsFilter,
          NoSort> {

    private JobWorkerStatisticsFilter filter;
    private SearchQueryPage page;

    @Override
    public Builder filter(final JobWorkerStatisticsFilter filter) {
      this.filter = filter;
      return this;
    }

    @Override
    public Builder sort(final NoSort value) {
      return this;
    }

    public Builder filter(
        final Function<JobWorkerStatisticsFilter.Builder, ObjectBuilder<JobWorkerStatisticsFilter>>
            fn) {
      return filter(FilterBuilders.jobWorkerStatistics(fn));
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
    public JobWorkerStatisticsQuery build() {
      return new JobWorkerStatisticsQuery(
          Objects.requireNonNull(filter, "filter must not be null"), page);
    }
  }
}
