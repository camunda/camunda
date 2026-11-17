/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.AgentDefinitionEntity;
import io.camunda.search.filter.AgentDefinitionFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record AgentDefinitionDbQuery(
    AgentDefinitionFilter filter,
    List<String> authorizedResourceIds,
    List<String> authorizedTenantIds,
    DbQuerySorting<AgentDefinitionEntity> sort,
    DbQueryPage page) {

  public static AgentDefinitionDbQuery of(
      final Function<AgentDefinitionDbQuery.Builder, ObjectBuilder<AgentDefinitionDbQuery>> fn) {
    return fn.apply(new AgentDefinitionDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<AgentDefinitionDbQuery> {

    private static final AgentDefinitionFilter EMPTY_FILTER =
        FilterBuilders.agentDefinition().build();

    private AgentDefinitionFilter filter;
    private List<String> authorizedResourceIds;
    private List<String> authorizedTenantIds;
    private DbQuerySorting<AgentDefinitionEntity> sort;
    private DbQueryPage page;

    public Builder filter(final AgentDefinitionFilter value) {
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

    public Builder sort(final DbQuerySorting<AgentDefinitionEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(
        final Function<AgentDefinitionFilter.Builder, ObjectBuilder<AgentDefinitionFilter>> fn) {
      return filter(FilterBuilders.agentDefinition(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<AgentDefinitionEntity>,
                ObjectBuilder<DbQuerySorting<AgentDefinitionEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public AgentDefinitionDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(Collections.emptyList()));
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new AgentDefinitionDbQuery(
          filter, authorizedResourceIds, authorizedTenantIds, sort, page);
    }
  }
}
