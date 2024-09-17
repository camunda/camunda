/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.query;

import io.camunda.service.search.filter.AuthorizationFilter;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.page.SearchQueryPage;
import io.camunda.service.search.sort.AuthorizationSort;
import io.camunda.service.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record AuthorizationQuery(
    AuthorizationFilter filter, AuthorizationSort sort, SearchQueryPage page)
    implements TypedSearchQuery<AuthorizationFilter, AuthorizationSort> {
  public static AuthorizationQuery of(
      final Function<Builder, ObjectBuilder<AuthorizationQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          AuthorizationQuery, AuthorizationQuery.Builder, AuthorizationFilter, AuthorizationSort> {
    private static final AuthorizationFilter EMPTY_FILTER = FilterBuilders.authorization().build();
    private static final AuthorizationSort EMPTY_SORT = SortOptionBuilders.authorization().build();

    private AuthorizationFilter filter;
    private AuthorizationSort sort;

    @Override
    public Builder filter(final AuthorizationFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final AuthorizationSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<AuthorizationFilter.Builder, ObjectBuilder<AuthorizationFilter>> fn) {
      return filter(FilterBuilders.authorization(fn));
    }

    public Builder sort(
        final Function<AuthorizationSort.Builder, ObjectBuilder<AuthorizationSort>> fn) {
      return sort(SortOptionBuilders.authorization(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public AuthorizationQuery build() {
      return new AuthorizationQuery(
          Objects.requireNonNullElse(filter, EMPTY_FILTER),
          Objects.requireNonNullElse(sort, EMPTY_SORT),
          page());
    }
  }
}
