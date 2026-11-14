/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.VariableHistoryItemFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.search.sort.VariableHistoryItemSort;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public final record VariableHistoryItemQuery(
    VariableHistoryItemFilter filter, VariableHistoryItemSort sort, SearchQueryPage page)
    implements TypedSearchQuery<VariableHistoryItemFilter, VariableHistoryItemSort> {

  public static VariableHistoryItemQuery of(
      final Function<Builder, ObjectBuilder<VariableHistoryItemQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          VariableHistoryItemQuery, Builder, VariableHistoryItemFilter, VariableHistoryItemSort> {

    private static final VariableHistoryItemFilter EMPTY_FILTER =
        FilterBuilders.variableHistoryItem().build();
    private static final VariableHistoryItemSort EMPTY_SORT =
        SortOptionBuilders.variableHistoryItem().build();

    private VariableHistoryItemFilter filter;
    private VariableHistoryItemSort sort;

    @Override
    public Builder filter(final VariableHistoryItemFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final VariableHistoryItemSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<VariableHistoryItemFilter.Builder, ObjectBuilder<VariableHistoryItemFilter>>
            fn) {
      return filter(FilterBuilders.variableHistoryItem(fn));
    }

    public Builder sort(
        final Function<VariableHistoryItemSort.Builder, ObjectBuilder<VariableHistoryItemSort>>
            fn) {
      return sort(SortOptionBuilders.variableHistoryItem(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public VariableHistoryItemQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new VariableHistoryItemQuery(filter, sort, page());
    }
  }
}
