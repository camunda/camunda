/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record DecisionInstanceDbQuery(
    DecisionInstanceFilter filter, DbQuerySorting<DecisionInstanceEntity> sort, DbQueryPage page) {

  public static DecisionInstanceDbQuery of(
      final Function<DecisionInstanceDbQuery.Builder, ObjectBuilder<DecisionInstanceDbQuery>> fn) {
    return fn.apply(new DecisionInstanceDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<DecisionInstanceDbQuery> {

    private static final DecisionInstanceFilter EMPTY_FILTER =
        FilterBuilders.decisionInstance().build();

    private DecisionInstanceFilter filter;
    private DbQuerySorting<DecisionInstanceEntity> sort;
    private DbQueryPage page;

    public Builder filter(final DecisionInstanceFilter value) {
      filter = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<DecisionInstanceEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(
        final Function<DecisionInstanceFilter.Builder, ObjectBuilder<DecisionInstanceFilter>> fn) {
      return filter(FilterBuilders.decisionInstance(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<DecisionInstanceEntity>,
                ObjectBuilder<DbQuerySorting<DecisionInstanceEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public DecisionInstanceDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      return new DecisionInstanceDbQuery(filter, sort, page);
    }
  }
}
