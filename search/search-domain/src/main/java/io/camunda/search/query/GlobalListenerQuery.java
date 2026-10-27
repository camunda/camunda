/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.search.query;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.GlobalListenerFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.GlobalListenerSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public final record GlobalListenerQuery(
    GlobalListenerFilter filter, GlobalListenerSort sort, SearchQueryPage page)
    implements TypedSearchQuery<GlobalListenerFilter, GlobalListenerSort> {

  public static GlobalListenerQuery of(
      final Function<Builder, ObjectBuilder<GlobalListenerQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          GlobalListenerQuery, Builder, GlobalListenerFilter, GlobalListenerSort> {

    private static final GlobalListenerFilter EMPTY_FILTER =
        FilterBuilders.globalListener().build();
    private static final GlobalListenerSort EMPTY_SORT =
        SortOptionBuilders.globalListener().build();

    private GlobalListenerFilter filter;
    private GlobalListenerSort sort;

    @Override
    public Builder filter(final GlobalListenerFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final GlobalListenerSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<GlobalListenerFilter.Builder, ObjectBuilder<GlobalListenerFilter>> fn) {
      return filter(FilterBuilders.globalListener(fn));
    }

    public Builder sort(
        final Function<GlobalListenerSort.Builder, ObjectBuilder<GlobalListenerSort>> fn) {
      return sort(SortOptionBuilders.globalListener(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public GlobalListenerQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new GlobalListenerQuery(filter, sort, page());
    }
  }
}
