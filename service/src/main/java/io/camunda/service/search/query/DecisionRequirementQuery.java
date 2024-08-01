/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.query;

import io.camunda.service.search.filter.DecisionRequirementFilter;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.page.SearchQueryPage;
import io.camunda.service.search.sort.DecisionRequirementSort;
import io.camunda.service.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record DecisionRequirementQuery(
    DecisionRequirementFilter filter, DecisionRequirementSort sort, SearchQueryPage page)
    implements TypedSearchQuery<DecisionRequirementFilter, DecisionRequirementSort> {

  public static DecisionRequirementQuery of(
      final Function<Builder, ObjectBuilder<DecisionRequirementQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          DecisionRequirementQuery, Builder, DecisionRequirementFilter, DecisionRequirementSort> {

    private static final DecisionRequirementFilter EMPTY_FILTER =
        FilterBuilders.decisionRequirement().build();
    private static final DecisionRequirementSort EMPTY_SORT =
        SortOptionBuilders.decisionRequirement().build();

    private DecisionRequirementFilter filter;
    private DecisionRequirementSort sort;

    @Override
    public Builder filter(final DecisionRequirementFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final DecisionRequirementSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<DecisionRequirementFilter.Builder, ObjectBuilder<DecisionRequirementFilter>>
            fn) {
      return filter(FilterBuilders.decisionRequirement(fn));
    }

    public Builder sort(
        final Function<DecisionRequirementSort.Builder, ObjectBuilder<DecisionRequirementSort>>
            fn) {
      return sort(SortOptionBuilders.decisionRequirement(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public DecisionRequirementQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new DecisionRequirementQuery(filter, sort, page());
    }
  }
}
