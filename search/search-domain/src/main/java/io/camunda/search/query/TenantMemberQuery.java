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
import io.camunda.search.sort.TenantMemberSort;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record TenantMemberQuery(TenantFilter filter, TenantMemberSort sort, SearchQueryPage page)
    implements TypedSearchQuery<TenantFilter, TenantMemberSort> {

  public static TenantMemberQuery of(
      final Function<TenantMemberQuery.Builder, ObjectBuilder<TenantMemberQuery>> fn) {
    return fn.apply(new TenantMemberQuery.Builder()).build();
  }

  public Builder toBuilder() {
    return new Builder().filter(filter).sort(sort).page(page);
  }

  public static final class Builder extends AbstractQueryBuilder<TenantMemberQuery.Builder>
      implements TypedSearchQueryBuilder<
          TenantMemberQuery, TenantMemberQuery.Builder, TenantFilter, TenantMemberSort> {

    private static final TenantFilter EMPTY_FILTER = FilterBuilders.tenant().build();
    private static final TenantMemberSort EMPTY_SORT = SortOptionBuilders.tenantMember().build();

    private TenantFilter filter;
    private TenantMemberSort sort;

    @Override
    public TenantMemberQuery.Builder filter(final TenantFilter value) {
      filter = value;
      return this;
    }

    @Override
    public TenantMemberQuery.Builder sort(final TenantMemberSort value) {
      sort = value;
      return this;
    }

    public TenantMemberQuery.Builder filter(
        final Function<TenantFilter.Builder, ObjectBuilder<TenantFilter>> fn) {
      return filter(FilterBuilders.tenant(fn));
    }

    public TenantMemberQuery.Builder sort(
        final Function<TenantMemberSort.Builder, ObjectBuilder<TenantMemberSort>> fn) {
      return sort(SortOptionBuilders.tenantMember(fn));
    }

    @Override
    protected TenantMemberQuery.Builder self() {
      return this;
    }

    @Override
    public TenantMemberQuery build() {
      return new TenantMemberQuery(
          Objects.requireNonNullElse(filter, EMPTY_FILTER),
          Objects.requireNonNullElse(sort, EMPTY_SORT),
          page());
    }
  }
}
