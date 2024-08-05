/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.query;

import io.camunda.service.search.filter.DecisionDefinitionFilter;
import io.camunda.service.search.filter.DecisionDefinitionFilter.Builder;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.page.SearchQueryPage;
import io.camunda.service.search.sort.DecisionDefinitionSort;
import io.camunda.service.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record DecisionDefinitionQuery(
    DecisionDefinitionFilter filter, DecisionDefinitionSort sort, SearchQueryPage page)
    implements TypedSearchQuery<DecisionDefinitionFilter, DecisionDefinitionSort> {

  public static DecisionDefinitionQuery of(
      final Function<Builder, ObjectBuilder<DecisionDefinitionQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          DecisionDefinitionQuery, Builder, DecisionDefinitionFilter, DecisionDefinitionSort> {

    private static final DecisionDefinitionFilter EMPTY_FILTER =
        FilterBuilders.decisionDefinition().build();
    private static final DecisionDefinitionSort EMPTY_SORT =
        SortOptionBuilders.decisionDefinition().build();

    private DecisionDefinitionFilter filter;
    private DecisionDefinitionSort sort;

    public Builder filter(final DecisionDefinitionFilter value) {
      filter = value;
      return this;
    }

    public Builder filter(
        final Function<DecisionDefinitionFilter.Builder, ObjectBuilder<DecisionDefinitionFilter>>
            fn) {
      return filter(FilterBuilders.decisionDefinition(fn));
    }

    public Builder sort(final DecisionDefinitionSort value) {
      sort = value;
      return this;
    }

    public Builder sort(
        final Function<DecisionDefinitionSort.Builder, ObjectBuilder<DecisionDefinitionSort>> fn) {
      return sort(SortOptionBuilders.decisionDefinition(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public DecisionDefinitionQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new DecisionDefinitionQuery(filter, sort, page());
    }
  }
}
