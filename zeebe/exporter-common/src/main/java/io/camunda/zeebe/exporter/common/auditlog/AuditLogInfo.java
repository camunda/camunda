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
import io.camunda.search.entities.AuditLogEntity.AuditLogTenantScope;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.util.Map;
import java.util.Optional;

public record AuditLogInfo(
    AuditLogOperationCategory category,
    AuditLogEntityType entityType,
    AuditLogOperationType operationType,
    AuditLogActor actor,
    Optional<AuditLogTenant> tenant) {

  public static AuditLogInfo of(final Record<?> record) {
    return new AuditLogInfo(
        getOperationCategory(record.getValueType()),
        getEntityType(record.getValueType()),
        getOperationType(record),
        AuditLogActor.of(record),
        AuditLogTenant.of(record));
  }

  private static AuditLogOperationCategory getOperationCategory(final ValueType valueType) {
    switch (valueType) {
      case BATCH_OPERATION_CREATION:
      case BATCH_OPERATION_LIFECYCLE_MANAGEMENT:
      case DECISION_EVALUATION:
      case DECISION_REQUIREMENTS:
      case DECISION:
      case FORM:
      case INCIDENT:
      case PROCESS_INSTANCE_CREATION:
      case PROCESS_INSTANCE_MIGRATION:
      case PROCESS_INSTANCE_MODIFICATION:
      case PROCESS_INSTANCE:
      case RESOURCE:
      case VARIABLE:
        return AuditLogOperationCategory.DEPLOYED_RESOURCES;
      case AUTHORIZATION:
      case GROUP:
      case MAPPING_RULE:
      case ROLE:
      case TENANT:
      case USER:
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
      case DECISION:
      case DECISION_EVALUATION:
        return AuditLogEntityType.DECISION;
      case DECISION_REQUIREMENTS:
        return AuditLogEntityType.RESOURCE;
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
      case FORM:
      case PROCESS:
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

      case DecisionIntent.CREATED:
        return AuditLogOperationType.CREATE;
      case DecisionIntent.DELETED:
        return AuditLogOperationType.DELETE;

      case DecisionRequirementsIntent.CREATED:
        return AuditLogOperationType.CREATE;
      case DecisionRequirementsIntent.DELETED:
        return AuditLogOperationType.DELETE;

      case FormIntent.CREATED:
        return AuditLogOperationType.CREATE;
      case FormIntent.DELETED:
        return AuditLogOperationType.DELETE;

      case GroupIntent.CREATED:
        return AuditLogOperationType.CREATE;
      case GroupIntent.UPDATED:
        return AuditLogOperationType.UPDATE;
      case GroupIntent.DELETED:
        return AuditLogOperationType.DELETE;
      case GroupIntent.ENTITY_ADDED:
        return AuditLogOperationType.ASSIGN;
      case GroupIntent.ENTITY_REMOVED:
        return AuditLogOperationType.UNASSIGN;

      case IncidentIntent.RESOLVED:
      case IncidentIntent.RESOLVE:
        return AuditLogOperationType.RESOLVE;

      case MappingRuleIntent.CREATED:
        return AuditLogOperationType.CREATE;
      case MappingRuleIntent.UPDATED:
        return AuditLogOperationType.UPDATE;
      case MappingRuleIntent.DELETED:
        return AuditLogOperationType.DELETE;

      case ProcessIntent.CREATED:
        return AuditLogOperationType.CREATE;
      case ProcessIntent.DELETED:
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

      case RoleIntent.CREATED:
        return AuditLogOperationType.CREATE;
      case RoleIntent.UPDATED:
        return AuditLogOperationType.UPDATE;
      case RoleIntent.DELETED:
        return AuditLogOperationType.DELETE;
      case RoleIntent.ENTITY_ADDED:
        return AuditLogOperationType.ASSIGN;
      case RoleIntent.ENTITY_REMOVED:
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

    public static final AuditLogActor ANONYMOUS =
        new AuditLogActor(AuditLogActorType.ANONYMOUS, null);
    public static final AuditLogActor UNKNOWN = new AuditLogActor(AuditLogActorType.UNKNOWN, null);

    public static AuditLogActor unknown() {
      return UNKNOWN;
    }

    public static AuditLogActor anonymous() {
      return ANONYMOUS;
    }

    public static AuditLogActor of(final Record<?> record) {
      final Map<String, Object> authorizations = record.getAuthorizations();

      // client
      final String clientId =
          Optional.ofNullable(authorizations.get(Authorization.AUTHORIZED_CLIENT_ID))
              .map(Object::toString)
              .orElse(null);
      if (clientId != null) {
        return new AuditLogActor(AuditLogActorType.CLIENT, clientId);
      }

      // user
      final String username =
          Optional.ofNullable(authorizations.get(Authorization.AUTHORIZED_USERNAME))
              .map(Object::toString)
              .orElse(null);
      if (username != null) {
        return new AuditLogActor(AuditLogActorType.USER, username);
      }

      // anonymous / internal
      final boolean isAnonymous =
          Optional.ofNullable(authorizations.get(Authorization.AUTHORIZED_ANONYMOUS_USER))
              .map(Boolean.class::cast)
              .orElse(false);
      if (isAnonymous) {
        return AuditLogActor.anonymous();
      }

      return AuditLogActor.unknown();
    }
  }

  public record AuditLogTenant(String tenantId) {
    public AuditLogTenantScope scope() {
      return AuditLogTenantScope.TENANT;
    }

    public static Optional<AuditLogTenant> of(final Record<?> record) {
      final var value = record.getValue();

      if (value instanceof TenantOwned) {
        final var tenantId = ((TenantOwned) value).getTenantId();
        return Optional.of(new AuditLogTenant(tenantId));
      }

      return Optional.empty();
    }
  }
}
