/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.AgentDefinitionFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.AgentDefinitionSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record AgentDefinitionQuery(
    AgentDefinitionFilter filter, AgentDefinitionSort sort, SearchQueryPage page)
    implements TypedSearchQuery<AgentDefinitionFilter, AgentDefinitionSort> {

  public static AgentDefinitionQuery of(
      final Function<Builder, ObjectBuilder<AgentDefinitionQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          AgentDefinitionQuery, Builder, AgentDefinitionFilter, AgentDefinitionSort> {

    private static final AgentDefinitionFilter EMPTY_FILTER =
        FilterBuilders.agentDefinition().build();
    private static final AgentDefinitionSort EMPTY_SORT =
        SortOptionBuilders.agentDefinition().build();

    private AgentDefinitionFilter filter;
    private AgentDefinitionSort sort;

    @Override
    public Builder filter(final AgentDefinitionFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final AgentDefinitionSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<AgentDefinitionFilter.Builder, ObjectBuilder<AgentDefinitionFilter>> fn) {
      return filter(FilterBuilders.agentDefinition(fn));
    }

    public Builder sort(
        final Function<AgentDefinitionSort.Builder, ObjectBuilder<AgentDefinitionSort>> fn) {
      return sort(SortOptionBuilders.agentDefinition(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public AgentDefinitionQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new AgentDefinitionQuery(filter, sort, page());
    }
  }
}
