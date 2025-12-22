/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog;

import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
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
        getOperationType(record),
        AuditLogActor.of(record),
        getBatchOperation(record));
  }

  private static Optional<BatchOperation> getBatchOperation(final Record<?> record) {
    final var batchOperationKey = record.getBatchOperationReference();
    if (RecordMetadataDecoder.batchOperationReferenceNullValue() != batchOperationKey) {
      return Optional.of(new BatchOperation(batchOperationKey));
    }
    return Optional.empty();
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
      case RESOURCE:
        return AuditLogOperationCategory.DEPLOYED_RESOURCES;
      case AUTHORIZATION:
      case USER:
      case MAPPING_RULE:
      case GROUP:
      case ROLE:
      case TENANT:
        return AuditLogOperationCategory.ADMIN;
      case USER_TASK:
        return AuditLogOperationCategory.USER_TASKS;
      default:
        return AuditLogOperationCategory.UNKNOWN;
    }
  }

  private static AuditLogEntityType getEntityType(final ValueType valueType) {
    switch (valueType) {
      case AUTHORIZATION:
        return AuditLogEntityType.AUTHORIZATION;
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
      case GROUP:
        return AuditLogEntityType.GROUP;
      case ROLE:
        return AuditLogEntityType.ROLE;
      case TENANT:
        return AuditLogEntityType.TENANT;
      case RESOURCE:
        return AuditLogEntityType.RESOURCE;
      case USER_TASK:
        return AuditLogEntityType.USER_TASK;
      default:
        return AuditLogEntityType.UNKNOWN;
    }
  }

  private static AuditLogOperationType getOperationType(final Record<?> record) {
    final Intent intent = record.getIntent();
    if (intent == null) {
      return AuditLogOperationType.UNKNOWN;
    }

    switch (intent) {
      case AuthorizationIntent.CREATED:
        return AuditLogOperationType.CREATE;
      case AuthorizationIntent.UPDATED:
        return AuditLogOperationType.UPDATE;
      case AuthorizationIntent.DELETED:
        return AuditLogOperationType.DELETE;

      case BatchOperationIntent.CREATED:
        return AuditLogOperationType.CREATE;
      case BatchOperationIntent.RESUMED:
      case BatchOperationIntent.RESUME:
        return AuditLogOperationType.RESUME;
      case BatchOperationIntent.SUSPENDED:
      case BatchOperationIntent.SUSPEND:
        return AuditLogOperationType.SUSPEND;
      case BatchOperationIntent.CANCELED:
      case BatchOperationIntent.CANCEL:
        return AuditLogOperationType.CANCEL;

      case DecisionEvaluationIntent.EVALUATED:
      case DecisionEvaluationIntent.FAILED:
        return AuditLogOperationType.EVALUATE;

      case IncidentIntent.RESOLVED:
      case IncidentIntent.RESOLVE:
        return AuditLogOperationType.RESOLVE;

      case MappingRuleIntent.CREATED:
        return AuditLogOperationType.CREATE;
      case MappingRuleIntent.UPDATED:
        return AuditLogOperationType.UPDATE;
      case MappingRuleIntent.DELETED:
        return AuditLogOperationType.DELETE;

      case ProcessInstanceCreationIntent.CREATED:
        return AuditLogOperationType.CREATE;

      case ProcessInstanceIntent.CANCELING:
      case ProcessInstanceIntent.CANCEL:
        return AuditLogOperationType.CANCEL;

      case ProcessInstanceMigrationIntent.MIGRATED:
      case ProcessInstanceMigrationIntent.MIGRATE:
        return AuditLogOperationType.MIGRATE;

      case ProcessInstanceModificationIntent.MODIFIED:
      case ProcessInstanceModificationIntent.MODIFY:
        return AuditLogOperationType.MODIFY;

      case ResourceIntent.CREATED:
        return AuditLogOperationType.CREATE;
      case ResourceIntent.DELETED:
        return AuditLogOperationType.DELETE;

      case TenantIntent.CREATED:
        return AuditLogOperationType.CREATE;
      case TenantIntent.UPDATED:
        return AuditLogOperationType.UPDATE;
      case TenantIntent.DELETED:
        return AuditLogOperationType.DELETE;
      case TenantIntent.ENTITY_ADDED:
        return AuditLogOperationType.ASSIGN;
      case TenantIntent.ENTITY_REMOVED:
        return AuditLogOperationType.UNASSIGN;

      case UserIntent.CREATED:
        return AuditLogOperationType.CREATE;
      case UserIntent.UPDATED:
        return AuditLogOperationType.UPDATE;
      case UserIntent.DELETED:
        return AuditLogOperationType.DELETE;

      case UserTaskIntent.ASSIGNED:
        // Operation type is UNASSIGN if assignee is not provided
        if (!(record.getValue() instanceof UserTaskRecordValue)) {
          return AuditLogOperationType.UNKNOWN;
        }
        final String assignee = ((UserTaskRecordValue) record.getValue()).getAssignee();
        return (assignee == null || assignee.isBlank())
            ? AuditLogOperationType.UNASSIGN
            : AuditLogOperationType.ASSIGN;
      case UserTaskIntent.ASSIGN:
        return AuditLogOperationType.ASSIGN;
      case UserTaskIntent.UPDATED:
      case UserTaskIntent.UPDATE:
        return AuditLogOperationType.UPDATE;
      case UserTaskIntent.COMPLETED:
      case UserTaskIntent.COMPLETE:
        return AuditLogOperationType.COMPLETE;

      case VariableIntent.CREATED:
        return AuditLogOperationType.CREATE;
      case VariableIntent.UPDATED:
        return AuditLogOperationType.UPDATE;

      // TODO: map additional intents to operations here
      default:
        return AuditLogOperationType.UNKNOWN;
    }
  }

  public record AuditLogActor(AuditLogActorType actorType, String actorId) {

    public static AuditLogActor of(final Record<?> record) {
      final Map<String, Object> authorizations = record.getAuthorizations();
      final var clientId = (String) authorizations.get(Authorization.AUTHORIZED_CLIENT_ID);
      if (clientId != null) {
        return new AuditLogActor(AuditLogActorType.CLIENT, clientId);
      }
      final var username = (String) authorizations.get(Authorization.AUTHORIZED_USERNAME);
      if (username != null) {
        return new AuditLogActor(AuditLogActorType.USER, username);
      }
      return null;
    }
  }

  public record BatchOperation(long key) {}
}
