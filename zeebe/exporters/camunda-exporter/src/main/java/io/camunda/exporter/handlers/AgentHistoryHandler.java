/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.template.AgentHistoryTemplate.COMMIT_STATUS;

import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.AgentContentMapper;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryCommitStatus;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryContentValue;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity.AgentHistoryLimitsValue;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity.AgentHistoryToolValue;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryEmbeddedToolCallValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryMessageContentValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue.AgentInstanceLimitsValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue.AgentInstanceToolValue;
import io.camunda.zeebe.util.DateUtil;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CamundaExporter handler for {@code AGENT_HISTORY} records.
 *
 * <ul>
 *   <li>{@code CREATED}: full upsert — every entity field is populated from the record value.
 *   <li>{@code COMMITTED} / {@code DISCARDED}: the engine only retains identity fields once an item
 *       is committed/discarded, so the record value's non-identity fields are trimmed and must not
 *       be applied to the entity — {@code updateEntity()} leaves those fields untouched, updating
 *       only {@code commitStatus} and the other identity fields. {@code flush()} additionally
 *       passes only {@code Map.of(COMMIT_STATUS, ...)} as updateFields so only {@code commitStatus}
 *       is written to the existing document.
 * </ul>
 */
public class AgentHistoryHandler
    implements OrdinalIndexExportHandler<AgentHistoryEntity, AgentHistoryRecordValue> {

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
        .setBpmnProcessId(value.getBpmnProcessId())
        .setProcessDefinitionKey(value.getProcessDefinitionKey())
        .setTenantId(value.getTenantId())
        .setJobKey(value.getJobKey())
        .setJobLease(value.getJobLease())
        .setLoopIteration(value.getLoopIteration())
        .setRole(mapRole(value.getRole()))
        .setCommitStatus(mapCommitStatusFromIntent(intent));

    // Guarded to CREATED only: ExporterBatchWriter shares one mutable entity across a batch, so a
    // same-batch COMMITTED/DISCARDED call would otherwise clobber these with its trimmed values —
    // AgentHistoryCreatedApplier in primary storage doesn't retain these fields past CREATED
    // either (see that class), so COMMITTED/DISCARDED events carry them as empty/default rather
    // than the original CREATED values; guarding to CREATED avoids clobbering.
    if (intent == AgentHistoryIntent.CREATED) {
      entity
          .setProducedAt(
              DateUtil.toOffsetDateTime(
                  Instant.ofEpochMilli(
                      value.getProducedAt() > 0 ? value.getProducedAt() : record.getTimestamp())))
          .setInputTokens(ExporterUtil.nullIfNegative(value.getMetrics().getInputTokens()))
          .setOutputTokens(ExporterUtil.nullIfNegative(value.getMetrics().getOutputTokens()))
          .setReasoningTokenCount(
              ExporterUtil.nullIfNegative(value.getMetrics().getReasoningTokenCount()))
          .setCacheCreationTokenCount(
              ExporterUtil.nullIfNegative(value.getMetrics().getCacheCreationTokenCount()))
          .setCacheReadTokenCount(
              ExporterUtil.nullIfNegative(value.getMetrics().getCacheReadTokenCount()))
          .setDurationMs(ExporterUtil.nullIfNegative(value.getMetrics().getDurationMs()))
          .setContent(AgentContentMapper.mapContent(value.getContent()))
          .setToolCalls(mapToolCalls(value.getToolCalls()))
          .setHistoryItemId(ExporterUtil.emptyToNull(value.getHistoryItemId()))
          .setTools(mapTools(value.getTools()))
          .setModel(ExporterUtil.emptyToNull(value.getModel()))
          .setProvider(ExporterUtil.emptyToNull(value.getProvider()))
          .setLimits(mapLimits(value.getLimits()))
          .setSystemPrompt(mapSystemPrompt(value.getSystemPrompt()));
    }
  }

  @Override
  public void flush(
      final TargetIndex index, final AgentHistoryEntity entity, final BatchRequest batchRequest) {
    // Only commitStatus changes after CREATED. Pass it as the sole update-fields entry so
    // partial updates on COMMITTED/DISCARDED never overwrite other fields in the document.
    batchRequest.upsert(
        index, entity.getId(), entity, Map.of(COMMIT_STATUS, entity.getCommitStatus()));
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
      case CONFIGURATION -> AgentHistoryRole.CONFIGURATION;
      // should never happen — protocol UNSPECIFIED is always overwritten before export
      case UNSPECIFIED ->
          throw new IllegalStateException(
              "Unexpected UNSPECIFIED AgentHistoryRole on an exported record");
    };
  }

  private static AgentHistoryCommitStatus mapCommitStatusFromIntent(
      final AgentHistoryIntent intent) {
    // should never happen — handlesRecord() gates updateEntity() to the three event intents
    return switch (intent) {
      case CREATED -> AgentHistoryCommitStatus.PENDING;
      case COMMITTED -> AgentHistoryCommitStatus.COMMITTED;
      case DISCARDED -> AgentHistoryCommitStatus.DISCARDED;
      default ->
          throw new IllegalStateException(
              "Unexpected AgentHistoryIntent on an exported record: " + intent);
    };
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

  /** The tools available to the agent, as of this entry. CONFIGURATION items only. */
  private static List<AgentHistoryToolValue> mapTools(
      final List<? extends AgentInstanceToolValue> tools) {
    if (tools == null || tools.isEmpty()) {
      return List.of();
    }
    return tools.stream()
        .map(
            t ->
                new AgentHistoryToolValue(
                    t.getName(),
                    ExporterUtil.emptyToNull(t.getDescription()),
                    ExporterUtil.emptyToNull(t.getElementId())))
        .toList();
  }

  /**
   * The operational limits, as of this entry. CONFIGURATION items only. {@code -1} on any field
   * means "no limit configured".
   */
  private static AgentHistoryLimitsValue mapLimits(final AgentInstanceLimitsValue limits) {
    if (limits == null) {
      return new AgentHistoryLimitsValue(-1L, -1, -1);
    }
    return new AgentHistoryLimitsValue(
        limits.getMaxTokens(), limits.getMaxModelCalls(), limits.getMaxToolCalls());
  }

  /** The system prompt, as content blocks, as of this entry. CONFIGURATION items only. */
  private static List<AgentHistoryContentValue> mapSystemPrompt(
      final List<? extends AgentHistoryMessageContentValue> systemPrompt) {
    if (systemPrompt == null || systemPrompt.isEmpty()) {
      return List.of();
    }
    return AgentContentMapper.mapContent(systemPrompt);
  }
}
