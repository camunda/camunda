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
import io.camunda.search.sort.IncidentProcessInstanceStatisticsByErrorSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record IncidentProcessInstanceStatisticsByErrorQuery(
    IncidentFilter filter, IncidentProcessInstanceStatisticsByErrorSort sort, SearchQueryPage page)
    implements TypedSearchQuery<IncidentFilter, IncidentProcessInstanceStatisticsByErrorSort> {

  public static IncidentProcessInstanceStatisticsByErrorQuery of(
      final Function<Builder, ObjectBuilder<IncidentProcessInstanceStatisticsByErrorQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          IncidentProcessInstanceStatisticsByErrorQuery,
          IncidentProcessInstanceStatisticsByErrorQuery.Builder,
          IncidentFilter,
          IncidentProcessInstanceStatisticsByErrorSort> {

    private static final IncidentFilter EMPTY_FILTER = FilterBuilders.incident().build();
    private static final IncidentProcessInstanceStatisticsByErrorSort EMPTY_SORT =
        SortOptionBuilders.incidentProcessInstanceStatisticsByError().build();

    private IncidentFilter filter;
    private IncidentProcessInstanceStatisticsByErrorSort sort;

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
    public Builder sort(final IncidentProcessInstanceStatisticsByErrorSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<IncidentFilter.Builder, ObjectBuilder<IncidentFilter>> fn) {
      return filter(FilterBuilders.incident(fn));
    }

    public Builder sort(
        final Function<
                IncidentProcessInstanceStatisticsByErrorSort.Builder,
                ObjectBuilder<IncidentProcessInstanceStatisticsByErrorSort>>
            fn) {
      return sort(SortOptionBuilders.incidentProcessInstanceStatisticsByError(fn));
    }

    @Override
    public IncidentProcessInstanceStatisticsByErrorQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new IncidentProcessInstanceStatisticsByErrorQuery(filter, sort, page());
    }
  }
}
