/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.JobTypeStatisticsFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.NoSort;
import io.camunda.search.sort.SortOption;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

/**
 * Query for retrieving job type level statistics.
 *
 * @param filter the filter criteria for job type statistics
 * @param page pagination parameters
 */
public record JobTypeStatisticsQuery(JobTypeStatisticsFilter filter, SearchQueryPage page)
    implements TypedSearchQuery<JobTypeStatisticsFilter, SortOption> {

  public static JobTypeStatisticsQuery of(
      final Function<Builder, ObjectBuilder<JobTypeStatisticsQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  @Override
  public SortOption sort() {
    return NoSort.NO_SORT;
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<JobTypeStatisticsQuery.Builder>
      implements TypedSearchQueryBuilder<
          JobTypeStatisticsQuery, JobTypeStatisticsQuery.Builder, JobTypeStatisticsFilter, NoSort> {
    private JobTypeStatisticsFilter filter;
    private SearchQueryPage page;

    @Override
    public Builder filter(final JobTypeStatisticsFilter filter) {
      this.filter = filter;
      return this;
    }

    @Override
    public Builder sort(final NoSort value) {
      return this;
    }

    public Builder filter(
        final Function<JobTypeStatisticsFilter.Builder, ObjectBuilder<JobTypeStatisticsFilter>>
            fn) {
      return filter(FilterBuilders.jobTypeStatistics(fn));
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
    public JobTypeStatisticsQuery build() {
      return new JobTypeStatisticsQuery(
          Objects.requireNonNull(filter, "filter must not be null"), page);
    }
  }
}
