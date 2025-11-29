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
import io.camunda.webapps.schema.entities.auditlog.AuditLogActorType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationCategory;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationResult;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationType;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.util.DateUtil;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

  private final String indexName;
  private final AuditLogOperationTransformer<? extends Intent, R> operationTransformer;

  public AuditLogHandler(
      final String indexName,
      final AuditLogOperationTransformer<? extends Intent, R> operationTransformer) {
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
    return hasActorData(record)
        && (operationTransformer.getSupportedIntents().contains(recordIntent)
            || (operationTransformer.getSupportedCommandRejections().contains(recordIntent)
                && RecordType.COMMAND_REJECTION.equals(record.getRecordType())
                && operationTransformer
                    .getSupportedRejectionTypes()
                    .contains(record.getRejectionType())));
  }

  @Override
  public List<String> generateIds(final Record<R> record) {
    return List.of(String.valueOf(record.getPosition()));
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
        .setEntityType(getEntityType(recordValueType))
        .setOperationType(getOperationType(record.getIntent()))
        .setEntityVersion(record.getRecordVersion())
        .setEntityValueType(recordValueType.value())
        .setEntityOperationIntent(recordIntent.value())
        .setTimestamp(DateUtil.toOffsetDateTime(record.getTimestamp()))
        .setCategory(getOperationCategory(recordValueType));
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

  private AuditLogOperationType getOperationType(final Intent intent) {
    switch (intent) {
      case ProcessInstanceModificationIntent.MODIFIED:
        return AuditLogOperationType.MODIFY;
      // TODO: map additional intents to operations here
      default:
        return AuditLogOperationType.UNKNOWN;
    }
  }

  private AuditLogOperationCategory getOperationCategory(final ValueType valueType) {
    switch (valueType) {
      case PROCESS_INSTANCE:
      case PROCESS_INSTANCE_CREATION:
      case PROCESS_INSTANCE_MODIFICATION:
      case PROCESS_INSTANCE_MIGRATION:
      case INCIDENT:
      case VARIABLE:
      case DECISION_EVALUATION:
      case BATCH_OPERATION_CREATION:
      case BATCH_OPERATION_LIFECYCLE_MANAGEMENT:
        return AuditLogOperationCategory.OPERATOR;
      case USER:
      case MAPPING_RULE:
      case AUTHORIZATION:
      case GROUP:
      case ROLE:
      case TENANT:
        return AuditLogOperationCategory.ADMIN;
      case USER_TASK:
        return AuditLogOperationCategory.USER_TASK;
      default:
        return AuditLogOperationCategory.UNKNOWN;
    }
  }

  private AuditLogEntityType getEntityType(final ValueType valueType) {
    switch (valueType) {
      case PROCESS_INSTANCE:
      case PROCESS_INSTANCE_CREATION:
      case PROCESS_INSTANCE_MODIFICATION:
      case PROCESS_INSTANCE_MIGRATION:
        return AuditLogEntityType.PROCESS_INSTANCE;
      case INCIDENT:
        return AuditLogEntityType.INCIDENT;
      case VARIABLE:
        return AuditLogEntityType.VARIABLE;
      case DECISION_EVALUATION:
        return AuditLogEntityType.DECISION;
      case BATCH_OPERATION_CREATION:
      case BATCH_OPERATION_LIFECYCLE_MANAGEMENT:
        return AuditLogEntityType.BATCH;
      case USER:
        return AuditLogEntityType.USER;
      case MAPPING_RULE:
        return AuditLogEntityType.MAPPING_RULE;
      case AUTHORIZATION:
        return AuditLogEntityType.AUTHORIZATION;
      case GROUP:
        return AuditLogEntityType.GROUP;
      case ROLE:
        return AuditLogEntityType.ROLE;
      case TENANT:
        return AuditLogEntityType.TENANT;
      case USER_TASK:
        return AuditLogEntityType.USER_TASK;
      default:
        return AuditLogEntityType.UNKNOWN;
    }
  }

  private void setBatchOperationData(final AuditLogEntity entity, final Record<R> record) {
    final var batchOperationKey = record.getBatchOperationReference();
    if (RecordMetadataDecoder.batchOperationReferenceNullValue() != batchOperationKey) {
      entity.setBatchOperationKey(batchOperationKey);
    }
  }

  private void setRejectionData(final AuditLogEntity entity, final Record<R> record) {
    entity.setResult(AuditLogOperationResult.FAILURE);
    // TODO: set rejection type and reason to AuditLogEntity#details
  }

  private boolean hasActorData(final Record<R> record) {
    final var authorizations = record.getAuthorizations();
    return getClientId(authorizations).isPresent() || getUsername(authorizations).isPresent();
  }

  private void setActorData(final AuditLogEntity entity, final Record<R> record) {
    final var authorizations = record.getAuthorizations();
    final var clientId = getClientId(authorizations);
    final var username = getUsername(authorizations);

    final String actorId;
    final AuditLogActorType actorType;
    if (clientId.isPresent()) {
      actorId = clientId.get();
      actorType = AuditLogActorType.CLIENT;
    } else {
      actorId = username.get();
      actorType = AuditLogActorType.USER;
    }

    entity.setActorId(actorId).setActorType(actorType);
  }

  private Optional<String> getUsername(final Map<String, Object> authorizationClaims) {
    return Optional.ofNullable((String) authorizationClaims.get(Authorization.AUTHORIZED_USERNAME));
  }

  private Optional<String> getClientId(final Map<String, Object> authorizationClaims) {
    return Optional.ofNullable(
        (String) authorizationClaims.get(Authorization.AUTHORIZED_CLIENT_ID));
  }
}
