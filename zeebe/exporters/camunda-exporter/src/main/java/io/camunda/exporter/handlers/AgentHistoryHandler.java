/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.template.AgentHistoryTemplate.COMMIT_STATUS;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryCommitStatus;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryContentType;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity.AgentHistoryContentValue;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryRole;
import io.camunda.webapps.schema.entities.document.DocumentReferenceEntity;
import io.camunda.webapps.schema.entities.document.DocumentReferenceMetadataEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryEmbeddedToolCallValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryMessageContentValue;
import io.camunda.zeebe.protocol.record.value.DocumentReferenceValue;
import io.camunda.zeebe.util.DateUtil;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CamundaExporter handler for {@code AGENT_HISTORY} records.
 *
 * <ul>
 *   <li>{@code CREATED}: full upsert — all entity fields are populated from the record value.
 *   <li>{@code COMMITTED} / {@code DISCARDED}: {@code updateEntity()} still populates all fields
 *       defensively; {@code flush()} passes only {@code Map.of(COMMIT_STATUS, ...)} as updateFields
 *       so only {@code commitStatus} is written to the existing document.
 * </ul>
 */
public class AgentHistoryHandler
    implements ExportHandler<AgentHistoryEntity, AgentHistoryRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentHistoryHandler.class);

  private static final Set<AgentHistoryIntent> HANDLED_INTENTS =
      Set.of(
          AgentHistoryIntent.CREATED, AgentHistoryIntent.COMMITTED, AgentHistoryIntent.DISCARDED);

  private final String indexName;

  public AgentHistoryHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.AGENT_HISTORY;
  }

  @Override
  public Class<AgentHistoryEntity> getEntityType() {
    return AgentHistoryEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<AgentHistoryRecordValue> record) {
    return HANDLED_INTENTS.contains((AgentHistoryIntent) record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<AgentHistoryRecordValue> record) {
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public AgentHistoryEntity createNewEntity(final String id) {
    return new AgentHistoryEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<AgentHistoryRecordValue> record, final AgentHistoryEntity entity) {
    final AgentHistoryRecordValue value = record.getValue();
    final AgentHistoryIntent intent = (AgentHistoryIntent) record.getIntent();

    entity
        .setKey(record.getKey())
        .setPartitionId(record.getPartitionId())
        .setAgentInstanceKey(value.getAgentInstanceKey())
        .setElementInstanceKey(value.getElementInstanceKey())
        .setProcessInstanceKey(value.getProcessInstanceKey())
        .setRootProcessInstanceKey(value.getRootProcessInstanceKey())
        .setProcessDefinitionKey(value.getProcessDefinitionKey())
        .setTenantId(value.getTenantId())
        .setBpmnProcessId(value.getBpmnProcessId())
        .setJobKey(value.getJobKey())
        .setJobLease(value.getJobLease())
        .setIteration(ExporterUtil.positiveOrNull(value.getIteration()))
        .setRole(mapRole(value.getRole()))
        .setCommitStatus(mapCommitStatusFromIntent(intent))
        .setProducedAt(
            value.getProducedAt() > 0
                ? DateUtil.toOffsetDateTime(Instant.ofEpochMilli(value.getProducedAt()))
                : null)
        .setInputTokens(value.getMetrics().getInputTokens())
        .setOutputTokens(value.getMetrics().getOutputTokens())
        .setDurationMs(value.getMetrics().getDurationMs())
        .setContent(mapContent(value.getContent()))
        .setToolCalls(mapToolCalls(value.getToolCalls()));
  }

  @Override
  public void flush(final AgentHistoryEntity entity, final BatchRequest batchRequest) {
    // Only commitStatus changes after CREATED. Pass it as the sole update-fields entry so
    // partial updates on COMMITTED/DISCARDED never overwrite other fields in the document.
    batchRequest.upsert(
        indexName, entity.getId(), entity, Map.of(COMMIT_STATUS, entity.getCommitStatus()));
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private static AgentHistoryRole mapRole(
      final io.camunda.zeebe.protocol.record.value.AgentHistoryRole protocolRole) {
    return switch (protocolRole) {
      case USER -> AgentHistoryRole.USER;
      case ASSISTANT -> AgentHistoryRole.ASSISTANT;
      case TOOL_RESULT -> AgentHistoryRole.TOOL_RESULT;
      default -> {
        LOGGER.warn(
            "Received unexpected AgentHistoryRole: {}, will be mapped to UNKNOWN", protocolRole);
        yield AgentHistoryRole.UNKNOWN;
      }
    };
  }

  private static AgentHistoryCommitStatus mapCommitStatusFromIntent(
      final AgentHistoryIntent intent) {
    return switch (intent) {
      case CREATED -> AgentHistoryCommitStatus.PENDING;
      case COMMITTED -> AgentHistoryCommitStatus.COMMITTED;
      case DISCARDED -> AgentHistoryCommitStatus.DISCARDED;
      default -> {
        LOGGER.warn(
            "Received unexpected AgentHistoryIntent: {}, will be mapped to UNKNOWN", intent);
        yield AgentHistoryCommitStatus.UNKNOWN;
      }
    };
  }

  private static List<AgentHistoryContentValue> mapContent(
      final List<? extends AgentHistoryMessageContentValue> content) {
    return content.stream()
        .map(
            c ->
                switch (c.getContentType()) {
                  case TEXT -> AgentHistoryContentValue.text(ExporterUtil.emptyToNull(c.getText()));
                  case DOCUMENT ->
                      AgentHistoryContentValue.document(
                          mapDocumentReference(c.getDocumentReference()));
                  case OBJECT -> AgentHistoryContentValue.object(c.getObject());
                  default -> {
                    LOGGER.warn(
                        "Received unexpected AgentHistoryContentType: {}, will be mapped to UNKNOWN",
                        c.getContentType());
                    yield new AgentHistoryContentValue(
                        AgentHistoryContentType.UNKNOWN,
                        ExporterUtil.emptyToNull(c.getText()),
                        mapDocumentReference(c.getDocumentReference()),
                        c.getObject().isEmpty() ? null : c.getObject());
                  }
                })
        .toList();
  }

  private static DocumentReferenceEntity mapDocumentReference(final DocumentReferenceValue ref) {
    if (ref == null) {
      return null;
    }

    final var meta = ref.getMetadata();
    final var expiresAt =
        meta.getExpiresAt() > 0
            ? DateUtil.toOffsetDateTime(Instant.ofEpochMilli(meta.getExpiresAt()))
            : null;
    return new DocumentReferenceEntity(
        ref.getDocumentId(),
        ref.getStoreId(),
        ref.getContentHash(),
        new DocumentReferenceMetadataEntity(
            meta.getContentType(),
            meta.getFileName(),
            expiresAt,
            meta.getSize(),
            ExporterUtil.emptyToNull(meta.getProcessDefinitionId()),
            ExporterUtil.positiveOrNull(meta.getProcessInstanceKey()),
            meta.getCustomProperties()));
  }

  private static List<
          io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity
              .AgentHistoryEmbeddedToolCallValue>
      mapToolCalls(final List<? extends AgentHistoryEmbeddedToolCallValue> toolCalls) {
    return toolCalls.stream()
        .map(
            t ->
                new io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity
                    .AgentHistoryEmbeddedToolCallValue(
                    t.getToolCallId(),
                    t.getToolName(),
                    ExporterUtil.emptyToNull(t.getElementId()),
                    t.getArguments()))
        .toList();
  }
}
