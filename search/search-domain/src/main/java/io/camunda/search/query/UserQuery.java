/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.UserFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.search.sort.UserSort;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record UserQuery(UserFilter filter, UserSort sort, SearchQueryPage page)
    implements TypedSearchQuery<UserFilter, UserSort> {
  public static UserQuery of(final Function<Builder, ObjectBuilder<UserQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<UserQuery, UserQuery.Builder, UserFilter, UserSort> {
    private static final UserFilter EMPTY_FILTER = FilterBuilders.user().build();
    private static final UserSort EMPTY_SORT = SortOptionBuilders.user().build();

    private UserFilter filter;
    private UserSort sort;

    @Override
    public Builder filter(final UserFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final UserSort value) {
      sort = value;
      return this;
    }

    public Builder filter(final Function<UserFilter.Builder, ObjectBuilder<UserFilter>> fn) {
      return filter(FilterBuilders.user(fn));
    }

    public Builder sort(final Function<UserSort.Builder, ObjectBuilder<UserSort>> fn) {
      return sort(SortOptionBuilders.user(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public UserQuery build() {
      return new UserQuery(
          Objects.requireNonNullElse(filter, EMPTY_FILTER),
          Objects.requireNonNullElse(sort, EMPTY_SORT),
          page());
    }
  }
}
