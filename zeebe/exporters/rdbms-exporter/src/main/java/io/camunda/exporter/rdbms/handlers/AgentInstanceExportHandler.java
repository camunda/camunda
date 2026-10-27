/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel;
import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel.AgentInstanceToolDbValue;
import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel.Builder;
import io.camunda.db.rdbms.write.service.AgentInstanceWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.exporter.rdbms.utils.AgentContentMapper;
import io.camunda.exporter.rdbms.utils.DateUtil;
import io.camunda.exporter.rdbms.utils.ExportUtil;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentInstanceExportHandler implements RdbmsExportHandler<AgentInstanceRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentInstanceExportHandler.class);

  private static final Set<AgentInstanceIntent> EXPORTABLE_INTENTS =
      Set.of(
          AgentInstanceIntent.CREATED,
          AgentInstanceIntent.UPDATED,
          AgentInstanceIntent.COMPLETED,
          AgentInstanceIntent.MIGRATED);

  private final AgentInstanceWriter writer;

  public AgentInstanceExportHandler(final AgentInstanceWriter writer) {
    this.writer = writer;
  }

  @Override
  public boolean canExport(final Record<AgentInstanceRecordValue> record) {
    return record.getIntent() instanceof final AgentInstanceIntent intent
        && EXPORTABLE_INTENTS.contains(intent);
  }

  @Override
  public void export(final Record<AgentInstanceRecordValue> record) {
    final var intent = (AgentInstanceIntent) record.getIntent();
    final var model = mapToDbModel(record, intent);
    if (intent == AgentInstanceIntent.CREATED) {
      writer.create(model);
    } else {
      writer.update(model);
    }
  }

  private AgentInstanceDbModel mapToDbModel(
      final Record<AgentInstanceRecordValue> record, final AgentInstanceIntent intent) {
    final var value = record.getValue();
    final var timestamp = DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp()));

    final Builder builder =
        new Builder()
            .agentInstanceKey(record.getKey())
            .agentDefinitionKey(value.getAgentDefinitionKey())
            .elementId(value.getElementId())
            .processInstanceKey(value.getProcessInstanceKey())
            .rootProcessInstanceKey(value.getRootProcessInstanceKey())
            .processDefinitionId(value.getBpmnProcessId())
            .processDefinitionKey(value.getProcessDefinitionKey())
            .processDefinitionVersion(value.getProcessDefinitionVersion())
            .processDefinitionVersionTag(
                ExportUtil.emptyToNull(value.getProcessDefinitionVersionTag()))
            .tenantId(value.getTenantId())
            .partitionId(record.getPartitionId())
            .status(mapStatus(value.getStatus()))
            .model(value.getDefinition().getModel())
            .provider(value.getDefinition().getProvider())
            .maxTokens(value.getLimits().getMaxTokens())
            .maxModelCalls(value.getLimits().getMaxModelCalls())
            .maxToolCalls(value.getLimits().getMaxToolCalls())
            .inputTokens(value.getMetrics().getInputTokens())
            .outputTokens(value.getMetrics().getOutputTokens())
            .reasoningTokenCount(value.getMetrics().getReasoningTokenCount())
            .cacheCreationTokenCount(value.getMetrics().getCacheCreationTokenCount())
            .cacheReadTokenCount(value.getMetrics().getCacheReadTokenCount())
            .modelCalls(value.getMetrics().getModelCalls())
            .toolCalls(value.getMetrics().getToolCalls())
            .lastUpdatedDate(timestamp)
            .elementInstanceKeys(value.getElementInstanceKeys());

    // `systemPrompt`/`tools` can carry large payloads; on UPDATED, the engine only includes them
    // in the event when they actually changed (see AgentHistoryBatchBehavior). Leave the builder
    // field unset (null) otherwise, so AgentInstanceWriter's merge/update logic doesn't overwrite
    // the stored value with nothing.
    if (intent != AgentInstanceIntent.UPDATED
        || value.getChangedAttributes().contains(AgentInstanceRecordValue.ATTR_SYSTEM_PROMPT)) {
      builder.systemPromptItems(
          AgentContentMapper.mapContent(value.getDefinition().getSystemPrompt()));
    }
    if (intent != AgentInstanceIntent.UPDATED
        || value.getChangedAttributes().contains(AgentInstanceRecordValue.ATTR_TOOLS)) {
      builder.toolValues(toToolDbValues(value.getTools()));
    }

    if (intent == AgentInstanceIntent.CREATED) {
      builder.creationDate(timestamp);
    }

    if (intent == AgentInstanceIntent.COMPLETED) {
      builder.completionDate(timestamp);
    }

    return builder.build();
  }

  private static AgentInstanceStatus mapStatus(
      final io.camunda.zeebe.protocol.record.value.AgentInstanceStatus protocolStatus) {
    return switch (protocolStatus) {
      case INITIALIZING -> AgentInstanceStatus.INITIALIZING;
      case IDLE -> AgentInstanceStatus.IDLE;
      case THINKING -> AgentInstanceStatus.THINKING;
      case TOOL_CALLING -> AgentInstanceStatus.TOOL_CALLING;
      case TOOL_DISCOVERY -> AgentInstanceStatus.TOOL_DISCOVERY;
      case COMPLETED -> AgentInstanceStatus.COMPLETED;
      default -> {
        LOGGER.warn(
            "Received unexpected AgentInstanceStatus: {}, will be mapped to UNKNOWN",
            protocolStatus);
        yield AgentInstanceStatus.UNKNOWN;
      }
    };
  }

  private static List<AgentInstanceToolDbValue> toToolDbValues(
      final List<? extends AgentInstanceRecordValue.AgentInstanceToolValue> tools) {
    // Does NOT collapse an empty list to null: AgentInstanceDbModel#serializeTools relies on
    // "empty list" and "null" staying distinguishable so a UPDATED event that explicitly clears
    // tools isn't mistaken for one that never touched them.
    if (tools == null) {
      return null;
    }
    return tools.stream()
        .map(
            t ->
                new AgentInstanceToolDbValue(
                    t.getName(),
                    ExportUtil.emptyToNull(t.getDescription()),
                    ExportUtil.emptyToNull(t.getElementId())))
        .toList();
  }
}
