/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.ProcessDefinitionInstanceStatisticsEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record ProcessDefinitionInstanceStatisticsDbQuery(
    ProcessInstanceFilter filter,
    List<String> authorizedResourceIds,
    List<String> authorizedTenantIds,
    DbQuerySorting<ProcessDefinitionInstanceStatisticsEntity> sort,
    DbQueryPage page) {

  public static ProcessDefinitionInstanceStatisticsDbQuery of(
      final Function<Builder, ObjectBuilder<ProcessDefinitionInstanceStatisticsDbQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder
      implements ObjectBuilder<ProcessDefinitionInstanceStatisticsDbQuery> {

    private static final ProcessInstanceFilter EMPTY_FILTER =
        FilterBuilders.processInstance().build();

    private ProcessInstanceFilter filter;
    private List<String> authorizedResourceIds = java.util.Collections.emptyList();
    private List<String> authorizedTenantIds = java.util.Collections.emptyList();
    private DbQuerySorting<ProcessDefinitionInstanceStatisticsEntity> sort;
    private DbQueryPage page;

    public Builder filter(final ProcessInstanceFilter value) {
      filter = value;
      return this;
    }

    public Builder filter(
        final Function<ProcessInstanceFilter.Builder, ObjectBuilder<ProcessInstanceFilter>> fn) {
      return filter(FilterBuilders.processInstance(fn));
    }

    public Builder authorizedResourceIds(final List<String> authorizedResourceIds) {
      this.authorizedResourceIds = authorizedResourceIds;
      return this;
    }

    public Builder authorizedTenantIds(final List<String> authorizedTenantIds) {
      this.authorizedTenantIds = authorizedTenantIds;
      return this;
    }

    public Builder sort(final DbQuerySorting<ProcessDefinitionInstanceStatisticsEntity> value) {
      sort = value;
      return this;
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<ProcessDefinitionInstanceStatisticsEntity>,
                ObjectBuilder<DbQuerySorting<ProcessDefinitionInstanceStatisticsEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    @Override
    public ProcessDefinitionInstanceStatisticsDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new ProcessDefinitionInstanceStatisticsDbQuery(
          filter, authorizedResourceIds, authorizedTenantIds, sort, page);
    }
  }
}
