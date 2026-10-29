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
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto.FailureReasonDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto.ToolCallsDto;
import io.camunda.optimize.dto.optimize.query.process.FlowNodeInstanceDto;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Copies what an agent run's metrics need from its process instance onto the run, so every agent
 * tile is a single-index query. Always recomputed from the whole process instance document, which
 * makes it safe to run any number of times and in any order.
 */
public final class AgentRunEnrichment {

  /** Incident messages are kept for grouping failure reasons, so they are cut to a keyword size. */
  static final int MAX_MESSAGE_LENGTH = 500;

  private AgentRunEnrichment() {}

  public static AgentRunDto compute(
      final AgentRunDto run, final ProcessInstanceDto processInstance, final OffsetDateTime now) {
    return compute(run, processInstance, null, now);
  }

  /**
   * Returns a run carrying only the enrichment fields, ready for a partial update.
   *
   * @param rootProcessDefinitionKey the definition key of the run's root process instance when it
   *     is not the run's own process instance; null when unknown
   */
  public static AgentRunDto compute(
      final AgentRunDto run,
      final ProcessInstanceDto processInstance,
      final String rootProcessDefinitionKey,
      final OffsetDateTime now) {
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

    final RunWindow window = RunWindow.of(run, processInstance);
    final List<FlowNodeInstanceDto> userTaskTools =
        processInstance.getFlowNodeInstances().stream()
            .filter(node -> FLOW_NODE_TYPE_USER_TASK.equals(node.getFlowNodeType()))
            .filter(node -> run.getToolFlowNodeIds().contains(node.getFlowNodeId()))
            .filter(node -> window.contains(node.getStartDate()))
            .toList();
    enriched.setHumanStepCount((long) userTaskTools.size());
    enriched.setHumanWaitDurationMs(
        userTaskTools.stream()
            .map(FlowNodeInstanceDto::getTotalDurationInMs)
            .filter(Objects::nonNull)
            .mapToLong(Long::longValue)
            .sum());
    enriched.setActiveDurationMs(activeDuration(run, processInstance, userTaskTools));

    final List<IncidentDto> toolIncidents =
        processInstance.getIncidents().stream()
            .filter(incident -> isOnToolElement(incident, run, processInstance, window))
            .toList();
    enriched.setToolIncidentCount((long) toolIncidents.size());
    enriched.setToolIncidentsByTool(countToolIncidents(run, toolIncidents, processInstance));
    enriched.setFailureReasons(
        failureReasons(
            run,
            Stream.concat(agentIncidents.stream(), toolIncidents.stream()).toList(),
            processInstance));

    enriched.setRootProcessDefinitionKey(
        run.getRootProcessInstanceId() == null
                || run.getRootProcessInstanceId().equals(run.getProcessInstanceId())
            ? processInstance.getProcessDefinitionKey()
            : rootProcessDefinitionKey);
    enriched.setEnrichedAt(now);
    return enriched;
  }

  /**
   * Sum of the ended durations of the agent's element instances, without the time spent in
   * user-task tools inside them: a person working on a tool is not agent time. Null until one of
   * the element instances has ended. For an AI Agent Task the tools run in a separate ad-hoc
   * sub-process, outside the task's element instances, so their time is never part of it.
   */
  static Long activeDuration(
      final AgentRunDto run,
      final ProcessInstanceDto processInstance,
      final List<FlowNodeInstanceDto> userTaskTools) {
    final List<FlowNodeInstanceDto> ended =
        processInstance.getFlowNodeInstances().stream()
            .filter(node -> run.getFlowNodeInstanceIds().contains(node.getFlowNodeInstanceId()))
            .filter(node -> node.getTotalDurationInMs() != null)
            .toList();
    if (ended.isEmpty()) {
      return null;
    }
    long total = 0;
    for (final FlowNodeInstanceDto agentNode : ended) {
      final long humanTime =
          userTaskTools.stream()
              .filter(tool -> tool.getTotalDurationInMs() != null)
              .filter(tool -> isInside(tool, agentNode))
              .mapToLong(FlowNodeInstanceDto::getTotalDurationInMs)
              .sum();
      total += Math.max(0, agentNode.getTotalDurationInMs() - humanTime);
    }
    return total;
  }

  private static boolean isInside(
      final FlowNodeInstanceDto inner, final FlowNodeInstanceDto outer) {
    return inner.getStartDate() != null
        && inner.getEndDate() != null
        && outer.getStartDate() != null
        && outer.getEndDate() != null
        && !inner.getStartDate().isBefore(outer.getStartDate())
        && !inner.getEndDate().isAfter(outer.getEndDate());
  }

  /**
   * An incident counts as a tool failure of the run when it was raised on one of the run's tool
   * elements during the run. With the element instance key the instance's start decides; once the
   * key was rewritten to the element ID, the incident's creation time does.
   */
  static boolean isOnToolElement(
      final IncidentDto incident,
      final AgentRunDto run,
      final ProcessInstanceDto processInstance,
      final RunWindow window) {
    final String activityId = incident.getActivityId();
    if (activityId == null) {
      return false;
    }
    final Optional<FlowNodeInstanceDto> instance =
        processInstance.getFlowNodeInstances().stream()
            .filter(node -> activityId.equals(node.getFlowNodeInstanceId()))
            .findFirst();
    if (instance.isPresent()) {
      return run.getToolFlowNodeIds().contains(instance.get().getFlowNodeId())
          && window.contains(instance.get().getStartDate());
    }
    return run.getToolFlowNodeIds().contains(activityId)
        && (incident.getCreateTime() == null || window.contains(incident.getCreateTime()));
  }

  private static List<ToolCallsDto> countToolIncidents(
      final AgentRunDto run,
      final List<IncidentDto> toolIncidents,
      final ProcessInstanceDto processInstance) {
    final Map<String, Long> counts = new LinkedHashMap<>();
    toolIncidents.forEach(
        incident ->
            counts.merge(
                resolveFlowNodeId(incident.getActivityId(), processInstance), 1L, Long::sum));
    return counts.entrySet().stream()
        .map(
            entry -> {
              final ToolCallsDto tool = new ToolCallsDto();
              tool.setToolFlowNodeId(entry.getKey());
              tool.setToolName(toolName(run, entry.getKey()));
              tool.setCount(entry.getValue());
              return tool;
            })
        .toList();
  }

  /**
   * One reason per element and error type, in order of the incidents' creation, with the first
   * message seen. Messages of later incidents of the same kind are dropped to keep the document
   * small.
   */
  static List<FailureReasonDto> failureReasons(
      final AgentRunDto run,
      final List<IncidentDto> incidents,
      final ProcessInstanceDto processInstance) {
    final Map<List<String>, FailureReasonDto> reasons = new LinkedHashMap<>();
    incidents.stream()
        .sorted(
            Comparator.comparing(
                IncidentDto::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())))
        .forEach(
            incident -> {
              final String flowNodeId =
                  resolveFlowNodeId(incident.getActivityId(), processInstance);
              final String errorType =
                  incident.getIncidentType() == null ? null : incident.getIncidentType().getId();
              final FailureReasonDto reason =
                  reasons.computeIfAbsent(
                      Arrays.asList(flowNodeId, errorType),
                      key -> {
                        final FailureReasonDto created = new FailureReasonDto();
                        created.setFlowNodeId(flowNodeId);
                        created.setToolName(
                            run.getToolFlowNodeIds().contains(flowNodeId)
                                ? toolName(run, flowNodeId)
                                : null);
                        created.setErrorType(errorType);
                        created.setMessage(truncate(incident.getIncidentMessage()));
                        created.setCount(0L);
                        return created;
                      });
              reason.setCount(reason.getCount() + 1);
            });
    return new ArrayList<>(reasons.values());
  }

  private static String toolName(final AgentRunDto run, final String toolFlowNodeId) {
    return run.getToolCallsByTool().stream()
        .filter(tool -> toolFlowNodeId.equals(tool.getToolFlowNodeId()))
        .map(ToolCallsDto::getToolName)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(toolFlowNodeId);
  }

  private static String truncate(final String message) {
    return message == null || message.length() <= MAX_MESSAGE_LENGTH
        ? message
        : message.substring(0, MAX_MESSAGE_LENGTH);
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

  /**
   * The time span of a run: from its first record or element instance start to its last record or
   * element instance end. Open-ended while the run is still active.
   */
  record RunWindow(OffsetDateTime start, OffsetDateTime end) {

    static RunWindow of(final AgentRunDto run, final ProcessInstanceDto processInstance) {
      final List<FlowNodeInstanceDto> agentNodes =
          processInstance.getFlowNodeInstances().stream()
              .filter(node -> run.getFlowNodeInstanceIds().contains(node.getFlowNodeInstanceId()))
              .toList();
      final OffsetDateTime start =
          Stream.concat(
                  Stream.of(run.getStartDate()),
                  agentNodes.stream().map(FlowNodeInstanceDto::getStartDate))
              .filter(Objects::nonNull)
              .min(Comparator.naturalOrder())
              .orElse(null);
      final boolean running = agentNodes.stream().anyMatch(node -> node.getEndDate() == null);
      final OffsetDateTime end =
          running
              ? null
              : Stream.concat(
                      Stream.of(run.getEndDate(), run.getLastActivityDate()),
                      agentNodes.stream().map(FlowNodeInstanceDto::getEndDate))
                  .filter(Objects::nonNull)
                  .max(Comparator.naturalOrder())
                  .orElse(null);
      return new RunWindow(start, end);
    }

    boolean contains(final OffsetDateTime date) {
      return date != null
          && (start == null || !date.isBefore(start))
          && (end == null || !date.isAfter(end));
    }
  }
}
