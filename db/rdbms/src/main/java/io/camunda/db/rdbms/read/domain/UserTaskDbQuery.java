/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.SearchQueryBase;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.search.sort.UserTaskSort;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record UserTaskDbQuery(UserTaskFilter filter, UserTaskSort sort, SearchQueryPage page) {

  public UserTaskDbQuery {
    // There should be a default in the SearchQueryPage, so this should never happen
    Objects.requireNonNull(page);
  }

  public static UserTaskDbQuery of(
      final Function<UserTaskDbQuery.Builder, ObjectBuilder<UserTaskDbQuery>> fn) {
    return fn.apply(new UserTaskDbQuery.Builder()).build();
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<UserTaskDbQuery.Builder>
      implements ObjectBuilder<UserTaskDbQuery> {

    private static final UserTaskFilter EMPTY_FILTER = FilterBuilders.userTask().build();
    private static final UserTaskSort EMPTY_SORT = SortOptionBuilders.userTask().build();

    private UserTaskFilter filter;
    private UserTaskSort sort;

    public Builder filter(final UserTaskFilter value) {
      filter = value;
      return this;
    }

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
    public UserTaskDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      final var page = page() != null ? page().sanitize() : SearchQueryPage.DEFAULT;
      return new UserTaskDbQuery(filter, sort, page);
    }
  }
}
