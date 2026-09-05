/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.security.core.authz.TenantOwnedEntity;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Search-domain representation of a single AGENT_HISTORY item. Technology-neutral — consumed by the
 * REST mapper and produced by the ES/OS and RDBMS readers.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentInstanceHistoryEntity(
    Long historyItemKey,
    String historyItemId,
    Long agentInstanceKey,
    Long elementInstanceKey,
    Long processInstanceKey,
    Long processDefinitionKey,
    String processDefinitionId,
    String tenantId,
    Long jobKey,
    String jobLease,
    Integer loopIteration,
    AgentInstanceHistoryRole role,
    List<ContentItem> content,
    List<ToolCall> toolCalls,
    @Nullable Metrics metrics,
    List<Tool> tools,
    @Nullable String model,
    @Nullable String provider,
    Limits limits,
    List<ContentItem> systemPrompt,
    AgentInstanceHistoryCommitStatus commitStatus,
    OffsetDateTime producedAt)
    implements TenantOwnedEntity {

  public AgentInstanceHistoryEntity {
    Objects.requireNonNull(historyItemKey, "historyItemKey");
    Objects.requireNonNull(historyItemId, "historyItemId");
    Objects.requireNonNull(agentInstanceKey, "agentInstanceKey");
    Objects.requireNonNull(elementInstanceKey, "elementInstanceKey");
    Objects.requireNonNull(processInstanceKey, "processInstanceKey");
    Objects.requireNonNull(processDefinitionKey, "processDefinitionKey");
    Objects.requireNonNull(processDefinitionId, "processDefinitionId");
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(jobKey, "jobKey");
    Objects.requireNonNull(jobLease, "jobLease");
    Objects.requireNonNull(loopIteration, "loopIteration");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(commitStatus, "commitStatus");
    Objects.requireNonNull(producedAt, "producedAt");
    // Mutable lists required — readers may hydrate by calling .add()
    content = content != null ? new ArrayList<>(content) : new ArrayList<>();
    toolCalls = toolCalls != null ? new ArrayList<>(toolCalls) : new ArrayList<>();
    tools = tools != null ? new ArrayList<>(tools) : new ArrayList<>();
    systemPrompt = systemPrompt != null ? new ArrayList<>(systemPrompt) : new ArrayList<>();
    limits = limits != null ? limits : new Limits(-1, -1, -1);
  }

  public enum AgentInstanceHistoryRole {
    USER,
    ASSISTANT,
    TOOL_RESULT,
    CONFIGURATION
  }

  public enum AgentInstanceHistoryCommitStatus {
    COMMITTED,
    PENDING,
    DISCARDED
  }

  /** A tool call embedded in a history item. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ToolCall(
      String toolCallId,
      String toolName,
      @Nullable String elementId,
      @Nullable Map<String, Object> arguments) {

    public ToolCall {
      Objects.requireNonNull(toolCallId, "toolCallId");
      Objects.requireNonNull(toolName, "toolName");
      arguments = arguments != null ? new HashMap<>(arguments) : new HashMap<>();
    }
  }

  /** Per-call token and latency metrics. Null when metrics were not provided at creation time. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Metrics(
      @Nullable Long inputTokens,
      @Nullable Long outputTokens,
      @Nullable Long reasoningTokenCount,
      @Nullable Long cacheCreationTokenCount,
      @Nullable Long cacheReadTokenCount,
      @Nullable Long durationMs) {}

  /** A tool made available to the agent by a CONFIGURATION item. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Tool(String name, @Nullable String description, @Nullable String elementId) {
    public Tool {
      Objects.requireNonNull(name, "name");
    }
  }

  /**
   * The limits set by a CONFIGURATION item. {@code -1} on any field means "no limit configured" for
   * that dimension — same convention as {@link
   * io.camunda.search.entities.AgentInstanceEntity.AgentInstanceLimits}.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Limits(long maxTokens, int maxModelCalls, int maxToolCalls) {}
}
