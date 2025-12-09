/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import static java.util.Collections.emptyList;

import io.camunda.search.entities.ProcessDefinitionInstanceVersionStatisticsEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.ProcessDefinitionInstanceVersionStatisticsFilter;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record ProcessDefinitionInstanceVersionStatisticsDbQuery(
    ProcessDefinitionInstanceVersionStatisticsFilter filter,
    List<String> authorizedResourceIds,
    List<String> authorizedTenantIds,
    DbQuerySorting<ProcessDefinitionInstanceVersionStatisticsEntity> sort,
    DbQueryPage page) {

  public static ProcessDefinitionInstanceVersionStatisticsDbQuery of(
      final Function<Builder, ObjectBuilder<ProcessDefinitionInstanceVersionStatisticsDbQuery>>
          fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder
      implements ObjectBuilder<ProcessDefinitionInstanceVersionStatisticsDbQuery> {

    private static final ProcessDefinitionInstanceVersionStatisticsFilter EMPTY_FILTER =
        FilterBuilders.processDefinitionInstanceVersionStatistics().build();

    private ProcessDefinitionInstanceVersionStatisticsFilter filter;
    private List<String> authorizedResourceIds = emptyList();
    private List<String> authorizedTenantIds = emptyList();
    private DbQuerySorting<ProcessDefinitionInstanceVersionStatisticsEntity> sort;
    private DbQueryPage page;

    public Builder filter(final ProcessDefinitionInstanceVersionStatisticsFilter value) {
      filter = value;
      return this;
    }

    public Builder filter(
        final Function<
                ProcessDefinitionInstanceVersionStatisticsFilter.Builder,
                ObjectBuilder<ProcessDefinitionInstanceVersionStatisticsFilter>>
            fn) {
      return filter(FilterBuilders.processDefinitionInstanceVersionStatistics(fn));
    }

    public Builder authorizedResourceIds(final List<String> authorizedResourceIds) {
      this.authorizedResourceIds = authorizedResourceIds;
      return this;
    }

    public Builder authorizedTenantIds(final List<String> authorizedTenantIds) {
      this.authorizedTenantIds = authorizedTenantIds;
      return this;
    }

    public Builder sort(
        final DbQuerySorting<ProcessDefinitionInstanceVersionStatisticsEntity> value) {
      sort = value;
      return this;
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<ProcessDefinitionInstanceVersionStatisticsEntity>,
                ObjectBuilder<DbQuerySorting<ProcessDefinitionInstanceVersionStatisticsEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    @Override
    public ProcessDefinitionInstanceVersionStatisticsDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new ProcessDefinitionInstanceVersionStatisticsDbQuery(
          filter, authorizedResourceIds, authorizedTenantIds, sort, page);
    }
  }
}
