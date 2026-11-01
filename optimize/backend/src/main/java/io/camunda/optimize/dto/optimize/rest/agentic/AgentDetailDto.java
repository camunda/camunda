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
 * One agent for the single agent view: its configuration as of the latest run and how its runs of
 * completed process instances went in the selected period.
 */
public record AgentDetailDto(
    String agentId,
    String name,
    String agentType,
    String processDefinitionKey,
    String processDefinitionName,
    String processDefinitionVersion,
    String model,
    String provider,
    Long maxModelCalls,
    Long maxToolCalls,
    Long maxTokens,
    long runs,
    long failedRuns,
    Double avgModelCalls,
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
        processDefinitionVersion,
        model,
        provider,
        maxModelCalls,
        maxToolCalls,
        maxTokens,
        runs,
        failedRuns,
        avgModelCalls,
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
    public static final String KIND_INCIDENT = "INCIDENT";
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
      Double successRate) {}

  /** Runs reached from one root process, directly or through call activities. */
  public record RootProcessUsage(
      String rootProcessDefinitionKey, String rootProcessName, long runs, Double totalTokens) {

    public RootProcessUsage withRootProcessName(final String name) {
      return new RootProcessUsage(rootProcessDefinitionKey, name, runs, totalTokens);
    }
  }
}
