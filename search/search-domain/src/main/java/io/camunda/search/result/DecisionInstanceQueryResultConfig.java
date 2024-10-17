/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.result;

import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.function.Function;

public record DecisionInstanceQueryResultConfig(List<FieldFilter> fieldFilters)
    implements QueryResultConfig {
  @Override
  public List<FieldFilter> getFieldFilters() {
    return fieldFilters;
  }

  public static DecisionInstanceQueryResultConfig of(
      final Function<
              DecisionInstanceQueryResultConfig.Builder,
              ObjectBuilder<DecisionInstanceQueryResultConfig>>
          fn) {
    return QueryResultConfigBuilders.decisionInstance(fn);
  }

  public static final class Builder
      extends AbstractBuilder<DecisionInstanceQueryResultConfig.Builder>
      implements ObjectBuilder<DecisionInstanceQueryResultConfig> {

    public DecisionInstanceQueryResultConfig.Builder evaluatedInputs() {
      currentFieldFilter = new FieldFilter("evaluatedInputs", null);
      return this;
    }

    public DecisionInstanceQueryResultConfig.Builder evaluatedOutputs() {
      currentFieldFilter = new FieldFilter("evaluatedOutputs", null);
      return this;
    }

    @Override
    protected DecisionInstanceQueryResultConfig.Builder self() {
      return this;
    }

    @Override
    public DecisionInstanceQueryResultConfig build() {
      return new DecisionInstanceQueryResultConfig(fieldFilters);
    }
  }
}
