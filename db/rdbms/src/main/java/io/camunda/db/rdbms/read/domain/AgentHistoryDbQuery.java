/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.AgentInstanceHistoryEntity;
import io.camunda.search.filter.AgentInstanceHistoryFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record AgentHistoryDbQuery(
    AgentInstanceHistoryFilter filter,
    List<String> authorizedResourceIds,
    List<String> authorizedTenantIds,
    DbQuerySorting<AgentInstanceHistoryEntity> sort,
    DbQueryPage page) {

  public static AgentHistoryDbQuery of(
      final Function<AgentHistoryDbQuery.Builder, ObjectBuilder<AgentHistoryDbQuery>> fn) {
    return fn.apply(new AgentHistoryDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<AgentHistoryDbQuery> {

    private static final AgentInstanceHistoryFilter EMPTY_FILTER =
        FilterBuilders.agentInstanceHistory().build();

    private AgentInstanceHistoryFilter filter;
    private List<String> authorizedResourceIds;
    private List<String> authorizedTenantIds;
    private DbQuerySorting<AgentInstanceHistoryEntity> sort;
    private DbQueryPage page;

    public Builder filter(final AgentInstanceHistoryFilter value) {
      filter = value;
      return this;
    }

    public Builder authorizedResourceIds(final List<String> value) {
      authorizedResourceIds = value;
      return this;
    }

    public Builder authorizedTenantIds(final List<String> value) {
      authorizedTenantIds = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<AgentInstanceHistoryEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(
        final Function<
                AgentInstanceHistoryFilter.Builder, ObjectBuilder<AgentInstanceHistoryFilter>>
            fn) {
      return filter(FilterBuilders.agentInstanceHistory(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<AgentInstanceHistoryEntity>,
                ObjectBuilder<DbQuerySorting<AgentInstanceHistoryEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public AgentHistoryDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(Collections.emptyList()));
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new AgentHistoryDbQuery(
          filter, authorizedResourceIds, authorizedTenantIds, sort, page);
    }
  }
}
