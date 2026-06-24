/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record IncidentDbQuery(
    IncidentFilter filter,
    List<String> authorizedResourceIds,
    List<String> authorizedTenantIds,
    DbQuerySorting<IncidentEntity> sort,
    DbQueryPage page) {

  public static IncidentDbQuery of(
      final Function<IncidentDbQuery.Builder, ObjectBuilder<IncidentDbQuery>> fn) {
    return fn.apply(new IncidentDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<IncidentDbQuery> {

    private static final IncidentFilter EMPTY_FILTER = FilterBuilders.incident().build();

    private IncidentFilter filter;
    private List<String> authorizedResourceIds = List.of();
    private List<String> authorizedTenantIds = List.of();
    private DbQuerySorting<IncidentEntity> sort;
    private DbQueryPage page;

    public Builder filter(final IncidentFilter value) {
      filter = value;
      return this;
    }

    public Builder authorizedResourceIds(final List<String> authorizedResourceIds) {
      this.authorizedResourceIds = authorizedResourceIds;
      return this;
    }

    public Builder authorizedTenantIds(final List<String> authorizedTenantIds) {
      this.authorizedTenantIds = authorizedTenantIds;
      return this;
    }

    public Builder sort(final DbQuerySorting<IncidentEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(
        final Function<IncidentFilter.Builder, ObjectBuilder<IncidentFilter>> fn) {
      return filter(FilterBuilders.incident(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<IncidentEntity>,
                ObjectBuilder<DbQuerySorting<IncidentEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public IncidentDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new IncidentDbQuery(filter, authorizedResourceIds, authorizedTenantIds, sort, page);
    }
  }
}
