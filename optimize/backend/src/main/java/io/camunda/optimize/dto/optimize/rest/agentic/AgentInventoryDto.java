/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.agentic;

import java.time.OffsetDateTime;
import java.util.List;

/** Every known agent with its configuration and how its runs went in the selected period. */
public record AgentInventoryDto(Summary summary, List<AgentInventoryEntry> agents) {

  /** Counts for the inventory header. */
  public record Summary(long agents, long processes, long externalAgents) {}

  /**
   * One agent. Run metrics cover runs of completed process instances in the period; rates and
   * averages are null when the agent had no such runs.
   */
  public record AgentInventoryEntry(
      String agentId,
      String name,
      String agentType,
      String processDefinitionKey,
      String processDefinitionName,
      List<String> models,
      Long maxModelCalls,
      long runs,
      Double incidentRate,
      Double limitHitRate,
      Double durationP50Ms,
      Double avgTokens,
      OffsetDateTime lastRunDate,
      Double totalTokens,
      Double p50Tokens,
      Double successRate,
      Double durationAvgMs,
      Double durationP95Ms,
      Double avgHumanSteps) {

    public AgentInventoryEntry withProcessDefinitionName(final String processDefinitionName) {
      return new AgentInventoryEntry(
          agentId,
          name,
          agentType,
          processDefinitionKey,
          processDefinitionName,
          models,
          maxModelCalls,
          runs,
          incidentRate,
          limitHitRate,
          durationP50Ms,
          avgTokens,
          lastRunDate,
          totalTokens,
          p50Tokens,
          successRate,
          durationAvgMs,
          durationP95Ms,
          avgHumanSteps);
    }
  }
}
