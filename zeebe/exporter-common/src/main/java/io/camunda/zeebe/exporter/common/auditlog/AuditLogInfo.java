/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog;

import io.camunda.webapps.schema.entities.auditlog.AuditLogActorType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationCategory;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationType;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import java.util.Map;
import java.util.Optional;

public record AuditLogInfo(
    AuditLogOperationCategory category,
    AuditLogEntityType entityType,
    AuditLogOperationType operationType,
    AuditLogActor actor,
    Optional<BatchOperation> batchOperation) {

  public static AuditLogInfo of(final Record<?> record) {
    return new AuditLogInfo(
        getOperationCategory(record.getValueType()),
        getEntityType(record.getValueType()),
        getOperationType(record.getIntent()),
        getActor(record.getAuthorizations()),
        getBatchOperation(record));
  }

  private static Optional<BatchOperation> getBatchOperation(final Record<?> record) {
    final var batchOperationKey = record.getBatchOperationReference();
    if (RecordMetadataDecoder.batchOperationReferenceNullValue() != batchOperationKey) {
      return Optional.of(new BatchOperation(batchOperationKey));
    }
    return Optional.empty();
  }

  private static AuditLogActor getActor(final Map<String, Object> authorizations) {
    if (authorizations.get(Authorization.AUTHORIZED_CLIENT_ID) != null) {
      return new AuditLogActor(
          AuditLogActorType.CLIENT,
          (String) authorizations.get(Authorization.AUTHORIZED_CLIENT_ID));
    } else if (authorizations.get(Authorization.AUTHORIZED_USERNAME) != null) {
      return new AuditLogActor(
          AuditLogActorType.USER, (String) authorizations.get(Authorization.AUTHORIZED_USERNAME));
    } else {
      return null;
    }
  }

  private static AuditLogOperationCategory getOperationCategory(final ValueType valueType) {
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

  private static AuditLogEntityType getEntityType(final ValueType valueType) {
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

  private static AuditLogOperationType getOperationType(final Intent intent) {
    if (intent == null) {
      return AuditLogOperationType.UNKNOWN;
    }

    switch (intent) {
      case ProcessInstanceModificationIntent.MODIFIED:
        return AuditLogOperationType.MODIFY;
      // TODO: map additional intents to operations here
      default:
        return AuditLogOperationType.UNKNOWN;
    }
  }

  public record AuditLogActor(AuditLogActorType actorType, String actorId) {}

  public record BatchOperation(long key) {}
}
