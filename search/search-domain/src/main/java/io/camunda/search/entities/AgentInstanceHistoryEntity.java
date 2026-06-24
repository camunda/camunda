/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
    Long agentInstanceKey,
    Long elementInstanceKey,
    Long processInstanceKey,
    Long processDefinitionKey,
    String processDefinitionId,
    String tenantId,
    Long jobKey,
    String jobLease,
    @Nullable Integer iteration,
    AgentInstanceHistoryRole role,
    List<ContentItem> content,
    List<ToolCall> toolCalls,
    Metrics metrics,
    AgentInstanceHistoryCommitStatus commitStatus,
    OffsetDateTime producedAt)
    implements TenantOwnedEntity {

  public AgentInstanceHistoryEntity {
    Objects.requireNonNull(historyItemKey, "historyItemKey");
    Objects.requireNonNull(agentInstanceKey, "agentInstanceKey");
    Objects.requireNonNull(elementInstanceKey, "elementInstanceKey");
    Objects.requireNonNull(processInstanceKey, "processInstanceKey");
    Objects.requireNonNull(processDefinitionKey, "processDefinitionKey");
    Objects.requireNonNull(processDefinitionId, "processDefinitionId");
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(jobKey, "jobKey");
    Objects.requireNonNull(jobLease, "jobLease");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(metrics, "metrics");
    Objects.requireNonNull(commitStatus, "commitStatus");
    Objects.requireNonNull(producedAt, "producedAt");
    // Mutable lists required — readers may hydrate by calling .add()
    content = content != null ? new ArrayList<>(content) : new ArrayList<>();
    toolCalls = toolCalls != null ? new ArrayList<>(toolCalls) : new ArrayList<>();
  }

  public enum AgentInstanceHistoryRole {
    USER,
    ASSISTANT,
    TOOL_RESULT
  }

  public enum AgentInstanceHistoryCommitStatus {
    COMMITTED,
    PENDING,
    DISCARDED
  }

  /**
   * A single content block within a history item. The {@code text}, {@code documentReference}, and
   * {@code object} fields are mutually exclusive based on {@code contentType}.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ContentItem(
      ContentType contentType,
      @Nullable String text,
      @Nullable DocumentReference documentReference,
      @Nullable Map<String, Object> object) {

    public ContentItem {
      Objects.requireNonNull(contentType, "contentType");
      object = object != null ? new HashMap<>(object) : new HashMap<>();
    }

    public enum ContentType {
      TEXT,
      DOCUMENT,
      OBJECT
    }
  }

  /** A document reference embedded in a {@link ContentItem}. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DocumentReference(
      String storeId, String documentId, @Nullable String contentHash, DocumentMetadata metadata) {

    public DocumentReference {
      Objects.requireNonNull(storeId, "storeId");
      Objects.requireNonNull(documentId, "documentId");
      Objects.requireNonNull(metadata, "metadata");
    }
  }

  /** Metadata for a {@link DocumentReference}. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DocumentMetadata(
      String contentType,
      String fileName,
      @Nullable OffsetDateTime expiresAt,
      Long size,
      @Nullable String processDefinitionId,
      @Nullable Long processInstanceKey,
      Map<String, Object> customProperties) {

    public DocumentMetadata {
      Objects.requireNonNull(contentType, "contentType");
      Objects.requireNonNull(fileName, "fileName");
      Objects.requireNonNull(size, "size");
      customProperties =
          customProperties != null ? new HashMap<>(customProperties) : new HashMap<>();
    }
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

  /** Per-call token and latency metrics. Zero-valued rather than null when not available. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Metrics(long inputTokens, long outputTokens, long durationMs) {}
}
