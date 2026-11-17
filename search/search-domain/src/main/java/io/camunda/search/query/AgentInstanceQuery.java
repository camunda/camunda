/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.AgentInstanceFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.AgentInstanceSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public final record AgentInstanceQuery(
    AgentInstanceFilter filter, AgentInstanceSort sort, SearchQueryPage page)
    implements TypedSearchQuery<AgentInstanceFilter, AgentInstanceSort> {

  public static AgentInstanceQuery of(
      final Function<Builder, ObjectBuilder<AgentInstanceQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          AgentInstanceQuery, Builder, AgentInstanceFilter, AgentInstanceSort> {

    private static final AgentInstanceFilter EMPTY_FILTER = FilterBuilders.agentInstance().build();
    private static final AgentInstanceSort EMPTY_SORT = SortOptionBuilders.agentInstance().build();

    private AgentInstanceFilter filter;
    private AgentInstanceSort sort;

    @Override
    public Builder filter(final AgentInstanceFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final AgentInstanceSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<AgentInstanceFilter.Builder, ObjectBuilder<AgentInstanceFilter>> fn) {
      return filter(FilterBuilders.agentInstance(fn));
    }

    public Builder sort(
        final Function<AgentInstanceSort.Builder, ObjectBuilder<AgentInstanceSort>> fn) {
      return sort(SortOptionBuilders.agentInstance(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public AgentInstanceQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new AgentInstanceQuery(filter, sort, page());
    }
  }
}
