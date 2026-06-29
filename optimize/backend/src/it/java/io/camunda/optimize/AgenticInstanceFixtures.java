/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import io.camunda.optimize.dto.optimize.query.process.AgentInstanceDto;
import io.camunda.optimize.dto.optimize.query.process.AgentInstanceDto.AgentMetricsDto;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Test-data builders for the agentic KPI tile integration tests. Kept in the {@code
 * io.camunda.optimize} package so the static factories can reach the {@code protected static
 * AbstractBrokerlessZeebeCCSMIT#completedInstance} base builder without inheriting from it —
 * favouring composition over a shared abstract test superclass.
 */
public final class AgenticInstanceFixtures {

  public static final String PROC_KEY = "my-agent-process";

  private AgenticInstanceFixtures() {}

  /**
   * Builds a minimal completed agentic {@link ProcessInstanceDto} with one {@link AgentInstanceDto}
   * and the given total token counts.
   */
  public static ProcessInstanceDto.ProcessInstanceDtoBuilder agenticInstanceWithTokens(
      final String processDefinitionKey, final long inputTokens, final long outputTokens) {
    final AgentMetricsDto metrics = new AgentMetricsDto();
    metrics.setInputTokens(inputTokens);
    metrics.setOutputTokens(outputTokens);

    final AgentInstanceDto agentInstance = new AgentInstanceDto();
    agentInstance.setAgentInstanceId(UUID.randomUUID().toString());
    agentInstance.setMetrics(metrics);

    return AbstractBrokerlessZeebeCCSMIT.completedInstance(processDefinitionKey)
        .agentInstances(List.of(agentInstance))
        .agentTotalInputTokens(inputTokens)
        .agentTotalOutputTokens(outputTokens)
        .agentTotalTokens(inputTokens + outputTokens);
  }

  /**
   * Builds a minimal completed agentic {@link ProcessInstanceDto} with one {@link AgentInstanceDto}
   * and the given execution duration (ms). Use this overload for duration-focused tests.
   */
  public static ProcessInstanceDto.ProcessInstanceDtoBuilder agenticInstanceWithDuration(
      final String processDefinitionKey, final long duration) {
    return agenticInstanceWithTokens(processDefinitionKey, 0L, 0L).duration(duration);
  }

  /**
   * Builds a completed agentic {@link ProcessInstanceDto} with the given total tool-call count. The
   * TOOL_CALLS view property sums the per-instance rollup {@code agentTotalToolCalls}, which the
   * exporter computes across the instance's agent instances — so the test sets it directly.
   */
  public static ProcessInstanceDto.ProcessInstanceDtoBuilder agenticInstanceWithToolCalls(
      final String processDefinitionKey, final long toolCalls) {
    return agenticInstanceWithTokens(processDefinitionKey, 0L, 0L).agentTotalToolCalls(toolCalls);
  }

  /**
   * Builds a completed agentic {@link ProcessInstanceDto} pinned to a specific process definition
   * version, so reports grouped by version see distinct buckets.
   */
  public static ProcessInstanceDto.ProcessInstanceDtoBuilder agenticInstance(
      final String processDefinitionKey,
      final String processDefinitionVersion,
      final long inputTokens,
      final long outputTokens) {
    return agenticInstanceWithTokens(processDefinitionKey, inputTokens, outputTokens)
        .processDefinitionVersion(processDefinitionVersion)
        .processDefinitionId(processDefinitionKey + ":" + processDefinitionVersion + ":1");
  }

  public static ProcessInstanceDto resolvedIncident(
      final ProcessInstanceDto.ProcessInstanceDtoBuilder builder) {
    final String instanceId = UUID.randomUUID().toString();
    return builder
        .processInstanceId(instanceId)
        .incidents(
            List.of(
                IncidentDto.builder()
                    .incidentStatus(IncidentStatus.RESOLVED)
                    .processInstanceId(instanceId)
                    .build()))
        .build();
  }

  public static List<ProcessInstanceDto> repeat(
      final int count, final Supplier<ProcessInstanceDto> supplier) {
    return IntStream.range(0, count).mapToObj(i -> supplier.get()).toList();
  }
}
