/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.filter.DecisionDefinitionFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record DecisionDefinitionDbQuery(
    DecisionDefinitionFilter filter,
    DbQuerySorting<DecisionDefinitionEntity> sort,
    DbQueryPage page) {

  public static DecisionDefinitionDbQuery of(
      final Function<Builder, ObjectBuilder<DecisionDefinitionDbQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<DecisionDefinitionDbQuery> {

    private static final DecisionDefinitionFilter EMPTY_FILTER =
        FilterBuilders.decisionDefinition().build();

    private DecisionDefinitionFilter filter;
    private DbQuerySorting<DecisionDefinitionEntity> sort;
    private DbQueryPage page;

    public DecisionDefinitionDbQuery.Builder filter(final DecisionDefinitionFilter value) {
      filter = value;
      return this;
    }

    public DecisionDefinitionDbQuery.Builder sort(
        final DbQuerySorting<DecisionDefinitionEntity> value) {
      sort = value;
      return this;
    }

    public DecisionDefinitionDbQuery.Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public DecisionDefinitionDbQuery.Builder filter(
        final Function<DecisionDefinitionFilter.Builder, ObjectBuilder<DecisionDefinitionFilter>>
            fn) {
      return filter(FilterBuilders.decisionDefinition(fn));
    }

    public DecisionDefinitionDbQuery.Builder sort(
        final Function<
                DbQuerySorting.Builder<DecisionDefinitionEntity>,
                ObjectBuilder<DbQuerySorting<DecisionDefinitionEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public DecisionDefinitionDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      return new DecisionDefinitionDbQuery(filter, sort, page);
    }
  }
}
