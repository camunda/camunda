/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.service.AuditLogWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.AuditLogActorType;
import io.camunda.search.entities.AuditLogEntityType;
import io.camunda.search.entities.AuditLogOperationCategory;
import io.camunda.search.entities.AuditLogOperationResult;
import io.camunda.search.entities.AuditLogOperationType;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.util.DateUtil;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;

public class AuditLogExportHandler<R extends RecordValue> implements RdbmsExportHandler<R> {

  private final AuditLogWriter auditLogWriter;

  public AuditLogExportHandler(final AuditLogWriter auditLogWriter) {
    this.auditLogWriter = auditLogWriter;
  }

  @Override
  public boolean canExport(final Record<R> record) {
    return record.getIntent() != null; // TODO: make it more specific
  }

  @Override
  public void export(final Record<R> record) {
    System.out.println("Exporting audit log for record starting: " + record.getRecordType().name());
    auditLogWriter.create(map(record));
    System.out.println("Exported audit log for record completed: " + record.getRecordType().name());
  }

  private AuditLogDbModel map(final Record<R> record) {
    final var auditLog =
        new AuditLogDbModel.Builder()
            .auditLogKey(
                RandomStringUtils.insecure()
                    .nextAlphabetic(
                        8)) // FIXME: generate correct ID.., and do we need to have it as a primary
            // key?
            .entityKey(String.valueOf(record.getKey()))
            .entityType(getEntityType(record.getValueType()))
            .operationType(getOperationType(record.getIntent()))
            .entityVersion(record.getRecordVersion())
            .entityValueType(record.getValueType().value())
            .entityOperationIntent(record.getIntent().value())
            .timestamp(DateUtil.toOffsetDateTime(record.getTimestamp()))
            .category(getOperationCategory(record.getValueType()));
    setActorData(auditLog, record);
    setBatchOperationData(auditLog, record);

    if (RecordType.COMMAND_REJECTION.equals(record.getRecordType())) {
      setRejectionData(auditLog, record);
    } else {
      auditLog.result(AuditLogOperationResult.SUCCESS);
      // operationTransformer.transform(auditLog, record); // FIXME: add transformer
    }
    return auditLog.build();
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

  private void setBatchOperationData(
      final AuditLogDbModel.Builder builder, final Record<R> record) {
    final var batchOperationKey = record.getBatchOperationReference();
    if (RecordMetadataDecoder.batchOperationReferenceNullValue() != batchOperationKey) {
      builder.batchOperationKey(batchOperationKey);
    }
  }

  private void setRejectionData(final AuditLogDbModel.Builder builder, final Record<R> record) {
    builder.result(AuditLogOperationResult.FAILURE);
    // TODO: set rejection type and reason to AuditLogEntity#details
  }

  private boolean hasActorData(final Record<R> record) {
    final var authorizations = record.getAuthorizations();
    return getClientId(authorizations).isPresent() || getUsername(authorizations).isPresent();
  }

  private void setActorData(final AuditLogDbModel.Builder builder, final Record<R> record) {
    // FIXME: actors are not mapped correctly when using desktop modeler... or should it be there?
    final var authorizations = record.getAuthorizations();
    final var clientId = getClientId(authorizations);
    final var username = getUsername(authorizations);

    final String actorId;
    final AuditLogActorType actorType;
    if (clientId.isPresent()) {
      actorId = clientId.get();
      actorType = AuditLogActorType.CLIENT;
    } else if (username.isPresent()) {
      actorId = username.get();
      actorType = AuditLogActorType.USER;
    } else {
      actorId = "unknown";
      actorType = AuditLogActorType.UNKNOWN;
    }

    builder.actorId(actorId).actorType(actorType);
  }

  private Optional<String> getUsername(final Map<String, Object> authorizationClaims) {
    return Optional.ofNullable((String) authorizationClaims.get(Authorization.AUTHORIZED_USERNAME));
  }

  private Optional<String> getClientId(final Map<String, Object> authorizationClaims) {
    return Optional.ofNullable(
        (String) authorizationClaims.get(Authorization.AUTHORIZED_CLIENT_ID));
  }
}
