/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.AgentInstanceHistoryFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.AgentInstanceHistorySort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public final record AgentInstanceHistoryQuery(
    AgentInstanceHistoryFilter filter, AgentInstanceHistorySort sort, SearchQueryPage page)
    implements TypedSearchQuery<AgentInstanceHistoryFilter, AgentInstanceHistorySort> {

  public static AgentInstanceHistoryQuery of(
      final Function<Builder, ObjectBuilder<AgentInstanceHistoryQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          AgentInstanceHistoryQuery,
          Builder,
          AgentInstanceHistoryFilter,
          AgentInstanceHistorySort> {

    private static final AgentInstanceHistoryFilter EMPTY_FILTER =
        FilterBuilders.agentInstanceHistory().build();
    private static final AgentInstanceHistorySort EMPTY_SORT =
        SortOptionBuilders.agentInstanceHistory().build();

    private AgentInstanceHistoryFilter filter;
    private AgentInstanceHistorySort sort;

    @Override
    public Builder filter(final AgentInstanceHistoryFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final AgentInstanceHistorySort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<
                AgentInstanceHistoryFilter.Builder, ObjectBuilder<AgentInstanceHistoryFilter>>
            fn) {
      return filter(FilterBuilders.agentInstanceHistory(fn));
    }

    public Builder sort(
        final Function<AgentInstanceHistorySort.Builder, ObjectBuilder<AgentInstanceHistorySort>>
            fn) {
      return sort(SortOptionBuilders.agentInstanceHistory(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public AgentInstanceHistoryQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new AgentInstanceHistoryQuery(filter, sort, page());
    }
  }
}
