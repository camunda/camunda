/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.AgentInstanceEntity;
import io.camunda.search.filter.AgentInstanceFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record AgentInstanceDbQuery(
    AgentInstanceFilter filter,
    List<String> authorizedResourceIds,
    List<String> authorizedTenantIds,
    DbQuerySorting<AgentInstanceEntity> sort,
    DbQueryPage page) {

  public static AgentInstanceDbQuery of(
      final Function<AgentInstanceDbQuery.Builder, ObjectBuilder<AgentInstanceDbQuery>> fn) {
    return fn.apply(new AgentInstanceDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<AgentInstanceDbQuery> {

    private static final AgentInstanceFilter EMPTY_FILTER = FilterBuilders.agentInstance().build();

    private AgentInstanceFilter filter;
    private List<String> authorizedResourceIds;
    private List<String> authorizedTenantIds;
    private DbQuerySorting<AgentInstanceEntity> sort;
    private DbQueryPage page;

    public Builder filter(final AgentInstanceFilter value) {
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

    public Builder sort(final DbQuerySorting<AgentInstanceEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(
        final Function<AgentInstanceFilter.Builder, ObjectBuilder<AgentInstanceFilter>> fn) {
      return filter(FilterBuilders.agentInstance(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<AgentInstanceEntity>,
                ObjectBuilder<DbQuerySorting<AgentInstanceEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public AgentInstanceDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(Collections.emptyList()));
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new AgentInstanceDbQuery(
          filter, authorizedResourceIds, authorizedTenantIds, sort, page);
    }
  }
}
