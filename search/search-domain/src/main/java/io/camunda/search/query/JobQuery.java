/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.JobFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.JobSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record JobQuery(JobFilter filter, JobSort sort, SearchQueryPage page)
    implements TypedSearchQuery<JobFilter, JobSort> {

  public static JobQuery of(final Function<Builder, ObjectBuilder<JobQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<JobQuery.Builder>
      implements TypedSearchQueryBuilder<JobQuery, JobQuery.Builder, JobFilter, JobSort> {

    private static final JobFilter EMPTY_FILTER = FilterBuilders.job().build();
    private static final JobSort EMPTY_SORT = SortOptionBuilders.job().build();

    private JobFilter filter;
    private JobSort sort;

    @Override
    public Builder filter(final JobFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final JobSort value) {
      sort = value;
      return this;
    }

    public JobQuery.Builder filter(final Function<JobFilter.Builder, ObjectBuilder<JobFilter>> fn) {
      return filter(FilterBuilders.job(fn));
    }

    public JobQuery.Builder sort(final Function<JobSort.Builder, ObjectBuilder<JobSort>> fn) {
      return sort(SortOptionBuilders.job(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public JobQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new JobQuery(filter, sort, page());
    }
  }
}
