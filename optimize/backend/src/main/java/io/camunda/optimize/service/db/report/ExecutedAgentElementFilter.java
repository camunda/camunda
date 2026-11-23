/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report;

import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.reader.AgentElementReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Restricts the process-instance tiles of the agentic dashboard to process instances that ran an
 * agent element of the selected processes, with Optimize's executed flow node filter. The agent
 * elements are only known per process, so the filter is added when a tile is evaluated instead of
 * being stored on the report. Agent run tiles read the agent index and need no such filter.
 */
final class ExecutedAgentElementFilter {

  // An empty list would match every instance; an ID no element has matches none.
  static final String NO_AGENT_ELEMENT = "__no_agent_element__";

  private static final Set<ProcessViewEntity> PROCESS_INSTANCE_ENTITIES =
      Set.of(
          ProcessViewEntity.PROCESS_INSTANCE,
          ProcessViewEntity.FLOW_NODE,
          ProcessViewEntity.INCIDENT,
          ProcessViewEntity.USER_TASK,
          // Phase 1 agent views, which read the agent data nested in process instances
          ProcessViewEntity.AGENT_INSTANCE);

  private ExecutedAgentElementFilter() {}

  static void addTo(final ProcessReportDataDto reportData, final AgentElementReader reader) {
    if (reportData.getView() == null
        || !PROCESS_INSTANCE_ENTITIES.contains(reportData.getView().getEntity())) {
      return;
    }
    final List<String> agentElements =
        reader.getAgentFlowNodeIds(
            reportData.getDefinitions().stream()
                .map(ReportDataDefinitionDto::getKey)
                .distinct()
                .toList());
    final List<ProcessFilterDto<?>> filters = new ArrayList<>(reportData.getFilter());
    filters.addAll(
        ProcessFilterBuilder.filter()
            .executedFlowNodes()
            .inOperator()
            .ids(
                agentElements.isEmpty()
                    ? new String[] {NO_AGENT_ELEMENT}
                    : agentElements.toArray(String[]::new))
            .add()
            .buildList());
    reportData.setFilter(filters);
  }
}
