/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import static io.camunda.search.aggregation.ProcessDefinitionProcessInstanceStatisticsAggregation.AGGREGATION_TERMS_SIZE;

import io.camunda.search.aggregation.ProcessDefinitionProcessInstanceStatisticsAggregation;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.ProcessDefinitionProcessInstanceStatisticsSort;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record ProcessDefinitionProcessInstanceStatisticsQuery(
    ProcessInstanceFilter filter,
    ProcessDefinitionProcessInstanceStatisticsSort sort,
    SearchQueryPage page)
    implements TypedSearchAggregationQuery<
        ProcessInstanceFilter,
        ProcessDefinitionProcessInstanceStatisticsSort,
        ProcessDefinitionProcessInstanceStatisticsAggregation> {

  public static ProcessDefinitionProcessInstanceStatisticsQuery of(
      final Function<Builder, ObjectBuilder<ProcessDefinitionProcessInstanceStatisticsQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  @Override
  public ProcessDefinitionProcessInstanceStatisticsAggregation aggregation() {
    return new ProcessDefinitionProcessInstanceStatisticsAggregation(filter, sort, page);
  }

  public ProcessDefinitionProcessInstanceStatisticsQuery withConvertedSortingField(
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
      final var newSort = new ProcessDefinitionProcessInstanceStatisticsSort(updatedOrderings);
      return new ProcessDefinitionProcessInstanceStatisticsQuery(filter, newSort, page);
    }
    return this;
  }

  public ProcessDefinitionProcessInstanceStatisticsQuery withUnlimitedPage() {
    return ProcessDefinitionProcessInstanceStatisticsQuery.of(
        b ->
            b.filter(filter)
                .sort(sort)
                .page(p -> p.size(AGGREGATION_TERMS_SIZE).from(0).after(null).before(null)));
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<
          ProcessDefinitionProcessInstanceStatisticsQuery.Builder>
      implements TypedSearchQueryBuilder<
          ProcessDefinitionProcessInstanceStatisticsQuery,
          ProcessDefinitionProcessInstanceStatisticsQuery.Builder,
          ProcessInstanceFilter,
          ProcessDefinitionProcessInstanceStatisticsSort> {

    private static final ProcessInstanceFilter EMPTY_FILTER =
        FilterBuilders.processInstance().build();
    private static final ProcessDefinitionProcessInstanceStatisticsSort EMPTY_SORT =
        SortOptionBuilders.processDefinitionProcessInstanceStatistics().build();

    private ProcessInstanceFilter filter;
    private ProcessDefinitionProcessInstanceStatisticsSort sort;

    @Override
    public Builder filter(final ProcessInstanceFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final ProcessDefinitionProcessInstanceStatisticsSort value) {
      sort = value;
      return this;
    }

    public ProcessDefinitionProcessInstanceStatisticsQuery.Builder filter(
        final Function<ProcessInstanceFilter.Builder, ObjectBuilder<ProcessInstanceFilter>> fn) {
      return filter(FilterBuilders.processInstance(fn));
    }

    public ProcessDefinitionProcessInstanceStatisticsQuery.Builder sort(
        final Function<
                ProcessDefinitionProcessInstanceStatisticsSort.Builder,
                ObjectBuilder<ProcessDefinitionProcessInstanceStatisticsSort>>
            fn) {
      return sort(SortOptionBuilders.processDefinitionProcessInstanceStatistics(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public ProcessDefinitionProcessInstanceStatisticsQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new ProcessDefinitionProcessInstanceStatisticsQuery(filter, sort, page());
    }
  }
}
