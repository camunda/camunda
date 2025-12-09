/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.ProcessDefinitionInstanceVersionStatisticsFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.ProcessDefinitionInstanceVersionStatisticsSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record ProcessDefinitionInstanceVersionStatisticsQuery(
    ProcessDefinitionInstanceVersionStatisticsFilter filter,
    ProcessDefinitionInstanceVersionStatisticsSort sort,
    SearchQueryPage page)
    implements TypedSearchAggregationQuery<
        ProcessDefinitionInstanceVersionStatisticsFilter,
        ProcessDefinitionInstanceVersionStatisticsSort,
        ProcessDefinitionInstanceVersionStatisticsAggregation> {

  public static ProcessDefinitionInstanceVersionStatisticsQuery of(
      final java.util.function.Function<
              Builder,
              io.camunda.util.ObjectBuilder<ProcessDefinitionInstanceVersionStatisticsQuery>>
          fn) {
    return fn.apply(new Builder()).build();
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsAggregation aggregation() {
    return new ProcessDefinitionInstanceVersionStatisticsAggregation(filter, sort, page);
  }

  public ProcessDefinitionInstanceVersionStatisticsQuery withConvertedSortingField(
      final String originalField, final String convertedField) {
    if (sort != null && sort.orderings() != null) {
      final var updatedOrderings =
          sort.orderings().stream()
              .map(
                  ordering ->
                      ordering.field().equals(originalField)
                          ? new io.camunda.search.sort.SortOption.FieldSorting(
                              convertedField, ordering.order())
                          : ordering)
              .toList();
      final var newSort = new ProcessDefinitionInstanceVersionStatisticsSort(updatedOrderings);
      return new ProcessDefinitionInstanceVersionStatisticsQuery(filter, newSort, page);
    }
    return this;
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<
          ProcessDefinitionInstanceVersionStatisticsQuery.Builder>
      implements TypedSearchQueryBuilder<
          ProcessDefinitionInstanceVersionStatisticsQuery,
          ProcessDefinitionInstanceVersionStatisticsQuery.Builder,
          ProcessDefinitionInstanceVersionStatisticsFilter,
          ProcessDefinitionInstanceVersionStatisticsSort> {

    private static final ProcessDefinitionInstanceVersionStatisticsFilter DEFAULT_FILTER =
        io.camunda.search.filter.FilterBuilders.processDefinitionInstanceVersionStatistics()
            .build();

    private static final ProcessDefinitionInstanceVersionStatisticsSort DEFAULT_SORT =
        SortOptionBuilders.processDefinitionInstanceVersionStatistics().build();

    private ProcessDefinitionInstanceVersionStatisticsFilter filter;
    private ProcessDefinitionInstanceVersionStatisticsSort sort;

    @Override
    public Builder filter(final ProcessDefinitionInstanceVersionStatisticsFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final ProcessDefinitionInstanceVersionStatisticsSort value) {
      sort = value;
      return this;
    }

    public ProcessDefinitionInstanceVersionStatisticsQuery.Builder filter(
        final Function<
                ProcessDefinitionInstanceVersionStatisticsFilter.Builder,
                ObjectBuilder<ProcessDefinitionInstanceVersionStatisticsFilter>>
            fn) {
      return filter(FilterBuilders.processDefinitionInstanceVersionStatistics(fn));
    }

    public ProcessDefinitionInstanceVersionStatisticsQuery.Builder sort(
        final Function<
                ProcessDefinitionInstanceVersionStatisticsSort.Builder,
                ObjectBuilder<ProcessDefinitionInstanceVersionStatisticsSort>>
            fn) {
      return sort(SortOptionBuilders.processDefinitionInstanceVersionStatistics(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public ProcessDefinitionInstanceVersionStatisticsQuery build() {
      filter = Objects.requireNonNullElse(filter, DEFAULT_FILTER);
      sort = Objects.requireNonNullElse(sort, DEFAULT_SORT);
      return new ProcessDefinitionInstanceVersionStatisticsQuery(filter, sort, page());
    }
  }
}
