/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.auditlog;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationResult;
import io.camunda.zeebe.exporter.common.handlers.auditlog.AuditLogCommonHandler;
import io.camunda.zeebe.exporter.common.handlers.auditlog.AuditLogOperationTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.DateUtil;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * A generic handler for audit log records that delegates record-type-specific transformation to an
 * {@link AuditLogOperationTransformer}.
 *
 * <p>This handler provides common audit log functionality such as setting actor data, batch
 * operation information, and handling both successful operations and rejections.
 *
 * <p>To add audit logging for a new record type:
 *
 * <ol>
 *   <li>Create a transformer implementing {@link AuditLogOperationTransformer}
 *   <li>Instantiate an {@link AuditLogHandler} with the transformer
 *   <li>Register the handler in the exporter resource provider
 * </ol>
 *
 * @param <R> the record value type this handler processes
 */
public class AuditLogHandler<R extends RecordValue> implements ExportHandler<AuditLogEntity, R> {

  protected static final int ID_LENGTH = 32;

  private final String indexName;
  private final AuditLogOperationTransformer<? extends Intent, R, AuditLogEntity>
      operationTransformer;

  public AuditLogHandler(
      final String indexName,
      final AuditLogOperationTransformer<? extends Intent, R, AuditLogEntity>
          operationTransformer) {
    this.indexName = indexName;
    this.operationTransformer = operationTransformer;
  }

  @Override
  public ValueType getHandledValueType() {
    return operationTransformer.getValueType();
  }

  @Override
  public Class<AuditLogEntity> getEntityType() {
    return AuditLogEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<R> record) {
    final var recordIntent = record.getIntent();
    return AuditLogCommonHandler.hasActorData(record)
        && (operationTransformer.getSupportedIntents().contains(recordIntent)
            || (operationTransformer.getSupportedCommandRejections().contains(recordIntent)
                && RecordType.COMMAND_REJECTION.equals(record.getRecordType())
                && operationTransformer
                    .getSupportedRejectionTypes()
                    .contains(record.getRejectionType())));
  }

  @Override
  public List<String> generateIds(final Record<R> record) {
    return List.of(RandomStringUtils.insecure().nextAlphanumeric(ID_LENGTH));
  }

  @Override
  public AuditLogEntity createNewEntity(final String id) {
    return new AuditLogEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<R> record, final AuditLogEntity entity) {

    final var recordValueType = record.getValueType();
    final var recordIntent = record.getIntent();

    entity
        .setEntityKey(String.valueOf(record.getKey()))
        .setEntityType(AuditLogCommonHandler.getEntityType(recordValueType))
        .setOperationType(AuditLogCommonHandler.getOperationType(record.getIntent()))
        .setEntityVersion(record.getRecordVersion())
        .setEntityValueType(recordValueType.value())
        .setEntityOperationIntent(recordIntent.value())
        .setTimestamp(DateUtil.toOffsetDateTime(record.getTimestamp()))
        .setCategory(AuditLogCommonHandler.getOperationCategory(recordValueType));
    setActorData(entity, record);
    setBatchOperationData(entity, record);

    if (RecordType.COMMAND_REJECTION.equals(record.getRecordType())) {
      setRejectionData(entity, record);
    } else {
      entity.setResult(AuditLogOperationResult.SUCCESS);
      operationTransformer.transform(entity, record);
    }
  }

  @Override
  public void flush(final AuditLogEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private void setBatchOperationData(final AuditLogEntity entity, final Record<R> record) {
    final var batchOperationKey = AuditLogCommonHandler.extractBatchOperationKey(record);
    batchOperationKey.ifPresent(entity::setBatchOperationKey);
  }

  private void setRejectionData(final AuditLogEntity entity, final Record<R> record) {
    entity.setResult(AuditLogOperationResult.FAILURE);
    // TODO: set rejection type and reason to AuditLogEntity#details
  }

  private void setActorData(final AuditLogEntity entity, final Record<R> record) {
    final var actorData = AuditLogCommonHandler.extractActorData(record);
    entity.setActorId(actorData.actorId()).setActorType(actorData.actorType());
  }
}
