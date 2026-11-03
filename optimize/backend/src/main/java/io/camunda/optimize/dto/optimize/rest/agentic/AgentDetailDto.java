/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.agentic;

import java.util.List;

/**
 * One agent for the single agent view: how its runs of completed process instances went in the
 * selected period. Tool data only exists for AI Agent Sub-process agents, which {@code
 * toolsAvailable} tells from the runs' agent type.
 */
public record AgentDetailDto(
    String agentId,
    String name,
    String agentType,
    String processDefinitionKey,
    String processDefinitionName,
    long runs,
    long failedRuns,
    List<LimitHit> limitHits,
    List<FailureReason> failureReasons,
    List<ToolUsage> tools,
    boolean toolsAvailable,
    List<ModelUsage> models,
    List<RootProcessUsage> whereUsed) {

  public AgentDetailDto withProcessNames(
      final String processDefinitionName, final List<RootProcessUsage> whereUsed) {
    return new AgentDetailDto(
        agentId,
        name,
        agentType,
        processDefinitionKey,
        processDefinitionName,
        runs,
        failedRuns,
        limitHits,
        failureReasons,
        tools,
        toolsAvailable,
        models,
        whereUsed);
  }

  /** Runs that stopped at one limit. */
  public record LimitHit(String limit, long runs) {}

  /**
   * Why runs failed, ranked by runs. A limit reason names the limit; an incident reason names the
   * element (with the tool's name when it is a tool), the error type and the first message seen.
   */
  public record FailureReason(
      String kind,
      String limit,
      String flowNodeId,
      String toolName,
      String errorType,
      String message,
      long runs) {

    public static final String KIND_LIMIT = "LIMIT";
    public static final String KIND_AGENT_INCIDENT = "AGENT_INCIDENT";
    public static final String KIND_TOOL_INCIDENT = "TOOL_INCIDENT";
  }

  /** Calls and incidents of one tool element; the failure rate is incidents per call. */
  public record ToolUsage(
      String toolFlowNodeId,
      String toolName,
      long calls,
      Double callsPerRun,
      long incidents,
      Double failureRate) {}

  /** The agent's runs on one model. */
  public record ModelUsage(
      String model,
      String provider,
      long runs,
      Double avgTokens,
      Double avgDurationMs,
      Double incidentRate) {}

  /** Runs reached from one root process, directly or through call activities. */
  public record RootProcessUsage(
      String rootProcessDefinitionKey, String rootProcessName, long runs, Double totalTokens) {

    public RootProcessUsage withRootProcessName(final String name) {
      return new RootProcessUsage(rootProcessDefinitionKey, name, runs, totalTokens);
    }
  }
}
