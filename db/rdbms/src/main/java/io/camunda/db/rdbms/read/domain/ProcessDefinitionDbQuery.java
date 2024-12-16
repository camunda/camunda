/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record ProcessDefinitionDbQuery(
    ProcessDefinitionFilter filter,
    DbQuerySorting<ProcessDefinitionEntity> sort,
    DbQueryPage page) {

  public static ProcessDefinitionDbQuery of(
      final Function<Builder, ObjectBuilder<ProcessDefinitionDbQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<ProcessDefinitionDbQuery> {

    private static final ProcessDefinitionFilter EMPTY_FILTER =
        FilterBuilders.processDefinition().build();

    private ProcessDefinitionFilter filter;
    private DbQuerySorting<ProcessDefinitionEntity> sort;
    private DbQueryPage page;

    public ProcessDefinitionDbQuery.Builder filter(final ProcessDefinitionFilter value) {
      filter = value;
      return this;
    }

    public ProcessDefinitionDbQuery.Builder sort(
        final DbQuerySorting<ProcessDefinitionEntity> value) {
      sort = value;
      return this;
    }

    public ProcessDefinitionDbQuery.Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public ProcessDefinitionDbQuery.Builder filter(
        final Function<ProcessDefinitionFilter.Builder, ObjectBuilder<ProcessDefinitionFilter>>
            fn) {
      return filter(FilterBuilders.processDefinition(fn));
    }

    public ProcessDefinitionDbQuery.Builder sort(
        final Function<
                DbQuerySorting.Builder<ProcessDefinitionEntity>,
                ObjectBuilder<DbQuerySorting<ProcessDefinitionEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public ProcessDefinitionDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      return new ProcessDefinitionDbQuery(filter, sort, page);
    }
  }
}
