/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.process.agent;

import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_FLOW_NODE_TOOL_CALLS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCE_METRICS_TOOL_CALLS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;

class ProcessViewFlowNodeToolCallsInterpreterOSTest {

  private final ProcessViewFlowNodeToolCallsInterpreterOS interpreter =
      new ProcessViewFlowNodeToolCallsInterpreterOS();

  @Test
  void getSupportedViewsReturnsFlowNodeToolCallsView() {
    // when / then
    assertThat(interpreter.getSupportedViews()).containsExactly(PROCESS_VIEW_FLOW_NODE_TOOL_CALLS);
  }

  @Test
  void getAggregationFieldNameReturnsNestedAgentInstanceMetricsField() {
    // when / then the field is scoped under the agentInstances nested path, not the flat rollup
    assertThat(interpreter.getAggregationFieldName())
        .isEqualTo(AGENT_INSTANCES + "." + AGENT_INSTANCE_METRICS_TOOL_CALLS);
  }

  @Test
  void createAggregationsWithSumTypeCreatesSumAggregationKey() {
    // given
    final var ctx = AgentInterpreterTestSupport.contextWith(AggregationType.SUM);

    // when
    final var aggs = interpreter.createAggregations(ctx);

    // then
    assertThat(aggs).containsKey("sumAggregation");
  }

  @Test
  void retrieveResultHappyPathReturnsMappedValue() {
    // given
    final var ctx = AgentInterpreterTestSupport.contextWith(AggregationType.SUM);
    final var aggs = Map.of("sumAggregation", Aggregate.of(a -> a.sum(s -> s.value(23.0))));

    // when
    final var result = interpreter.retrieveResult(null, aggs, ctx);

    // then
    assertThat(result.getViewMeasures()).hasSize(1);
    assertThat(result.getViewMeasures().get(0).getValue()).isEqualTo(23.0);
  }

  @Test
  void retrieveResultZeroValueReturnsZero() {
    // given a flow node with no tool calls at all
    final var ctx = AgentInterpreterTestSupport.contextWith(AggregationType.SUM);
    final var aggs = Map.of("sumAggregation", Aggregate.of(a -> a.sum(s -> s.value(0.0))));

    // when
    final var result = interpreter.retrieveResult(null, aggs, ctx);

    // then
    assertThat(result.getViewMeasures().get(0).getValue()).isEqualTo(0.0);
  }

  @Test
  void retrieveResultNanAggregationReturnsNull() {
    // given
    final var ctx = AgentInterpreterTestSupport.contextWith(AggregationType.SUM);
    final var aggs = Map.of("sumAggregation", Aggregate.of(a -> a.sum(s -> s.value(Double.NaN))));

    // when
    final var result = interpreter.retrieveResult(null, aggs, ctx);

    // then
    assertThat(result.getViewMeasures().get(0).getValue()).isNull();
  }
}
