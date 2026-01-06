/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.AuditLogFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.AuditLogSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record AuditLogQuery(AuditLogFilter filter, AuditLogSort sort, SearchQueryPage page)
    implements TypedSearchQuery<AuditLogFilter, AuditLogSort> {

  public static AuditLogQuery of(
      final Function<AuditLogQuery.Builder, ObjectBuilder<AuditLogQuery>> fn) {
    return fn.apply(new AuditLogQuery.Builder()).build();
  }

  public Builder toBuilder() {
    return new Builder().filter(filter).sort(sort).page(page);
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<AuditLogQuery.Builder>
      implements TypedSearchQueryBuilder<
          AuditLogQuery, AuditLogQuery.Builder, AuditLogFilter, AuditLogSort> {
    private static final AuditLogFilter EMPTY_FILTER = FilterBuilders.auditLog().build();
    private static final AuditLogSort EMPTY_SORT = SortOptionBuilders.auditLog().build();

    private AuditLogFilter filter;
    private AuditLogSort sort;

    @Override
    public AuditLogQuery.Builder filter(final AuditLogFilter value) {
      filter = value;
      return this;
    }

    @Override
    public AuditLogQuery.Builder sort(final AuditLogSort value) {
      sort = value;
      return this;
    }

    public AuditLogQuery.Builder filter(
        final Function<AuditLogFilter.Builder, ObjectBuilder<AuditLogFilter>> fn) {
      return filter(FilterBuilders.auditLog(fn));
    }

    public AuditLogQuery.Builder sort(
        final Function<AuditLogSort.Builder, ObjectBuilder<AuditLogSort>> fn) {
      return sort(SortOptionBuilders.auditLog(fn));
    }

    @Override
    protected AuditLogQuery.Builder self() {
      return this;
    }

    @Override
    public AuditLogQuery build() {
      return new AuditLogQuery(
          Objects.requireNonNullElse(filter, EMPTY_FILTER),
          Objects.requireNonNullElse(sort, EMPTY_SORT),
          page());
    }
  }
}
