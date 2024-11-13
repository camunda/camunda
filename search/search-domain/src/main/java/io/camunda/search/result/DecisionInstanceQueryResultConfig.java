/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.result;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public record DecisionInstanceQueryResultConfig(
    Boolean includeEvaluatedInputs, Boolean includeEvaluatedOutputs) implements QueryResultConfig {

  public static DecisionInstanceQueryResultConfig of(
      final Function<
              DecisionInstanceQueryResultConfig.Builder,
              ObjectBuilder<DecisionInstanceQueryResultConfig>>
          fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<DecisionInstanceQueryResultConfig> {

    private static final Boolean DEFAULT_INCLUDE_EVALUATED_INPUTS = false;
    private static final Boolean DEFAULT_INCLUDE_EVALUATED_OUTPUTS = false;

    private Boolean includeEvaluatedInputs = DEFAULT_INCLUDE_EVALUATED_INPUTS;
    private Boolean includeEvaluatedOutputs = DEFAULT_INCLUDE_EVALUATED_OUTPUTS;

    public DecisionInstanceQueryResultConfig.Builder includeEvaluatedInputs(
        final boolean includeEvaluatedInputs) {
      this.includeEvaluatedInputs = includeEvaluatedInputs;
      return this;
    }

    public DecisionInstanceQueryResultConfig.Builder includeEvaluatedOutputs(
        final boolean includeEvaluatedOutputs) {
      this.includeEvaluatedOutputs = includeEvaluatedOutputs;
      return this;
    }

    @Override
    public DecisionInstanceQueryResultConfig build() {
      return new DecisionInstanceQueryResultConfig(includeEvaluatedInputs, includeEvaluatedOutputs);
    }
  }
}
