/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.AdHocSubprocessActivityFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.AdHocSubprocessActivitySort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record AdHocSubprocessActivityQuery(
    AdHocSubprocessActivityFilter filter, AdHocSubprocessActivitySort sort, SearchQueryPage page)
    implements TypedSearchQuery<AdHocSubprocessActivityFilter, AdHocSubprocessActivitySort> {

  public static AdHocSubprocessActivityQuery of(
      final Function<
              AdHocSubprocessActivityQuery.Builder, ObjectBuilder<AdHocSubprocessActivityQuery>>
          fn) {
    return fn.apply(new AdHocSubprocessActivityQuery.Builder()).build();
  }

  public static final class Builder extends AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          AdHocSubprocessActivityQuery,
          Builder,
          AdHocSubprocessActivityFilter,
          AdHocSubprocessActivitySort> {

    private static final AdHocSubprocessActivityFilter EMPTY_FILTER =
        FilterBuilders.adHocSubprocessActivity().build();
    private static final AdHocSubprocessActivitySort EMPTY_SORT =
        SortOptionBuilders.adHocSubprocessActivity().build();

    private AdHocSubprocessActivityFilter filter;
    private AdHocSubprocessActivitySort sort;

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public Builder filter(final AdHocSubprocessActivityFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final AdHocSubprocessActivitySort value) {
      sort = value;
      return this;
    }

    public AdHocSubprocessActivityQuery.Builder filter(
        final Function<
                AdHocSubprocessActivityFilter.Builder, ObjectBuilder<AdHocSubprocessActivityFilter>>
            fn) {
      return filter(FilterBuilders.adHocSubprocessActivity(fn));
    }

    public Builder sort(
        final Function<
                AdHocSubprocessActivitySort.Builder, ObjectBuilder<AdHocSubprocessActivitySort>>
            fn) {
      return sort(SortOptionBuilders.adHocSubprocessActivity(fn));
    }

    @Override
    public AdHocSubprocessActivityQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new AdHocSubprocessActivityQuery(filter, sort, page());
    }
  }
}
