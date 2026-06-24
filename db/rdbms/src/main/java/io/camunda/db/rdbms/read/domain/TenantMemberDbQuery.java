/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.TenantMemberEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.TenantMemberFilter;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record TenantMemberDbQuery(
    TenantMemberFilter filter,
    List<String> authorizedResourceIds,
    DbQuerySorting<TenantMemberEntity> sort,
    DbQueryPage page) {

  public static TenantMemberDbQuery of(
      final Function<TenantMemberDbQuery.Builder, ObjectBuilder<TenantMemberDbQuery>> fn) {
    return fn.apply(new TenantMemberDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<TenantMemberDbQuery> {

    private static final TenantMemberFilter EMPTY_FILTER = FilterBuilders.tenantMember().build();

    private TenantMemberFilter filter;
    private DbQuerySorting<TenantMemberEntity> sort;
    private DbQueryPage page;
    private List<String> authorizedResourceIds = Collections.emptyList();

    public Builder filter(final TenantMemberFilter value) {
      filter = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<TenantMemberEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder authorizedResourceIds(final List<String> authorizedResourceIds) {
      this.authorizedResourceIds = authorizedResourceIds;
      return this;
    }

    public Builder filter(
        final Function<TenantMemberFilter.Builder, ObjectBuilder<TenantMemberFilter>> fn) {
      return filter(FilterBuilders.tenantMember(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<TenantMemberEntity>,
                ObjectBuilder<DbQuerySorting<TenantMemberEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public TenantMemberDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      return new TenantMemberDbQuery(filter, authorizedResourceIds, sort, page);
    }
  }
}
