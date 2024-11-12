/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.search.query;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.search.sort.UserTaskSort;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public final record UserTaskQuery(UserTaskFilter filter, UserTaskSort sort, SearchQueryPage page)
    implements TypedSearchQuery<UserTaskFilter, UserTaskSort> {

  public static UserTaskQuery of(final Function<Builder, ObjectBuilder<UserTaskQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<UserTaskQuery, Builder, UserTaskFilter, UserTaskSort> {

    private static final UserTaskFilter EMPTY_FILTER = FilterBuilders.userTask().build();
    private static final UserTaskSort EMPTY_SORT = SortOptionBuilders.userTask().build();

    private UserTaskFilter filter;
    private UserTaskSort sort;

    @Override
    public Builder filter(final UserTaskFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final UserTaskSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<UserTaskFilter.Builder, ObjectBuilder<UserTaskFilter>> fn) {
      return filter(FilterBuilders.userTask(fn));
    }

    public Builder sort(final Function<UserTaskSort.Builder, ObjectBuilder<UserTaskSort>> fn) {
      return sort(SortOptionBuilders.userTask(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public UserTaskQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new UserTaskQuery(filter, sort, page());
    }
  }
}
