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
import io.camunda.search.sort.RoleMemberSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record RoleMemberQuery(RoleFilter filter, RoleMemberSort sort, SearchQueryPage page)
    implements TypedSearchQuery<RoleFilter, RoleMemberSort> {
  public static RoleMemberQuery of(
      final Function<RoleMemberQuery.Builder, ObjectBuilder<RoleMemberQuery>> fn) {
    return fn.apply(new RoleMemberQuery.Builder()).build();
  }

  public Builder toBuilder() {
    return new Builder().filter(filter).sort(sort).page(page);
  }

  public static final class Builder extends AbstractQueryBuilder<RoleMemberQuery.Builder>
      implements TypedSearchQueryBuilder<
          RoleMemberQuery, RoleMemberQuery.Builder, RoleFilter, RoleMemberSort> {
    private static final RoleFilter EMPTY_FILTER = FilterBuilders.role().build();
    private static final RoleMemberSort EMPTY_SORT = SortOptionBuilders.roleMember().build();

    private RoleFilter filter;
    private RoleMemberSort sort;

    @Override
    public RoleMemberQuery.Builder filter(final RoleFilter value) {
      filter = value;
      return this;
    }

    @Override
    public RoleMemberQuery.Builder sort(final RoleMemberSort value) {
      sort = value;
      return this;
    }

    public RoleMemberQuery.Builder filter(
        final Function<RoleFilter.Builder, ObjectBuilder<RoleFilter>> fn) {
      return filter(FilterBuilders.role(fn));
    }

    public RoleMemberQuery.Builder sort(
        final Function<RoleMemberSort.Builder, ObjectBuilder<RoleMemberSort>> fn) {
      return sort(SortOptionBuilders.roleMember(fn));
    }

    @Override
    protected RoleMemberQuery.Builder self() {
      return this;
    }

    @Override
    public RoleMemberQuery build() {
      return new RoleMemberQuery(
          Objects.requireNonNullElse(filter, EMPTY_FILTER),
          Objects.requireNonNullElse(sort, EMPTY_SORT),
          page());
    }
  }
}
