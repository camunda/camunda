/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.query;

import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.filter.IncidentFilter;
import io.camunda.service.search.page.SearchQueryPage;
import io.camunda.service.search.sort.IncidentSort;
import io.camunda.service.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record IncidentQuery(IncidentFilter filter, IncidentSort sort, SearchQueryPage page)
    implements TypedSearchQuery<IncidentFilter, IncidentSort> {

  public static IncidentQuery of(final Function<Builder, ObjectBuilder<IncidentQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          IncidentQuery, IncidentQuery.Builder, IncidentFilter, IncidentSort> {

    private static final IncidentFilter EMPTY_FILTER = FilterBuilders.incident().build();
    private static final IncidentSort EMPTY_SORT = SortOptionBuilders.incident().build();

    private IncidentFilter filter;
    private IncidentSort sort;

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public Builder filter(final IncidentFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final IncidentSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<IncidentFilter.Builder, ObjectBuilder<IncidentFilter>> fn) {
      return filter(FilterBuilders.incident(fn));
    }

    public Builder sort(final Function<IncidentSort.Builder, ObjectBuilder<IncidentSort>> fn) {
      return sort(SortOptionBuilders.incident(fn));
    }

    @Override
    public IncidentQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new IncidentQuery(filter, sort, page());
    }
  }
}
