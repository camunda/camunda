/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.IncidentStatisticsSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record IncidentStatisticsQuery(
    IncidentFilter filter, IncidentStatisticsSort sort, SearchQueryPage page)
    implements TypedSearchQuery<IncidentFilter, IncidentStatisticsSort> {

  public static IncidentStatisticsQuery of(
      final Function<Builder, ObjectBuilder<IncidentStatisticsQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          IncidentStatisticsQuery,
          IncidentStatisticsQuery.Builder,
          IncidentFilter,
          IncidentStatisticsSort> {

    private static final IncidentFilter EMPTY_FILTER = FilterBuilders.incident().build();
    private static final IncidentStatisticsSort EMPTY_SORT =
        SortOptionBuilders.incidentStatistics().build();

    private IncidentFilter filter;
    private IncidentStatisticsSort sort;

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public Builder filter(final IncidentFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final IncidentStatisticsSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<IncidentFilter.Builder, ObjectBuilder<IncidentFilter>> fn) {
      return filter(FilterBuilders.incident(fn));
    }

    public Builder sort(
        final Function<IncidentStatisticsSort.Builder, ObjectBuilder<IncidentStatisticsSort>> fn) {
      return sort(SortOptionBuilders.incidentStatistics(fn));
    }

    @Override
    public IncidentStatisticsQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new IncidentStatisticsQuery(filter, sort, page());
    }
  }
}
