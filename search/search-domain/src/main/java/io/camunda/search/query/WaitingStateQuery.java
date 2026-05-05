/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.WaitingStateFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.NoSort;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record WaitingStateQuery(WaitingStateFilter filter, SearchQueryPage page)
    implements TypedSearchQuery<WaitingStateFilter, NoSort> {

  public static WaitingStateQuery of(final Function<Builder, ObjectBuilder<WaitingStateQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  @Override
  public NoSort sort() {
    return NoSort.NO_SORT;
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          WaitingStateQuery, WaitingStateQuery.Builder, WaitingStateFilter, NoSort> {

    private static final WaitingStateFilter EMPTY_FILTER = new WaitingStateFilter.Builder().build();
    private WaitingStateFilter filter;

    @Override
    public Builder filter(final WaitingStateFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final NoSort value) {
      return this;
    }

    public Builder filter(
        final Function<WaitingStateFilter.Builder, ObjectBuilder<WaitingStateFilter>> fn) {
      return filter(fn.apply(new WaitingStateFilter.Builder()).build());
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public WaitingStateQuery build() {
      return new WaitingStateQuery(Objects.requireNonNullElse(filter, EMPTY_FILTER), page());
    }
  }
}
