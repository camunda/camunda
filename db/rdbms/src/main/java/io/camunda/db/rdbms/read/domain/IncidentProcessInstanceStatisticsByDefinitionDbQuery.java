/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import static java.util.Collections.emptyList;

import io.camunda.search.entities.IncidentProcessInstanceStatisticsByDefinitionEntity;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record IncidentProcessInstanceStatisticsByDefinitionDbQuery(
    Integer errorMessageHash,
    List<String> authorizedResourceIds,
    List<String> authorizedTenantIds,
    DbQuerySorting<IncidentProcessInstanceStatisticsByDefinitionEntity> sort,
    DbQueryPage page) {

  public static IncidentProcessInstanceStatisticsByDefinitionDbQuery of(
      final Function<Builder, ObjectBuilder<IncidentProcessInstanceStatisticsByDefinitionDbQuery>>
          fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder
      implements ObjectBuilder<IncidentProcessInstanceStatisticsByDefinitionDbQuery> {

    private Integer errorMessageHash;
    private List<String> authorizedResourceIds = emptyList();
    private List<String> authorizedTenantIds = emptyList();
    private DbQuerySorting<IncidentProcessInstanceStatisticsByDefinitionEntity> sort;
    private DbQueryPage page;

    public Builder errorMessageHash(final Integer value) {
      errorMessageHash = value;
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

    public Builder sort(
        final DbQuerySorting<IncidentProcessInstanceStatisticsByDefinitionEntity> value) {
      sort = value;
      return this;
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<IncidentProcessInstanceStatisticsByDefinitionEntity>,
                ObjectBuilder<DbQuerySorting<IncidentProcessInstanceStatisticsByDefinitionEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    @Override
    public IncidentProcessInstanceStatisticsByDefinitionDbQuery build() {
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new IncidentProcessInstanceStatisticsByDefinitionDbQuery(
          errorMessageHash, authorizedResourceIds, authorizedTenantIds, sort, page);
    }
  }
}
