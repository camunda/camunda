/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.AgentInstanceHistoryEntity;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryCommitStatus;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryRole;
import io.camunda.search.entities.AgentInstanceHistoryEntity.Limits;
import io.camunda.search.entities.AgentInstanceHistoryEntity.Metrics;
import io.camunda.search.entities.AgentInstanceHistoryEntity.Tool;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ToolCall;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity.AgentHistoryEmbeddedToolCallValue;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity.AgentHistoryLimitsValue;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity.AgentHistoryToolValue;
import java.util.List;
import java.util.Objects;

public class AgentHistoryEntityTransformer
    implements ServiceTransformer<AgentHistoryEntity, AgentInstanceHistoryEntity> {

  @Override
  public AgentInstanceHistoryEntity apply(final AgentHistoryEntity source) {
    return new AgentInstanceHistoryEntity(
        source.getKey(),
        Objects.requireNonNullElse(source.getHistoryItemId(), ""),
        source.getAgentInstanceKey(),
        source.getElementInstanceKey(),
        source.getProcessInstanceKey(),
        source.getProcessDefinitionKey(),
        source.getBpmnProcessId(),
        source.getTenantId(),
        source.getJobKey(),
        source.getJobLease(),
        source.getLoopIteration(),
        toRole(source.getRole()),
        AgentContentTransformer.toContent(source.getContent()),
        toToolCalls(source.getToolCalls()),
        toMetrics(
            source.getInputTokens(),
            source.getOutputTokens(),
            source.getReasoningTokenCount(),
            source.getCacheCreationTokenCount(),
            source.getCacheReadTokenCount(),
            source.getDurationMs()),
        toTools(source.getTools()),
        source.getModel(),
        source.getProvider(),
        toLimits(source.getLimits()),
        AgentContentTransformer.toContent(source.getSystemPrompt()),
        toCommitStatus(source.getCommitStatus()),
        source.getProducedAt());
  }

  private static AgentInstanceHistoryRole toRole(
      final io.camunda.webapps.schema.entities.agenthistory.AgentHistoryRole role) {
    return switch (role) {
      case USER -> AgentInstanceHistoryRole.USER;
      case ASSISTANT -> AgentInstanceHistoryRole.ASSISTANT;
      case TOOL_RESULT -> AgentInstanceHistoryRole.TOOL_RESULT;
      case CONFIGURATION -> AgentInstanceHistoryRole.CONFIGURATION;
    };
  }

  private static AgentInstanceHistoryCommitStatus toCommitStatus(
      final io.camunda.webapps.schema.entities.agenthistory.AgentHistoryCommitStatus status) {
    return switch (status) {
      case PENDING -> AgentInstanceHistoryCommitStatus.PENDING;
      case COMMITTED -> AgentInstanceHistoryCommitStatus.COMMITTED;
      case DISCARDED -> AgentInstanceHistoryCommitStatus.DISCARDED;
    };
  }

  private static List<ToolCall> toToolCalls(
      final List<AgentHistoryEmbeddedToolCallValue> toolCalls) {
    if (toolCalls == null) {
      return List.of();
    }
    return toolCalls.stream()
        .map(t -> new ToolCall(t.toolCallId(), t.toolName(), t.elementId(), t.arguments()))
        .toList();
  }

  /**
   * Returns null when all metric fields are null (metrics were never provided). When only some
   * fields are null (partial absence), constructs a {@link Metrics} preserving the available values
   * rather than losing them.
   */
  private static Metrics toMetrics(
      final Long inputTokens,
      final Long outputTokens,
      final Long reasoningTokenCount,
      final Long cacheCreationTokenCount,
      final Long cacheReadTokenCount,
      final Long durationMs) {
    if (inputTokens == null
        && outputTokens == null
        && reasoningTokenCount == null
        && cacheCreationTokenCount == null
        && cacheReadTokenCount == null
        && durationMs == null) {
      return null;
    }
    return new Metrics(
        inputTokens,
        outputTokens,
        reasoningTokenCount,
        cacheCreationTokenCount,
        cacheReadTokenCount,
        durationMs);
  }

  /** The tools available to the agent, as of this entry. CONFIGURATION items only. */
  private static List<Tool> toTools(final List<AgentHistoryToolValue> tools) {
    if (tools == null) {
      return List.of();
    }
    return tools.stream().map(t -> new Tool(t.name(), t.description(), t.elementId())).toList();
  }

  /**
   * The operational limits, as of this entry. CONFIGURATION items only. {@code -1} on any field
   * means "no limit configured".
   */
  private static Limits toLimits(final AgentHistoryLimitsValue limits) {
    if (limits == null) {
      return new Limits(-1, -1, -1);
    }
    return new Limits(limits.maxTokens(), limits.maxModelCalls(), limits.maxToolCalls());
  }
}
