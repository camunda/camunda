/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.ElementInstanceInspectionFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.NoSort;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record ElementInstanceInspectionQuery(
    ElementInstanceInspectionFilter filter, SearchQueryPage page)
    implements TypedSearchQuery<ElementInstanceInspectionFilter, NoSort> {

  public static ElementInstanceInspectionQuery of(
      final Function<Builder, ObjectBuilder<ElementInstanceInspectionQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  @Override
  public NoSort sort() {
    return NoSort.NO_SORT;
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          ElementInstanceInspectionQuery,
          Builder,
          ElementInstanceInspectionFilter,
          NoSort> {

    private static final ElementInstanceInspectionFilter EMPTY_FILTER =
        FilterBuilders.elementInstanceInspection().build();

    private ElementInstanceInspectionFilter filter;

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public Builder filter(final ElementInstanceInspectionFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final NoSort value) {
      return this;
    }

    public Builder filter(
        final Function<
                ElementInstanceInspectionFilter.Builder,
                ObjectBuilder<ElementInstanceInspectionFilter>>
            fn) {
      return filter(FilterBuilders.elementInstanceInspection(fn));
    }

    @Override
    public ElementInstanceInspectionQuery build() {
      return new ElementInstanceInspectionQuery(
          Objects.requireNonNullElse(filter, EMPTY_FILTER), page());
    }
  }
}
