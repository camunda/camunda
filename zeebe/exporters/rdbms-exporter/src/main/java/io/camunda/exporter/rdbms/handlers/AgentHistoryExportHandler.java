/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.AgentHistoryDbModel;
import io.camunda.db.rdbms.write.domain.AgentHistoryDbModel.Builder;
import io.camunda.db.rdbms.write.service.AgentHistoryWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.exporter.rdbms.utils.AgentContentMapper;
import io.camunda.exporter.rdbms.utils.DateUtil;
import io.camunda.exporter.rdbms.utils.ExportUtil;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryCommitStatus;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryRole;
import io.camunda.search.entities.AgentInstanceHistoryEntity.Tool;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ToolCall;
import io.camunda.search.entities.ContentItem;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryEmbeddedToolCallValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryMessageContentValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue.AgentInstanceLimitsValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue.AgentInstanceToolValue;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public class AgentHistoryExportHandler implements RdbmsExportHandler<AgentHistoryRecordValue> {

  private static final Set<AgentHistoryIntent> EXPORTABLE_INTENTS =
      Set.of(
          AgentHistoryIntent.CREATED, AgentHistoryIntent.COMMITTED, AgentHistoryIntent.DISCARDED);

  private final AgentHistoryWriter writer;

  public AgentHistoryExportHandler(final AgentHistoryWriter writer) {
    this.writer = writer;
  }

  @Override
  public boolean canExport(final Record<AgentHistoryRecordValue> record) {
    return record.getIntent() instanceof final AgentHistoryIntent intent
        && EXPORTABLE_INTENTS.contains(intent);
  }

  @Override
  public void export(final Record<AgentHistoryRecordValue> record) {
    final var intent = (AgentHistoryIntent) record.getIntent();
    final var model = mapToDbModel(record, intent);
    if (intent == AgentHistoryIntent.CREATED) {
      writer.create(model);
    } else {
      writer.updateCommitStatus(model);
    }
  }

  private AgentHistoryDbModel mapToDbModel(
      final Record<AgentHistoryRecordValue> record, final AgentHistoryIntent intent) {
    final var value = record.getValue();
    final long producedAtMillis =
        value.getProducedAt() > 0 ? value.getProducedAt() : record.getTimestamp();

    final var builder =
        new Builder()
            .agentHistoryKey(record.getKey())
            .agentInstanceKey(value.getAgentInstanceKey())
            .elementInstanceKey(value.getElementInstanceKey())
            .processInstanceKey(value.getProcessInstanceKey())
            .rootProcessInstanceKey(value.getRootProcessInstanceKey())
            .processDefinitionId(value.getBpmnProcessId())
            .processDefinitionKey(value.getProcessDefinitionKey())
            .tenantId(value.getTenantId())
            .partitionId(record.getPartitionId())
            .jobKey(value.getJobKey())
            .jobLease(value.getJobLease())
            .loopIteration(value.getLoopIteration())
            .role(mapRole(value.getRole()))
            .commitStatus(mapCommitStatus(intent))
            .producedAt(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(producedAtMillis)))
            .inputTokens(ExportUtil.nullIfNegative(value.getMetrics().getInputTokens()))
            .outputTokens(ExportUtil.nullIfNegative(value.getMetrics().getOutputTokens()))
            .reasoningTokenCount(
                ExportUtil.nullIfNegative(value.getMetrics().getReasoningTokenCount()))
            .cacheCreationTokenCount(
                ExportUtil.nullIfNegative(value.getMetrics().getCacheCreationTokenCount()))
            .cacheReadTokenCount(
                ExportUtil.nullIfNegative(value.getMetrics().getCacheReadTokenCount()))
            .durationMs(ExportUtil.nullIfNegative(value.getMetrics().getDurationMs()))
            .contentItems(AgentContentMapper.mapContent(value.getContent()))
            .toolCallValues(mapToolCalls(value.getToolCalls()))
            .historyItemId(ExportUtil.emptyToNull(value.getHistoryItemId()))
            .toolValues(mapTools(value.getTools()))
            .model(ExportUtil.emptyToNull(value.getModel()))
            .provider(ExportUtil.emptyToNull(value.getProvider()))
            .systemPromptItems(mapSystemPrompt(value.getSystemPrompt()));
    applyLimits(builder, value.getLimits());
    return builder.build();
  }

  private static AgentInstanceHistoryRole mapRole(
      final io.camunda.zeebe.protocol.record.value.AgentHistoryRole protocolRole) {
    return switch (protocolRole) {
      case USER -> AgentInstanceHistoryRole.USER;
      case ASSISTANT -> AgentInstanceHistoryRole.ASSISTANT;
      case TOOL_RESULT -> AgentInstanceHistoryRole.TOOL_RESULT;
      case CONFIGURATION -> AgentInstanceHistoryRole.CONFIGURATION;
      case UNSPECIFIED ->
          throw new IllegalStateException(
              "should never happen — protocol UNSPECIFIED is always overwritten before export");
    };
  }

  /** The tools available to the agent, as of this entry. CONFIGURATION items only. */
  private static List<Tool> mapTools(final List<? extends AgentInstanceToolValue> tools) {
    if (tools == null || tools.isEmpty()) {
      return List.of();
    }
    return tools.stream()
        .map(
            t ->
                new Tool(
                    t.getName(),
                    ExportUtil.emptyToNull(t.getDescription()),
                    ExportUtil.emptyToNull(t.getElementId())))
        .toList();
  }

  /**
   * The operational limits, as of this entry. CONFIGURATION items only. Leaves a DB column null
   * (rather than writing {@code -1} literally) when its sub-field isn't configured, independently
   * per sub-field — {@link io.camunda.db.rdbms.read.mapper.AgentHistoryEntityMapper} defaults each
   * back to {@code -1} on read.
   */
  private static void applyLimits(final Builder builder, final AgentInstanceLimitsValue limits) {
    if (limits == null) {
      return;
    }
    builder
        .maxTokens(ExportUtil.nullIfNegative(limits.getMaxTokens()))
        .maxModelCalls(ExportUtil.nullIfNegative(limits.getMaxModelCalls()))
        .maxToolCalls(ExportUtil.nullIfNegative(limits.getMaxToolCalls()));
  }

  /** The system prompt, as content blocks, as of this entry. CONFIGURATION items only. */
  private static List<ContentItem> mapSystemPrompt(
      final List<? extends AgentHistoryMessageContentValue> systemPrompt) {
    if (systemPrompt == null || systemPrompt.isEmpty()) {
      return List.of();
    }
    return AgentContentMapper.mapContent(systemPrompt);
  }

  private static AgentInstanceHistoryCommitStatus mapCommitStatus(final AgentHistoryIntent intent) {
    return switch (intent) {
      case CREATED -> AgentInstanceHistoryCommitStatus.PENDING;
      case COMMITTED -> AgentInstanceHistoryCommitStatus.COMMITTED;
      case DISCARDED -> AgentInstanceHistoryCommitStatus.DISCARDED;
      default ->
          throw new IllegalStateException(
              "Unexpected AgentHistoryIntent on an exported record: " + intent);
    };
  }

  private static List<ToolCall> mapToolCalls(
      final List<? extends AgentHistoryEmbeddedToolCallValue> toolCalls) {
    return toolCalls.stream()
        .map(
            t ->
                new ToolCall(
                    t.getToolCallId(),
                    t.getToolName(),
                    ExportUtil.emptyToNull(t.getElementId()),
                    t.getArguments()))
        .toList();
  }
}
