/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.RoleFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.RoleSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record RoleQuery(RoleFilter filter, RoleSort sort, SearchQueryPage page)
    implements TypedSearchQuery<RoleFilter, RoleSort> {
  public static RoleQuery of(final Function<RoleQuery.Builder, ObjectBuilder<RoleQuery>> fn) {
    return fn.apply(new RoleQuery.Builder()).build();
  }

  public Builder toBuilder() {
    return new Builder().filter(filter).sort(sort).page(page);
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<RoleQuery.Builder>
      implements TypedSearchQueryBuilder<RoleQuery, RoleQuery.Builder, RoleFilter, RoleSort> {
    private static final RoleFilter EMPTY_FILTER = FilterBuilders.role().build();
    private static final RoleSort EMPTY_SORT = SortOptionBuilders.role().build();

    private RoleFilter filter;
    private RoleSort sort;

    @Override
    public RoleQuery.Builder filter(final RoleFilter value) {
      filter = value;
      return this;
    }

    @Override
    public RoleQuery.Builder sort(final RoleSort value) {
      sort = value;
      return this;
    }

    public RoleQuery.Builder filter(
        final Function<RoleFilter.Builder, ObjectBuilder<RoleFilter>> fn) {
      return filter(FilterBuilders.role(fn));
    }

    public RoleQuery.Builder sort(final Function<RoleSort.Builder, ObjectBuilder<RoleSort>> fn) {
      return sort(SortOptionBuilders.role(fn));
    }

    @Override
    protected RoleQuery.Builder self() {
      return this;
    }

    @Override
    public RoleQuery build() {
      return new RoleQuery(
          Objects.requireNonNullElse(filter, EMPTY_FILTER),
          Objects.requireNonNullElse(sort, EMPTY_SORT),
          page());
    }
  }
}
