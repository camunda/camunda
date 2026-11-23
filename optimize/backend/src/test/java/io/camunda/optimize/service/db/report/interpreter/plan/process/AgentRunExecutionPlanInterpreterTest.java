/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.plan.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.AGENT_RUN_CACHE_READ_TOKENS_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.AGENT_RUN_CACHE_WRITE_TOKENS_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.AGENT_RUN_FREQUENCY_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.AGENT_RUN_INPUT_TOKENS_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.AGENT_RUN_OUTPUT_TOKENS_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.AGENT_RUN_REASONING_TOKENS_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.AGENT_RUN_TOTAL_TOKENS_GROUP_BY_NONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentRunExecutionPlanInterpreterTest {

  private final AgentRunExecutionPlanInterpreter interpreter =
      new AgentRunExecutionPlanInterpreter() {
        @Override
        public CommandEvaluationResult<Object> interpret(
            final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
          return null;
        }

        @Override
        public Optional<MinMaxStatDto> getGroupByMinMaxStats(
            final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
          return Optional.empty();
        }
      };

  @Test
  void shouldSupportOnlyTheAgentRunPlans() {
    // when
    final Set<ProcessExecutionPlan> plans = interpreter.getSupportedExecutionPlans();

    // then
    assertThat(plans)
        .containsExactlyInAnyOrder(
            AGENT_RUN_FREQUENCY_GROUP_BY_NONE,
            AGENT_RUN_TOTAL_TOKENS_GROUP_BY_NONE,
            AGENT_RUN_INPUT_TOKENS_GROUP_BY_NONE,
            AGENT_RUN_OUTPUT_TOKENS_GROUP_BY_NONE,
            AGENT_RUN_CACHE_READ_TOKENS_GROUP_BY_NONE,
            AGENT_RUN_CACHE_WRITE_TOKENS_GROUP_BY_NONE,
            AGENT_RUN_REASONING_TOKENS_GROUP_BY_NONE);
  }

  @Test
  void shouldAcceptEndDateAndCompletedFilters() {
    // given
    final ProcessReportDataDto reportData =
        reportWithFilters(
            ProcessFilterBuilder.filter()
                .completedInstancesOnly()
                .add()
                .rollingInstanceEndDate()
                .start(7L, DateUnit.DAYS)
                .add()
                .buildList());

    // when / then
    assertThatCode(() -> AgentRunExecutionPlanInterpreter.validateFilters(reportData))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectFiltersOnFieldsAgentRunsDoNotHave() {
    // given
    final ProcessReportDataDto reportData =
        reportWithFilters(ProcessFilterBuilder.filter().runningInstancesOnly().add().buildList());

    // when / then
    assertThatThrownBy(() -> AgentRunExecutionPlanInterpreter.validateFilters(reportData))
        .isInstanceOf(OptimizeValidationException.class)
        .hasMessageContaining("RunningInstancesOnlyFilterDto");
  }

  private static ProcessReportDataDto reportWithFilters(final List<ProcessFilterDto<?>> filters) {
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setFilter(filters);
    return reportData;
  }
}
