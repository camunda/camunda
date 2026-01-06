/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.IncidentProcessInstanceStatisticsByDefinitionSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record IncidentProcessInstanceStatisticsByDefinitionQuery(
    IncidentFilter filter,
    IncidentProcessInstanceStatisticsByDefinitionSort sort,
    SearchQueryPage page)
    implements TypedSearchAggregationQuery<
        IncidentFilter,
        IncidentProcessInstanceStatisticsByDefinitionSort,
        IncidentProcessInstanceStatisticsByDefinitionAggregation> {

  public static IncidentProcessInstanceStatisticsByDefinitionQuery of(
      final Function<Builder, ObjectBuilder<IncidentProcessInstanceStatisticsByDefinitionQuery>>
          fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<
          IncidentProcessInstanceStatisticsByDefinitionQuery.Builder>
      implements TypedSearchQueryBuilder<
          IncidentProcessInstanceStatisticsByDefinitionQuery,
          IncidentProcessInstanceStatisticsByDefinitionQuery.Builder,
          IncidentFilter,
          IncidentProcessInstanceStatisticsByDefinitionSort> {

    private static final IncidentFilter DEFAULT_FILTER = FilterBuilders.incident().build();
    private static final IncidentProcessInstanceStatisticsByDefinitionSort DEFAULT_SORT =
        SortOptionBuilders.incidentProcessInstanceStatisticsByDefinition().build();

    private IncidentFilter filter;
    private IncidentProcessInstanceStatisticsByDefinitionSort sort;

    @Override
    public Builder filter(final IncidentFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final IncidentProcessInstanceStatisticsByDefinitionSort value) {
      sort = value;
      return this;
    }

    public IncidentProcessInstanceStatisticsByDefinitionQuery.Builder filter(
        final java.util.function.Function<IncidentFilter.Builder, ObjectBuilder<IncidentFilter>>
            fn) {
      return filter(FilterBuilders.incident(fn));
    }

    public IncidentProcessInstanceStatisticsByDefinitionQuery.Builder sort(
        final java.util.function.Function<
                IncidentProcessInstanceStatisticsByDefinitionSort.Builder,
                ObjectBuilder<IncidentProcessInstanceStatisticsByDefinitionSort>>
            fn) {
      return sort(SortOptionBuilders.incidentProcessInstanceStatisticsByDefinition(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public IncidentProcessInstanceStatisticsByDefinitionQuery build() {
      filter = Objects.requireNonNullElse(filter, DEFAULT_FILTER);
      sort = Objects.requireNonNullElse(sort, DEFAULT_SORT);
      return new IncidentProcessInstanceStatisticsByDefinitionQuery(filter, sort, page());
    }
  }
}
