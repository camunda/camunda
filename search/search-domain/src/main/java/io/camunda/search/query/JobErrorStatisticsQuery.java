/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.aggregation.JobErrorStatisticsAggregation;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.JobErrorStatisticsFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.NoSort;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

/**
 * Query for retrieving per-error statistics aggregated over jobs.
 *
 * @param filter the filter criteria
 * @param page pagination parameters
 */
public record JobErrorStatisticsQuery(JobErrorStatisticsFilter filter, SearchQueryPage page)
    implements TypedSearchAggregationQuery<
        JobErrorStatisticsFilter, NoSort, JobErrorStatisticsAggregation> {

  public static JobErrorStatisticsQuery of(
      final Function<Builder, ObjectBuilder<JobErrorStatisticsQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  @Override
  public NoSort sort() {
    return NoSort.NO_SORT;
  }

  @Override
  public JobErrorStatisticsAggregation aggregation() {
    return new JobErrorStatisticsAggregation(page);
  }

  @Override
  public SearchQueryPage page() {
    return page;
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<JobErrorStatisticsQuery.Builder>
      implements TypedSearchQueryBuilder<
          JobErrorStatisticsQuery,
          JobErrorStatisticsQuery.Builder,
          JobErrorStatisticsFilter,
          NoSort> {

    private JobErrorStatisticsFilter filter;
    private SearchQueryPage page;

    @Override
    public Builder filter(final JobErrorStatisticsFilter filter) {
      this.filter = filter;
      return this;
    }

    @Override
    public Builder sort(final NoSort value) {
      return this;
    }

    public Builder filter(
        final Function<JobErrorStatisticsFilter.Builder, ObjectBuilder<JobErrorStatisticsFilter>>
            fn) {
      return filter(FilterBuilders.jobErrorStatistics(fn));
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
    public JobErrorStatisticsQuery build() {
      return new JobErrorStatisticsQuery(
          Objects.requireNonNull(filter, "filter must not be null"), page);
    }
  }
}
