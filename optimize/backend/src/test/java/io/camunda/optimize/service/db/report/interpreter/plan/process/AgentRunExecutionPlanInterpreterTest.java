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
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.AGENT_RUN_INVENTORY;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.AGENT_RUN_OUTPUT_TOKENS_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.AGENT_RUN_REASONING_TOKENS_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.AGENT_RUN_TOTAL_TOKENS_GROUP_BY_NONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
            AGENT_RUN_REASONING_TOKENS_GROUP_BY_NONE,
            AGENT_RUN_INVENTORY);
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

  @Test
  void shouldReadAgentDefinitionsOnTheTenantsOfEachReportDefinition() {
    // given two versions of one process on different tenants, and a process on a null tenant
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setDefinitions(
        List.of(
            definition("process-a", "tenant-1"),
            definition("process-a", "tenant-2"),
            definition("process-b", (String) null)));
    final List<Map<String, Set<String>>> requests = new ArrayList<>();

    // when
    AgentRunExecutionPlanInterpreter.definedAgentIdsByTenant(
        tenantIdsByKey -> {
          requests.add(tenantIdsByKey);
          return List.of();
        },
        reportData);

    // then
    assertThat(requests)
        .singleElement()
        .satisfies(
            request -> {
              assertThat(request).containsOnlyKeys("process-a", "process-b");
              assertThat(request.get("process-a"))
                  .containsExactlyInAnyOrder("tenant-1", "tenant-2");
              assertThat(request.get("process-b")).containsExactly((String) null);
            });
  }

  @Test
  void shouldGroupDefinedAgentIdsByTenant() {
    // given
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setDefinitions(List.of(definition("process-a", "tenant-1", "tenant-2")));

    // when
    final Map<String, Set<String>> agentIdsByTenant =
        AgentRunExecutionPlanInterpreter.definedAgentIdsByTenant(
            tenantIdsByKey ->
                List.of(
                    agentDefinition("process-a:agent-1", "tenant-1"),
                    agentDefinition("process-a:agent-2", "tenant-1"),
                    agentDefinition("process-a:agent-1", "tenant-2"),
                    agentDefinition("process-a:agent-3", null)),
            reportData);

    // then
    assertThat(agentIdsByTenant)
        .containsOnlyKeys("tenant-1", "tenant-2", null)
        .containsEntry("tenant-1", Set.of("process-a:agent-1", "process-a:agent-2"))
        .containsEntry("tenant-2", Set.of("process-a:agent-1"))
        .containsEntry(null, Set.of("process-a:agent-3"));
  }

  @Test
  void shouldFindNoDefinedAgentsWhenTheReportHasNoDefinitions() {
    // given
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setDefinitions(List.of());

    // when
    final Map<String, Set<String>> agentIdsByTenant =
        AgentRunExecutionPlanInterpreter.definedAgentIdsByTenant(
            tenantIdsByKey -> {
              assertThat(tenantIdsByKey).isEmpty();
              return List.of();
            },
            reportData);

    // then
    assertThat(agentIdsByTenant).isEmpty();
  }

  private static ReportDataDefinitionDto definition(final String key, final String... tenantIds) {
    final ReportDataDefinitionDto definition = new ReportDataDefinitionDto(key);
    definition.setTenantIds(Arrays.asList(tenantIds));
    return definition;
  }

  private static AgentDefinitionDto agentDefinition(final String agentId, final String tenantId) {
    final AgentDefinitionDto agentDefinition = new AgentDefinitionDto();
    agentDefinition.setAgentId(agentId);
    agentDefinition.setTenantId(tenantId);
    return agentDefinition;
  }

  private static ProcessReportDataDto reportWithFilters(final List<ProcessFilterDto<?>> filters) {
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setFilter(filters);
    return reportData;
  }
}
