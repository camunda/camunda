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
import io.camunda.exporter.rdbms.utils.DateUtil;
import io.camunda.exporter.rdbms.utils.ExportUtil;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryCommitStatus;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryRole;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ContentItem;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ContentItem.ContentType;
import io.camunda.search.entities.AgentInstanceHistoryEntity.DocumentMetadata;
import io.camunda.search.entities.AgentInstanceHistoryEntity.DocumentReference;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ToolCall;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryEmbeddedToolCallValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryMessageContentValue;
import io.camunda.zeebe.protocol.record.value.DocumentReferenceValue;
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

    return new Builder()
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
        .iteration(ExportUtil.positiveOrNull(value.getIteration()))
        .role(mapRole(value.getRole()))
        .commitStatus(mapCommitStatus(intent))
        .producedAt(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(producedAtMillis)))
        .inputTokens(value.getMetrics().getInputTokens())
        .outputTokens(value.getMetrics().getOutputTokens())
        .durationMs(value.getMetrics().getDurationMs())
        .contentItems(mapContent(value.getContent()))
        .toolCallValues(mapToolCalls(value.getToolCalls()))
        .build();
  }

  private static AgentInstanceHistoryRole mapRole(
      final io.camunda.zeebe.protocol.record.value.AgentHistoryRole protocolRole) {
    return switch (protocolRole) {
      case USER -> AgentInstanceHistoryRole.USER;
      case ASSISTANT -> AgentInstanceHistoryRole.ASSISTANT;
      case TOOL_RESULT -> AgentInstanceHistoryRole.TOOL_RESULT;
      case UNSPECIFIED ->
          throw new IllegalStateException(
              "should never happen — protocol UNSPECIFIED is always overwritten before export");
    };
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

  private static List<ContentItem> mapContent(
      final List<? extends AgentHistoryMessageContentValue> content) {
    return content.stream()
        .map(
            c ->
                switch (c.getContentType()) {
                  case TEXT ->
                      new ContentItem(
                          ContentType.TEXT, ExportUtil.emptyToNull(c.getText()), null, null);
                  case DOCUMENT ->
                      new ContentItem(
                          ContentType.DOCUMENT,
                          null,
                          mapDocumentReference(c.getDocumentReference()),
                          null);
                  case OBJECT -> new ContentItem(ContentType.OBJECT, null, null, c.getObject());
                  case UNSPECIFIED ->
                      throw new IllegalStateException(
                          "should never happen — protocol UNSPECIFIED is always overwritten before export");
                })
        .toList();
  }

  private static DocumentReference mapDocumentReference(final DocumentReferenceValue ref) {
    if (ref == null) {
      return null;
    }

    final var meta = ref.getMetadata();
    final var expiresAt =
        meta.getExpiresAt() > 0
            ? DateUtil.toOffsetDateTime(Instant.ofEpochMilli(meta.getExpiresAt()))
            : null;
    final Long processInstanceKey =
        meta.getProcessInstanceKey() > 0 ? meta.getProcessInstanceKey() : null;
    return new DocumentReference(
        ref.getStoreId(),
        ref.getDocumentId(),
        ExportUtil.emptyToNull(ref.getContentHash()),
        new DocumentMetadata(
            meta.getContentType(),
            meta.getFileName(),
            expiresAt,
            meta.getSize(),
            ExportUtil.emptyToNull(meta.getProcessDefinitionId()),
            processInstanceKey,
            meta.getCustomProperties()));
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
