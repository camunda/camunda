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
import io.camunda.search.entities.AgentInstanceHistoryEntity.ContentItem;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ContentItem.ContentType;
import io.camunda.search.entities.AgentInstanceHistoryEntity.DocumentMetadata;
import io.camunda.search.entities.AgentInstanceHistoryEntity.DocumentReference;
import io.camunda.search.entities.AgentInstanceHistoryEntity.Limits;
import io.camunda.search.entities.AgentInstanceHistoryEntity.Metrics;
import io.camunda.search.entities.AgentInstanceHistoryEntity.Tool;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ToolCall;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryContentValue;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity.AgentHistoryEmbeddedToolCallValue;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity.AgentHistoryLimitsValue;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity.AgentHistoryToolValue;
import io.camunda.webapps.schema.entities.document.DocumentReferenceEntity;
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
        toContent(source.getContent()),
        toToolCalls(source.getToolCalls()),
        toMetrics(source.getInputTokens(), source.getOutputTokens(), source.getDurationMs()),
        toTools(source.getTools()),
        source.getModel(),
        source.getProvider(),
        toLimits(source.getLimits()),
        toSystemPrompt(source.getSystemPrompt()),
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

  private static List<ContentItem> toContent(final List<AgentHistoryContentValue> content) {
    if (content == null) {
      return List.of();
    }
    return content.stream().map(AgentHistoryEntityTransformer::toContentItem).toList();
  }

  private static ContentItem toContentItem(final AgentHistoryContentValue value) {
    return switch (value.contentType()) {
      case TEXT -> new ContentItem(ContentType.TEXT, value.text(), null, null);
      case DOCUMENT ->
          new ContentItem(
              ContentType.DOCUMENT, null, toDocumentReference(value.documentReference()), null);
      case OBJECT -> new ContentItem(ContentType.OBJECT, null, null, value.object());
    };
  }

  private static DocumentReference toDocumentReference(final DocumentReferenceEntity entity) {
    if (entity == null) {
      return null;
    }
    final var meta = entity.metadata();
    return new DocumentReference(
        entity.storeId(),
        entity.documentId(),
        entity.contentHash(),
        new DocumentMetadata(
            meta.contentType(),
            meta.fileName(),
            meta.expiresAt(),
            meta.size(),
            meta.processDefinitionId(),
            meta.processInstanceKey(),
            meta.customProperties()));
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
   * Returns null when all three metric fields are null (metrics were never provided). When only
   * some fields are null (partial absence), constructs a {@link Metrics} preserving the available
   * values rather than losing them.
   */
  private static Metrics toMetrics(
      final Long inputTokens, final Long outputTokens, final Long durationMs) {
    if (inputTokens == null && outputTokens == null && durationMs == null) {
      return null;
    }
    return new Metrics(inputTokens, outputTokens, durationMs);
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

  /** The system prompt, as content blocks, as of this entry. CONFIGURATION items only. */
  private static List<ContentItem> toSystemPrompt(
      final List<AgentHistoryContentValue> systemPrompt) {
    if (systemPrompt == null) {
      return List.of();
    }
    return systemPrompt.stream().map(AgentHistoryEntityTransformer::toContentItem).toList();
  }
}
