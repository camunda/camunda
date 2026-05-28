/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.ElementInstanceWaitStateFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.NoSort;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record ElementInstanceWaitStateQuery(
    ElementInstanceWaitStateFilter filter, SearchQueryPage page)
    implements TypedSearchQuery<ElementInstanceWaitStateFilter, NoSort> {

  public static ElementInstanceWaitStateQuery of(
      final Function<Builder, ObjectBuilder<ElementInstanceWaitStateQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  @Override
  public NoSort sort() {
    return NoSort.NO_SORT;
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          ElementInstanceWaitStateQuery, Builder, ElementInstanceWaitStateFilter, NoSort> {

    private static final ElementInstanceWaitStateFilter EMPTY_FILTER =
        FilterBuilders.elementInstanceWaitState().build();

    private ElementInstanceWaitStateFilter filter;

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public Builder filter(final ElementInstanceWaitStateFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final NoSort value) {
      return this;
    }

    public Builder filter(
        final Function<
                ElementInstanceWaitStateFilter.Builder,
                ObjectBuilder<ElementInstanceWaitStateFilter>>
            fn) {
      return filter(FilterBuilders.elementInstanceWaitState(fn));
    }

    @Override
    public ElementInstanceWaitStateQuery build() {
      return new ElementInstanceWaitStateQuery(
          Objects.requireNonNullElse(filter, EMPTY_FILTER), page());
    }
  }
}
