/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record ProcessInstanceDbQuery(
    ProcessInstanceFilter filter, DbQuerySorting<ProcessInstanceEntity> sort, DbQueryPage page) {

  public static ProcessInstanceDbQuery of(
      final Function<ProcessInstanceDbQuery.Builder, ObjectBuilder<ProcessInstanceDbQuery>> fn) {
    return fn.apply(new ProcessInstanceDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<ProcessInstanceDbQuery> {

    private static final ProcessInstanceFilter EMPTY_FILTER =
        FilterBuilders.processInstance().build();

    private ProcessInstanceFilter filter;
    private DbQuerySorting<ProcessInstanceEntity> sort;
    private DbQueryPage page;

    public Builder filter(final ProcessInstanceFilter value) {
      filter = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<ProcessInstanceEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(
        final Function<ProcessInstanceFilter.Builder, ObjectBuilder<ProcessInstanceFilter>> fn) {
      return filter(FilterBuilders.processInstance(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<ProcessInstanceEntity>,
                ObjectBuilder<DbQuerySorting<ProcessInstanceEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public ProcessInstanceDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      return new ProcessInstanceDbQuery(filter, sort, page);
    }
  }
}
