/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.process.agent;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AbstractProcessViewAgentMetricInterpreterOSTest {

  @Test
  void createAggregationsThrowsWhenNeitherFieldNorScriptProvided() {
    final var interpreter = new MissingSourceInterpreterOS();
    final var ctx = AgentInterpreterTestSupport.contextWith(AggregationType.SUM);
    assertThatThrownBy(() -> interpreter.createAggregations(ctx))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("getAggregationFieldName")
        .hasMessageContaining("getAggregationScript");
  }

  /** Subclass that overrides neither hook, exercising the fail-fast guard. */
  private static final class MissingSourceInterpreterOS
      extends AbstractProcessViewAgentMetricInterpreterOS {
    @Override
    public Set<ProcessView> getSupportedViews() {
      return Set.of();
    }
  }
}
