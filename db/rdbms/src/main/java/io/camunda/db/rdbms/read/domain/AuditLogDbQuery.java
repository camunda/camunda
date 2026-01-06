/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.filter.AuditLogFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record AuditLogDbQuery(
    AuditLogFilter filter,
    List<String> authorizedResourceIds,
    List<String> authorizedTenantIds,
    DbQuerySorting<AuditLogEntity> sort,
    DbQueryPage page) {

  public static AuditLogDbQuery of(final Function<Builder, ObjectBuilder<AuditLogDbQuery>> fn) {
    return fn.apply(new AuditLogDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<AuditLogDbQuery> {

    private static final AuditLogFilter EMPTY_FILTER = FilterBuilders.auditLog().build();

    private AuditLogFilter filter;
    private List<String> authorizedResourceIds = List.of();
    private List<String> authorizedTenantIds = List.of();
    private DbQuerySorting<AuditLogEntity> sort;
    private DbQueryPage page;

    public Builder filter(final AuditLogFilter value) {
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

    public Builder sort(final DbQuerySorting<AuditLogEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public AuditLogDbQuery.Builder filter(
        final Function<AuditLogFilter.Builder, ObjectBuilder<AuditLogFilter>> fn) {
      return filter(FilterBuilders.auditLog(fn));
    }

    public AuditLogDbQuery.Builder sort(
        final Function<
                DbQuerySorting.Builder<AuditLogEntity>,
                ObjectBuilder<DbQuerySorting<AuditLogEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public AuditLogDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new AuditLogDbQuery(filter, authorizedResourceIds, authorizedTenantIds, sort, page);
    }
  }
}
