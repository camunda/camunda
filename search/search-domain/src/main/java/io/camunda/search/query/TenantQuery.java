/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.TenantFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.search.sort.TenantSort;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record TenantQuery(TenantFilter filter, TenantSort sort, SearchQueryPage page)
    implements TypedSearchQuery<TenantFilter, TenantSort> {

  public static TenantQuery of(final Function<TenantQuery.Builder, ObjectBuilder<TenantQuery>> fn) {
    return fn.apply(new TenantQuery.Builder()).build();
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<TenantQuery.Builder>
      implements TypedSearchQueryBuilder<
          TenantQuery, TenantQuery.Builder, TenantFilter, TenantSort> {

    private static final TenantFilter EMPTY_FILTER = FilterBuilders.tenant().build();
    private static final TenantSort EMPTY_SORT = SortOptionBuilders.tenant().build();

    private TenantFilter filter;
    private TenantSort sort;

    @Override
    public TenantQuery.Builder filter(final TenantFilter value) {
      filter = value;
      return this;
    }

    @Override
    public TenantQuery.Builder sort(final TenantSort value) {
      sort = value;
      return this;
    }

    public TenantQuery.Builder filter(
        final Function<TenantFilter.Builder, ObjectBuilder<TenantFilter>> fn) {
      return filter(FilterBuilders.tenant(fn));
    }

    public TenantQuery.Builder sort(
        final Function<TenantSort.Builder, ObjectBuilder<TenantSort>> fn) {
      return sort(SortOptionBuilders.tenant(fn));
    }

    @Override
    protected TenantQuery.Builder self() {
      return this;
    }

    @Override
    public TenantQuery build() {
      return new TenantQuery(
          Objects.requireNonNullElse(filter, EMPTY_FILTER),
          Objects.requireNonNullElse(sort, EMPTY_SORT),
          page());
    }
  }
}
