/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.TenantEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.TenantFilter;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record TenantDbQuery(
    TenantFilter filter,
    List<String> authorizedResourceIds,
    DbQuerySorting<TenantEntity> sort,
    DbQueryPage page) {

  public static TenantDbQuery of(
      final Function<TenantDbQuery.Builder, ObjectBuilder<TenantDbQuery>> fn) {
    return fn.apply(new TenantDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<TenantDbQuery> {

    private static final TenantFilter EMPTY_FILTER = FilterBuilders.tenant().build();

    private TenantFilter filter;
    private DbQuerySorting<TenantEntity> sort;
    private DbQueryPage page;
    private List<String> authorizedResourceIds = Collections.emptyList();

    public Builder filter(final TenantFilter value) {
      filter = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<TenantEntity> value) {
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

    public Builder filter(final Function<TenantFilter.Builder, ObjectBuilder<TenantFilter>> fn) {
      return filter(FilterBuilders.tenant(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<TenantEntity>, ObjectBuilder<DbQuerySorting<TenantEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public TenantDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      return new TenantDbQuery(filter, authorizedResourceIds, sort, page);
    }
  }
}
