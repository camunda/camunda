/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.plan.process;

import static java.util.stream.Collectors.toSet;

import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.reader.AgentDefinitionReader;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Evaluates the agentRun views against the agent run index, one document per agent run. The run
 * index uses the process instance field names for the definition, tenant and end date, so the
 * process definition scoping and the instance end date filters apply to it unchanged.
 *
 * <p>A run counts only when its agent definition is imported on the run's tenant, as a process
 * instance counts only when its process definition is imported. Definitions and runs are imported
 * from separate record streams, so a run can arrive first; until its definition follows, the run
 * has no name or agent type and is left out of every agentRun report.
 */
public interface AgentRunExecutionPlanInterpreter extends ProcessExecutionPlanInterpreter {

  List<Class<? extends ProcessFilterDto<?>>> SUPPORTED_FILTERS =
      List.of(InstanceEndDateFilterDto.class, CompletedInstancesOnlyFilterDto.class);

  @Override
  default Set<ProcessExecutionPlan> getSupportedExecutionPlans() {
    return Arrays.stream(ProcessExecutionPlan.values())
        .filter(
            plan -> plan.getView().getProcessViewDto().getEntity() == ProcessViewEntity.AGENT_RUN)
        .collect(toSet());
  }

  /** Other process filters read fields that agent runs do not have, so they are rejected. */
  static void validateFilters(final ProcessReportDataDto reportData) {
    reportData.getFilter().stream()
        .filter(filter -> !SUPPORTED_FILTERS.contains(filter.getClass()))
        .findFirst()
        .ifPresent(
            filter -> {
              throw new OptimizeValidationException(
                  String.format(
                      "The filter [%s] is not supported for agentRun views. Supported filters: "
                          + "instanceEndDate, completedInstancesOnly.",
                      filter.getClass().getSimpleName()));
            });
  }

  /**
   * The agent ids with an imported definition, per tenant, within the processes and tenants the
   * report is scoped to. A {@code null} key holds the definitions without a tenant.
   */
  static Map<String, Set<String>> definedAgentIdsByTenant(
      final AgentDefinitionReader agentDefinitionReader, final ProcessReportDataDto reportData) {
    final Map<String, Set<String>> tenantIdsByProcessDefinitionKey = new HashMap<>();
    for (final ReportDataDefinitionDto definition : reportData.getDefinitions()) {
      if (definition.getKey() != null) {
        tenantIdsByProcessDefinitionKey
            .computeIfAbsent(definition.getKey(), key -> new HashSet<>())
            .addAll(Optional.ofNullable(definition.getTenantIds()).orElse(List.of()));
      }
    }
    final Map<String, Set<String>> agentIdsByTenant = new HashMap<>();
    for (final AgentDefinitionDto agentDefinition :
        agentDefinitionReader.getAgentDefinitions(tenantIdsByProcessDefinitionKey)) {
      agentIdsByTenant
          .computeIfAbsent(agentDefinition.getTenantId(), tenantId -> new HashSet<>())
          .add(agentDefinition.getAgentId());
    }
    return agentIdsByTenant;
  }
}
