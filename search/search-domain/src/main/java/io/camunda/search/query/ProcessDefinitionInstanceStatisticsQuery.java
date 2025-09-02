/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_TERMS_SIZE;

import io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.ProcessDefinitionInstanceStatisticsSort;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record ProcessDefinitionInstanceStatisticsQuery(
    ProcessInstanceFilter filter,
    ProcessDefinitionInstanceStatisticsSort sort,
    SearchQueryPage page)
    implements TypedSearchAggregationQuery<
        ProcessInstanceFilter,
        ProcessDefinitionInstanceStatisticsSort,
        ProcessDefinitionInstanceStatisticsAggregation> {

  public static ProcessDefinitionInstanceStatisticsQuery of(
      final Function<Builder, ObjectBuilder<ProcessDefinitionInstanceStatisticsQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  @Override
  public ProcessDefinitionInstanceStatisticsAggregation aggregation() {
    return new ProcessDefinitionInstanceStatisticsAggregation(filter, sort, page);
  }

  public ProcessDefinitionInstanceStatisticsQuery withConvertedSortingField(
      final String originalField, final String convertedField) {
    if (sort != null && sort.orderings() != null) {
      final var updatedOrderings =
          sort.orderings().stream()
              .map(
                  ordering ->
                      ordering.field().equals(originalField)
                          ? new FieldSorting(convertedField, ordering.order())
                          : ordering)
              .toList();
      final var newSort = new ProcessDefinitionInstanceStatisticsSort(updatedOrderings);
      return new ProcessDefinitionInstanceStatisticsQuery(filter, newSort, page);
    }
    return this;
  }

  public ProcessDefinitionInstanceStatisticsQuery withUnlimitedPage() {
    return ProcessDefinitionInstanceStatisticsQuery.of(
        b ->
            b.filter(filter)
                .sort(sort)
                .page(p -> p.size(AGGREGATION_TERMS_SIZE).from(0).after(null).before(null)));
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<ProcessDefinitionInstanceStatisticsQuery.Builder>
      implements TypedSearchQueryBuilder<
          ProcessDefinitionInstanceStatisticsQuery,
          ProcessDefinitionInstanceStatisticsQuery.Builder,
          ProcessInstanceFilter,
          ProcessDefinitionInstanceStatisticsSort> {

    private static final ProcessInstanceFilter EMPTY_FILTER =
        FilterBuilders.processInstance().build();
    private static final ProcessDefinitionInstanceStatisticsSort EMPTY_SORT =
        SortOptionBuilders.processDefinitionInstanceStatistics().build();

    private ProcessInstanceFilter filter;
    private ProcessDefinitionInstanceStatisticsSort sort;

    @Override
    public Builder filter(final ProcessInstanceFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final ProcessDefinitionInstanceStatisticsSort value) {
      sort = value;
      return this;
    }

    public ProcessDefinitionInstanceStatisticsQuery.Builder filter(
        final Function<ProcessInstanceFilter.Builder, ObjectBuilder<ProcessInstanceFilter>> fn) {
      return filter(FilterBuilders.processInstance(fn));
    }

    public ProcessDefinitionInstanceStatisticsQuery.Builder sort(
        final Function<
                ProcessDefinitionInstanceStatisticsSort.Builder,
                ObjectBuilder<ProcessDefinitionInstanceStatisticsSort>>
            fn) {
      return sort(SortOptionBuilders.processDefinitionInstanceStatistics(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public ProcessDefinitionInstanceStatisticsQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new ProcessDefinitionInstanceStatisticsQuery(filter, sort, page());
    }
  }
}
