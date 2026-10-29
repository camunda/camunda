/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process.agentrun;

import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.AGENT_RUN_DURATION_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.AGENT_RUN_HUMAN_STEPS_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.AGENT_RUN_MODEL_CALLS_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.AGENT_RUN_TOOL_CALLS_GROUP_BY_TOOL;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.AGENT_RUN_TOTAL_TOKENS_GROUP_BY_MODEL;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.AGENT_RUN_TOTAL_TOKENS_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.AGENT_RUN_TOTAL_TOKENS_GROUP_BY_START_DATE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentRunViewInterpretersTest {

  @Test
  void shouldAggregateFlatRunFieldsForUngroupedMetrics() {
    assertThat(ProcessViewAgentRunMetricInterpreterES.fieldFor(AGENT_RUN_DURATION_GROUP_BY_NONE))
        .isEqualTo("activeDurationMs");
    assertThat(ProcessViewAgentRunMetricInterpreterES.fieldFor(AGENT_RUN_MODEL_CALLS_GROUP_BY_NONE))
        .isEqualTo("modelCalls");
    assertThat(ProcessViewAgentRunMetricInterpreterES.fieldFor(AGENT_RUN_HUMAN_STEPS_GROUP_BY_NONE))
        .isEqualTo("humanStepCount");
    assertThat(
            ProcessViewAgentRunMetricInterpreterES.fieldFor(AGENT_RUN_TOTAL_TOKENS_GROUP_BY_NONE))
        .isEqualTo("totalTokens");
    assertThat(
            ProcessViewAgentRunMetricInterpreterES.fieldFor(
                AGENT_RUN_TOTAL_TOKENS_GROUP_BY_START_DATE))
        .isEqualTo("totalTokens");
  }

  @Test
  void shouldAggregateNestedEntriesWhenGroupedByModelOrTool() {
    // Per-model tokens and per-tool calls live in nested entries, so a run that used two models
    // or several tools contributes to each group with its own share.
    assertThat(
            ProcessViewAgentRunMetricInterpreterES.fieldFor(AGENT_RUN_TOTAL_TOKENS_GROUP_BY_MODEL))
        .isEqualTo("tokensByModel.totalTokens");
    assertThat(ProcessViewAgentRunMetricInterpreterES.fieldFor(AGENT_RUN_TOOL_CALLS_GROUP_BY_TOOL))
        .isEqualTo("toolCallsByTool.count");
  }

  @Test
  void shouldComputeFlagRateAsShareOfRunsInPercent() {
    assertThat(ProcessViewAgentRunRateInterpreterES.rate(3, 12)).isEqualTo(25.0);
    assertThat(ProcessViewAgentRunRateInterpreterES.rate(0, 12)).isEqualTo(0.0);
  }

  @Test
  void shouldReturnNoFlagRateWithoutRuns() {
    assertThat(ProcessViewAgentRunRateInterpreterES.rate(0, 0)).isNull();
  }

  @Test
  void shouldComputeAdoptionRateAgainstCompletedAgenticProcessInstances() {
    assertThat(ProcessViewAgentRunAdoptionRateInterpreterES.rate(30, 120L)).isEqualTo(25.0);
  }

  @Test
  void shouldReturnNoAdoptionRateWithoutDenominator() {
    assertThat(ProcessViewAgentRunAdoptionRateInterpreterES.rate(5, null)).isNull();
    assertThat(ProcessViewAgentRunAdoptionRateInterpreterES.rate(5, 0L)).isNull();
  }

  @Test
  void shouldCapAdoptionRateWhenProcessInstancesLagBehindAgentRuns() {
    assertThat(ProcessViewAgentRunAdoptionRateInterpreterES.rate(12, 10L)).isEqualTo(100.0);
  }
}
