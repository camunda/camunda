/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProcessViewAgentRunTokensInterpreterESTest {

  private final ProcessViewAgentRunTokensInterpreterES interpreter =
      new ProcessViewAgentRunTokensInterpreterES();

  @ParameterizedTest
  @CsvSource({
    "AGENT_RUN_TOTAL_TOKENS_GROUP_BY_NONE, totalTokens",
    "AGENT_RUN_INPUT_TOKENS_GROUP_BY_NONE, inputTokens",
    "AGENT_RUN_OUTPUT_TOKENS_GROUP_BY_NONE, outputTokens",
    "AGENT_RUN_CACHE_READ_TOKENS_GROUP_BY_NONE, cacheReadTokens",
    "AGENT_RUN_CACHE_WRITE_TOKENS_GROUP_BY_NONE, cacheWriteTokens",
    "AGENT_RUN_REASONING_TOKENS_GROUP_BY_NONE, reasoningTokens"
  })
  void shouldSumAndAverageTheRunFieldOfTheView(
      final ProcessExecutionPlan plan, final String expectedField) {
    // given
    final var context =
        AgentInterpreterTestSupport.contextWith(AggregationType.SUM, AggregationType.AVERAGE);
    when(context.getPlan()).thenReturn(plan);

    // when
    final var aggregations = interpreter.createAggregations(context);

    // then
    assertThat(interpreter.getSupportedViews()).contains(plan.getView());
    assertThat(aggregations.get("sumAggregation").build().sum().field()).isEqualTo(expectedField);
    assertThat(aggregations.get("avgAggregation").build().avg().field()).isEqualTo(expectedField);
  }

  @ParameterizedTest
  @CsvSource({"AGENT_RUN_TOTAL_TOKENS_GROUP_BY_NONE", "AGENT_RUN_REASONING_TOKENS_GROUP_BY_NONE"})
  void shouldReturnTheAggregatedValue(final ProcessExecutionPlan plan) {
    // given
    final var context = AgentInterpreterTestSupport.contextWith(AggregationType.SUM);
    when(context.getPlan()).thenReturn(plan);

    // when
    final var result =
        interpreter.retrieveResult(
            null, Map.of("sumAggregation", Aggregate.of(a -> a.sum(s -> s.value(42.0)))), context);

    // then
    assertThat(result.getViewMeasures()).singleElement().extracting("value").isEqualTo(42.0);
  }
}
