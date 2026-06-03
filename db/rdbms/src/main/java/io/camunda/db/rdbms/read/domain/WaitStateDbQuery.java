/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.WaitStateEntity;
import io.camunda.search.filter.ElementInstanceWaitStateFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record WaitStateDbQuery(
    ElementInstanceWaitStateFilter filter,
    List<String> authorizedTenantIds,
    DbQuerySorting<WaitStateEntity> sort,
    DbQueryPage page) {

  public static WaitStateDbQuery of(
      final Function<WaitStateDbQuery.Builder, ObjectBuilder<WaitStateDbQuery>> fn) {
    return fn.apply(new WaitStateDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<WaitStateDbQuery> {

    private static final ElementInstanceWaitStateFilter EMPTY_FILTER =
        FilterBuilders.elementInstanceWaitState().build();

    private ElementInstanceWaitStateFilter filter;
    private List<String> authorizedTenantIds;
    private DbQuerySorting<WaitStateEntity> sort;
    private DbQueryPage page;

    public Builder filter(final ElementInstanceWaitStateFilter value) {
      filter = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<WaitStateEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder authorizedTenantIds(final List<String> value) {
      authorizedTenantIds = value;
      return this;
    }

    @Override
    public WaitStateDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new WaitStateDbQuery(filter, authorizedTenantIds, sort, page);
    }
  }
}
