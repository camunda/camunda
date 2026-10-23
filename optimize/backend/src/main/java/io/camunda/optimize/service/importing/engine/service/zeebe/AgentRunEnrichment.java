/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_USER_TASK;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto.ToolCallsDto;
import io.camunda.optimize.dto.optimize.query.process.FlowNodeInstanceDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Copies what an agent run's metrics need from its process instance onto the run, so every agent
 * tile is a single-index query. Always recomputed from the whole process instance document, which
 * makes it safe to run any number of times and in any order.
 */
public final class AgentRunEnrichment {

  private AgentRunEnrichment() {}

  /** Returns a run carrying only the enrichment fields, ready for a partial update. */
  public static AgentRunDto compute(
      final AgentRunDto run, final ProcessInstanceDto processInstance, final OffsetDateTime now) {
    final AgentRunDto enriched = new AgentRunDto();
    enriched.setAgentInstanceId(run.getAgentInstanceId());
    enriched.setProcessInstanceState(processInstance.getState());
    enriched.setProcessInstanceEndDate(processInstance.getEndDate());

    final List<IncidentDto> agentIncidents =
        processInstance.getIncidents().stream()
            .filter(incident -> isOnAgentElement(incident, run, processInstance))
            .toList();
    enriched.setIncidentCount((long) agentIncidents.size());
    enriched.setHasIncident(!agentIncidents.isEmpty());
    enriched.setIncidentFlowNodeIds(
        agentIncidents.stream()
            .map(incident -> resolveFlowNodeId(incident.getActivityId(), processInstance))
            .distinct()
            .toList());

    enriched.setProcessUserTaskCount(
        processInstance.getFlowNodeInstances().stream()
            .filter(node -> FLOW_NODE_TYPE_USER_TASK.equals(node.getFlowNodeType()))
            .count());

    enriched.setToolCallsByTool(
        run.getToolCallsByTool().stream()
            .map(tool -> countToolCalls(tool, processInstance))
            .toList());
    enriched.setEnrichedAt(now);
    return enriched;
  }

  /**
   * An incident counts for the run when it was raised on one of the run's element instances. The
   * incident import stores the element instance key in {@code activityId} and rewrites it to the
   * element ID once the flow node is known, so both forms are matched. A rewritten ID can't tell
   * element instances apart, so with several runs of one agent element in a process instance each
   * run sees the incident.
   */
  static boolean isOnAgentElement(
      final IncidentDto incident, final AgentRunDto run, final ProcessInstanceDto processInstance) {
    final String activityId = incident.getActivityId();
    if (activityId == null) {
      return false;
    }
    if (run.getFlowNodeInstanceIds().contains(activityId)) {
      return true;
    }
    final boolean isElementInstanceKey =
        processInstance.getFlowNodeInstances().stream()
            .anyMatch(node -> activityId.equals(node.getFlowNodeInstanceId()));
    return !isElementInstanceKey && activityId.equals(run.getFlowNodeId());
  }

  private static String resolveFlowNodeId(
      final String activityId, final ProcessInstanceDto processInstance) {
    return processInstance.getFlowNodeInstances().stream()
        .filter(node -> activityId.equals(node.getFlowNodeInstanceId()))
        .map(FlowNodeInstanceDto::getFlowNodeId)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(activityId);
  }

  /**
   * Tool calls are the flow node instances of the tool's element in the run's process instance, the
   * same matching the Phase 1 tool-call heatmap uses. Several runs of one agent element in a
   * process instance would each see all of them.
   */
  private static ToolCallsDto countToolCalls(
      final ToolCallsDto tool, final ProcessInstanceDto processInstance) {
    final ToolCallsDto counted = new ToolCallsDto();
    counted.setToolFlowNodeId(tool.getToolFlowNodeId());
    counted.setToolName(tool.getToolName());
    counted.setCount(
        processInstance.getFlowNodeInstances().stream()
            .filter(node -> tool.getToolFlowNodeId().equals(node.getFlowNodeId()))
            .count());
    return counted;
  }
}
