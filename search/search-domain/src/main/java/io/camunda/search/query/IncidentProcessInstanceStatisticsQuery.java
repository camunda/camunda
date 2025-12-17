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
import io.camunda.search.sort.IncidentProcessInstanceStatisticsSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record IncidentProcessInstanceStatisticsQuery(
    IncidentFilter filter, IncidentProcessInstanceStatisticsSort sort, SearchQueryPage page)
    implements TypedSearchQuery<IncidentFilter, IncidentProcessInstanceStatisticsSort> {

  public static IncidentProcessInstanceStatisticsQuery of(
      final Function<Builder, ObjectBuilder<IncidentProcessInstanceStatisticsQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          IncidentProcessInstanceStatisticsQuery,
          IncidentProcessInstanceStatisticsQuery.Builder,
          IncidentFilter,
          IncidentProcessInstanceStatisticsSort> {

    private static final IncidentFilter EMPTY_FILTER = FilterBuilders.incident().build();
    private static final IncidentProcessInstanceStatisticsSort EMPTY_SORT =
        SortOptionBuilders.incidentProcessInstanceStatistics().build();

    private IncidentFilter filter;
    private IncidentProcessInstanceStatisticsSort sort;

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
    public Builder sort(final IncidentProcessInstanceStatisticsSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<IncidentFilter.Builder, ObjectBuilder<IncidentFilter>> fn) {
      return filter(FilterBuilders.incident(fn));
    }

    public Builder sort(
        final Function<
                IncidentProcessInstanceStatisticsSort.Builder,
                ObjectBuilder<IncidentProcessInstanceStatisticsSort>>
            fn) {
      return sort(SortOptionBuilders.incidentProcessInstanceStatistics(fn));
    }

    @Override
    public IncidentProcessInstanceStatisticsQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new IncidentProcessInstanceStatisticsQuery(filter, sort, page());
    }
  }
}
