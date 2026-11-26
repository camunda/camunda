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
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.filter.AgentRunFilters;
import io.camunda.optimize.service.db.reader.AgentDefinitionReader;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Evaluates the agentRun views against the agent run index, one document per agent run. The report
 * definitions and the accepted process filters ({@link AgentRunFilters}) are applied to the agent
 * run's own fields, not the process instance's.
 *
 * <p>A run counts only when its agent definition is imported on the run's tenant, as a process
 * instance counts only when its process definition is imported. Definitions and runs are imported
 * from separate record streams, so a run can arrive first; until its definition follows, the run
 * has no name or agent type and is left out of every agentRun report.
 */
public interface AgentRunExecutionPlanInterpreter extends ProcessExecutionPlanInterpreter {

  @Override
  default Set<ProcessExecutionPlan> getSupportedExecutionPlans() {
    return Arrays.stream(ProcessExecutionPlan.values())
        .filter(
            plan -> plan.getView().getProcessViewDto().getEntity() == ProcessViewEntity.AGENT_RUN)
        .collect(toSet());
  }

  /** Rejects the filters an agentRun report cannot apply, before the report is evaluated. */
  static void validateFilters(final ProcessReportDataDto reportData) {
    AgentRunFilters.validate(reportData.getFilter());
  }

  /**
   * The agent ids with an imported definition, per tenant, within the processes and tenants the
   * report is scoped to. A {@code null} key holds the definitions without a tenant.
   */
  static Map<String, Set<String>> definedAgentIdsByTenant(
      final AgentDefinitionReader agentDefinitionReader, final ProcessReportDataDto reportData) {
    final Map<String, Set<String>> agentIdsByTenant = new HashMap<>();
    for (final AgentDefinitionDto agentDefinition :
        agentDefinitionReader.getAgentDefinitions(tenantIdsByProcessDefinitionKey(reportData))) {
      agentIdsByTenant
          .computeIfAbsent(agentDefinition.getTenantId(), tenantId -> new HashSet<>())
          .add(agentDefinition.getAgentId());
    }
    return agentIdsByTenant;
  }

  /** The tenants the report is scoped to, per process definition key. */
  static Map<String, Set<String>> tenantIdsByProcessDefinitionKey(
      final ProcessReportDataDto reportData) {
    final Map<String, Set<String>> tenantIdsByProcessDefinitionKey = new HashMap<>();
    for (final ReportDataDefinitionDto definition : reportData.getDefinitions()) {
      if (definition.getKey() != null) {
        tenantIdsByProcessDefinitionKey
            .computeIfAbsent(definition.getKey(), key -> new HashSet<>())
            .addAll(Optional.ofNullable(definition.getTenantIds()).orElse(List.of()));
      }
    }
    return tenantIdsByProcessDefinitionKey;
  }
}
