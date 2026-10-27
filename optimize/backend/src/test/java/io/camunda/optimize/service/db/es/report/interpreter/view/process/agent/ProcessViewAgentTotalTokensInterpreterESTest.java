/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process.agent;

import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_TOTAL_TOKENS;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProcessViewAgentTotalTokensInterpreterESTest {

  private final ProcessViewAgentTotalTokensInterpreterES interpreter =
      new ProcessViewAgentTotalTokensInterpreterES();

  @Test
  void getSupportedViewsReturnsTotalTokensView() {
    assertThat(interpreter.getSupportedViews()).containsExactly(PROCESS_VIEW_AGENT_TOTAL_TOKENS);
  }

  @Test
  void getAggregationScriptReferencesTotalTokensField() {
    final var script = interpreter.getAggregationScript();
    assertThat(script).isNotNull();
    assertThat(script.source()).contains(ProcessInstanceIndex.AGENT_TOTAL_TOKENS);
  }

  @Test
  void createAggregationsWithSumTypeCreatesSumAggregationKey() {
    final var ctx = AgentInterpreterTestSupport.contextWith(AggregationType.SUM);
    final var aggs = interpreter.createAggregations(ctx);
    assertThat(aggs).containsKey("sumAggregation");
  }

  @Test
  void retrieveResultHappyPathReturnsMappedValue() {
    final var ctx = AgentInterpreterTestSupport.contextWith(AggregationType.SUM);
    final var aggs = Map.of("sumAggregation", Aggregate.of(a -> a.sum(s -> s.value(150.0))));
    final var result = interpreter.retrieveResult(null, aggs, ctx);
    assertThat(result.getViewMeasures()).hasSize(1);
    assertThat(result.getViewMeasures().get(0).getValue()).isEqualTo(150.0);
  }

  @Test
  void retrieveResultZeroValueReturnsZero() {
    final var ctx = AgentInterpreterTestSupport.contextWith(AggregationType.SUM);
    final var aggs = Map.of("sumAggregation", Aggregate.of(a -> a.sum(s -> s.value(0.0))));
    final var result = interpreter.retrieveResult(null, aggs, ctx);
    assertThat(result.getViewMeasures().get(0).getValue()).isEqualTo(0.0);
  }

  @Test
  void retrieveResultNanAggregationReturnsNull() {
    final var ctx = AgentInterpreterTestSupport.contextWith(AggregationType.SUM);
    final var aggs = Map.of("sumAggregation", Aggregate.of(a -> a.sum(s -> s.value(Double.NaN))));
    final var result = interpreter.retrieveResult(null, aggs, ctx);
    assertThat(result.getViewMeasures().get(0).getValue()).isNull();
  }

  @Test
  void retrieveResultLargeValueMapsThroughUnchanged() {
    final var ctx = AgentInterpreterTestSupport.contextWith(AggregationType.SUM);
    final var largeValue = 400_000_000.0;
    final var aggs = Map.of("sumAggregation", Aggregate.of(a -> a.sum(s -> s.value(largeValue))));
    final var result = interpreter.retrieveResult(null, aggs, ctx);
    assertThat(result.getViewMeasures().get(0).getValue()).isEqualTo(largeValue);
  }
}
