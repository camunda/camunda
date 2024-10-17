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

public final class QueryResultConfigBuilders {

  private QueryResultConfigBuilders() {}

  public static ProcessInstanceQueryResultConfig.Builder processInstance() {
    return new ProcessInstanceQueryResultConfig.Builder();
  }

  public static ProcessInstanceQueryResultConfig processInstance(
      final Function<
              ProcessInstanceQueryResultConfig.Builder,
              ObjectBuilder<ProcessInstanceQueryResultConfig>>
          fn) {
    return fn.apply(processInstance()).build();
  }

  public static DecisionRequirementsQueryResultConfig.Builder decisionRequirements() {
    return new DecisionRequirementsQueryResultConfig.Builder();
  }

  public static DecisionRequirementsQueryResultConfig decisionRequirements(
      final Function<
              DecisionRequirementsQueryResultConfig.Builder,
              ObjectBuilder<DecisionRequirementsQueryResultConfig>>
          fn) {
    return fn.apply(decisionRequirements()).build();
  }

  public static DecisionInstanceQueryResultConfig.Builder decisionInstance() {
    return new DecisionInstanceQueryResultConfig.Builder();
  }

  public static DecisionInstanceQueryResultConfig decisionInstance(
      final Function<
              DecisionInstanceQueryResultConfig.Builder,
              ObjectBuilder<DecisionInstanceQueryResultConfig>>
          fn) {
    return fn.apply(decisionInstance()).build();
  }
}
