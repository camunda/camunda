/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.emptyToNull;
import static io.camunda.exporter.utils.ExporterUtil.nullIfNegative;
import static io.camunda.exporter.utils.ExporterUtil.positiveOrNull;
import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;

import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.webapps.schema.entities.variablehistory.VariableHistoryActorType;
import io.camunda.webapps.schema.entities.variablehistory.VariableHistoryEntity;
import io.camunda.webapps.schema.entities.variablehistory.VariableHistoryOperationType;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.variablehistory.VariableHistory;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.util.DateUtil;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * Writes one document per recorded variable change to the variable history. The variable handler
 * keeps writing the current value, which this handler does not replace.
 */
@NullMarked
public class VariableHistoryHandler
    implements OrdinalIndexExportHandler<VariableHistoryEntity, VariableRecordValue> {

  private final String indexName;
  private final int variableSizeThreshold;
  private final AuditLogConfiguration auditLogConfiguration;

  public VariableHistoryHandler(
      final String indexName,
      final int variableSizeThreshold,
      final AuditLogConfiguration auditLogConfiguration) {
    this.indexName = indexName;
    this.variableSizeThreshold = variableSizeThreshold;
    this.auditLogConfiguration = auditLogConfiguration;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.VARIABLE;
  }

  @Override
  public Class<VariableHistoryEntity> getEntityType() {
    return VariableHistoryEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<VariableRecordValue> record) {
    return VariableHistory.isRecorded(record, auditLogConfiguration);
  }

  /**
   * The partition and the log position identify the event, so an event that is exported again
   * overwrites its document. This is the document id only, the sort key is {@code
   * variableHistoryItemKey}.
   */
  @Override
  public List<String> generateIds(final Record<VariableRecordValue> record) {
    return List.of(record.getPartitionId() + "-" + record.getPosition());
  }

  @Override
  public VariableHistoryEntity createNewEntity(final String id) {
    return new VariableHistoryEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<VariableRecordValue> record, final VariableHistoryEntity entity) {
    final var recordValue = record.getValue();
    final var source = recordValue.getSource();
    final var actor = VariableHistory.actorOf(record);

    entity
        .setVariableHistoryItemKey(
            Protocol.encodePartitionId(record.getPartitionId(), record.getPosition()))
        .setVariableKey(record.getKey())
        .setName(recordValue.getName())
        .setScopeKey(recordValue.getScopeKey())
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setRootProcessInstanceKey(positiveOrNull(recordValue.getRootProcessInstanceKey()))
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setProcessDefinitionId(recordValue.getBpmnProcessId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()))
        .setTimestamp(DateUtil.toOffsetDateTime(record.getTimestamp()))
        .setOperationType(
            record.getIntent() == VariableIntent.CREATED
                ? VariableHistoryOperationType.CREATE
                : VariableHistoryOperationType.UPDATE)
        .setVariableOperationType(source.getType())
        // an event from before the upgrade has a type but no causing element, which the protocol
        // reports as -1 and an empty string
        .setElementInstanceKey(nullIfNegative(source.getElementInstanceKey()))
        .setElementId(emptyToNull(source.getElementId()))
        .setActorType(actorTypeOf(actor.type()))
        .setActorId(actor.id())
        .setAgentElementId(actor.agentElementId());

    // a preview above the threshold, as in the variable handler, but no cut of a growing list yet
    final var value = recordValue.getValue();
    if (value.length() > variableSizeThreshold) {
      entity.setValue(value.substring(0, variableSizeThreshold));
      entity.setFullValue(value);
      entity.setIsPreview(true);
    } else {
      entity.setValue(value);
      entity.setFullValue(null);
      entity.setIsPreview(false);
    }
  }

  @Override
  public void flush(
      final TargetIndex index,
      final VariableHistoryEntity entity,
      final BatchRequest batchRequest) {
    batchRequest.add(index, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private static VariableHistoryActorType actorTypeOf(final AuditLogActorType type) {
    return switch (type) {
      case USER -> VariableHistoryActorType.USER;
      case CLIENT -> VariableHistoryActorType.CLIENT;
      case ENGINE -> VariableHistoryActorType.ENGINE;
      // An anonymous change is not recorded, so only the unknown actor can get here. Mapping it
      // instead of throwing keeps a mistake in the check from blocking the exporter.
      case UNKNOWN, ANONYMOUS -> VariableHistoryActorType.UNKNOWN;
    };
  }
}
