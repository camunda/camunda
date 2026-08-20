/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.aggregation.VariableNameAggregation;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.NoSort;
import io.camunda.search.sort.SortOption;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record VariableNameQuery(VariableFilter filter, SearchQueryPage page)
    implements TypedSearchAggregationQuery<VariableFilter, SortOption, VariableNameAggregation> {

  public static VariableNameQuery of(final Function<Builder, ObjectBuilder<VariableNameQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  @Override
  public SortOption sort() {
    return NoSort.NO_SORT;
  }

  @Override
  public VariableNameAggregation aggregation() {
    return new VariableNameAggregation(filter, page);
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<VariableNameQuery, Builder, VariableFilter, SortOption> {

    private static final VariableFilter EMPTY_FILTER = FilterBuilders.variable().build();

    private VariableFilter filter;

    @Override
    public Builder filter(final VariableFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final SortOption value) {
      return this;
    }

    public Builder filter(
        final Function<VariableFilter.Builder, ObjectBuilder<VariableFilter>> fn) {
      return filter(FilterBuilders.variable(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public VariableNameQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      return new VariableNameQuery(filter, page());
    }
  }
}
