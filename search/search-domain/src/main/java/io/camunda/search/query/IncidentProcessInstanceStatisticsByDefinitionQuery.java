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
import io.camunda.search.filter.IncidentProcessInstanceStatisticsByDefinitionFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.IncidentProcessInstanceStatisticsByDefinitionSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record IncidentProcessInstanceStatisticsByDefinitionQuery(
    IncidentProcessInstanceStatisticsByDefinitionFilter filter,
    IncidentProcessInstanceStatisticsByDefinitionSort sort,
    SearchQueryPage page)
    implements TypedSearchAggregationQuery<
        IncidentProcessInstanceStatisticsByDefinitionFilter,
        IncidentProcessInstanceStatisticsByDefinitionSort,
        IncidentProcessInstanceStatisticsByDefinitionAggregation> {

  public static IncidentProcessInstanceStatisticsByDefinitionQuery of(
      final Function<Builder, ObjectBuilder<IncidentProcessInstanceStatisticsByDefinitionQuery>>
          fn) {
    return fn.apply(new Builder()).build();
  }

  @Override
  public io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation aggregation() {
    return new io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation(
        filter, sort, page);
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<
          IncidentProcessInstanceStatisticsByDefinitionQuery.Builder>
      implements TypedSearchQueryBuilder<
          IncidentProcessInstanceStatisticsByDefinitionQuery,
          IncidentProcessInstanceStatisticsByDefinitionQuery.Builder,
          IncidentProcessInstanceStatisticsByDefinitionFilter,
          IncidentProcessInstanceStatisticsByDefinitionSort> {

    private static final IncidentProcessInstanceStatisticsByDefinitionFilter DEFAULT_FILTER =
        FilterBuilders.incidentProcessInstanceStatisticsByDefinition().build();

    private static final IncidentProcessInstanceStatisticsByDefinitionSort DEFAULT_SORT =
        SortOptionBuilders.incidentProcessInstanceStatisticsByDefinition().build();

    private IncidentProcessInstanceStatisticsByDefinitionFilter filter;
    private IncidentProcessInstanceStatisticsByDefinitionSort sort;

    @Override
    public Builder filter(final IncidentProcessInstanceStatisticsByDefinitionFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final IncidentProcessInstanceStatisticsByDefinitionSort value) {
      sort = value;
      return this;
    }

    public IncidentProcessInstanceStatisticsByDefinitionQuery.Builder filter(
        final java.util.function.Function<
                IncidentProcessInstanceStatisticsByDefinitionFilter.Builder,
                ObjectBuilder<IncidentProcessInstanceStatisticsByDefinitionFilter>>
            fn) {
      return filter(FilterBuilders.incidentProcessInstanceStatisticsByDefinition(fn));
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
