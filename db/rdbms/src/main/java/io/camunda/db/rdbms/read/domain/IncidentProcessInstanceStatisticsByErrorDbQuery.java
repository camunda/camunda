/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import static java.util.Collections.emptyList;

import io.camunda.search.entities.IncidentProcessInstanceStatisticsByErrorEntity;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record IncidentProcessInstanceStatisticsByErrorDbQuery(
    List<String> authorizedResourceIds,
    List<String> authorizedTenantIds,
    DbQuerySorting<IncidentProcessInstanceStatisticsByErrorEntity> sort,
    DbQueryPage page) {

  public static IncidentProcessInstanceStatisticsByErrorDbQuery of(
      final Function<Builder, ObjectBuilder<IncidentProcessInstanceStatisticsByErrorDbQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder
      implements ObjectBuilder<IncidentProcessInstanceStatisticsByErrorDbQuery> {

    private List<String> authorizedResourceIds = emptyList();
    private List<String> authorizedTenantIds = emptyList();
    private DbQuerySorting<IncidentProcessInstanceStatisticsByErrorEntity> sort;
    private DbQueryPage page;

    public Builder authorizedResourceIds(final List<String> authorizedResourceIds) {
      this.authorizedResourceIds = authorizedResourceIds;
      return this;
    }

    public Builder authorizedTenantIds(final List<String> authorizedTenantIds) {
      this.authorizedTenantIds = authorizedTenantIds;
      return this;
    }

    public Builder sort(
        final DbQuerySorting<IncidentProcessInstanceStatisticsByErrorEntity> value) {
      sort = value;
      return this;
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<IncidentProcessInstanceStatisticsByErrorEntity>,
                ObjectBuilder<DbQuerySorting<IncidentProcessInstanceStatisticsByErrorEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    @Override
    public IncidentProcessInstanceStatisticsByErrorDbQuery build() {
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new IncidentProcessInstanceStatisticsByErrorDbQuery(
          authorizedResourceIds, authorizedTenantIds, sort, page);
    }
  }
}

