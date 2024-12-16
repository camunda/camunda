/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.filter.DecisionRequirementsFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.result.DecisionRequirementsQueryResultConfig;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record DecisionRequirementsDbQuery(
    DecisionRequirementsFilter filter,
    DbQuerySorting<DecisionRequirementsEntity> sort,
    DbQueryPage page,
    DecisionRequirementsQueryResultConfig resultConfig) {

  public static DecisionRequirementsDbQuery of(
      final Function<Builder, ObjectBuilder<DecisionRequirementsDbQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<DecisionRequirementsDbQuery> {

    private static final DecisionRequirementsFilter DEFAULT_FILTER =
        FilterBuilders.decisionRequirements().build();
    private static final DecisionRequirementsQueryResultConfig DEFAULT_RESULT_CONFIG =
        DecisionRequirementsQueryResultConfig.of(b -> b);

    private DecisionRequirementsFilter filter;
    private DbQuerySorting<DecisionRequirementsEntity> sort;
    private DbQueryPage page;
    private DecisionRequirementsQueryResultConfig resultConfig;

    public DecisionRequirementsDbQuery.Builder filter(final DecisionRequirementsFilter value) {
      filter = value;
      return this;
    }

    public DecisionRequirementsDbQuery.Builder sort(
        final DbQuerySorting<DecisionRequirementsEntity> value) {
      sort = value;
      return this;
    }

    public DecisionRequirementsDbQuery.Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public DecisionRequirementsDbQuery.Builder resultConfig(
        final DecisionRequirementsQueryResultConfig resultConfig) {
      this.resultConfig = resultConfig;
      return this;
    }

    @Override
    public DecisionRequirementsDbQuery build() {
      filter = Objects.requireNonNullElse(filter, DEFAULT_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      resultConfig = Objects.requireNonNullElse(resultConfig, DEFAULT_RESULT_CONFIG);
      return new DecisionRequirementsDbQuery(filter, sort, page, resultConfig);
    }
  }
}
