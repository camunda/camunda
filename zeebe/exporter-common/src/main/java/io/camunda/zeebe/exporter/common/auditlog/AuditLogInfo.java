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
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public record AuditLogInfo(
    AuditLogOperationCategory category,
    AuditLogEntityType entityType,
    AuditLogOperationType operationType,
    AuditLogActor actor,
    Optional<AuditLogTenant> tenant) {

  // Static map for ValueType to AuditLogOperationCategory mappings
  private static final Map<ValueType, AuditLogOperationCategory> OPERATION_CATEGORY_MAP =
      new EnumMap<>(
          Map.ofEntries(
              Map.entry(
                  ValueType.BATCH_OPERATION_CREATION, AuditLogOperationCategory.DEPLOYED_RESOURCES),
              Map.entry(
                  ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
                  AuditLogOperationCategory.DEPLOYED_RESOURCES),
              Map.entry(
                  ValueType.DECISION_EVALUATION, AuditLogOperationCategory.DEPLOYED_RESOURCES),
              Map.entry(
                  ValueType.DECISION_REQUIREMENTS, AuditLogOperationCategory.DEPLOYED_RESOURCES),
              Map.entry(ValueType.DECISION, AuditLogOperationCategory.DEPLOYED_RESOURCES),
              Map.entry(ValueType.FORM, AuditLogOperationCategory.DEPLOYED_RESOURCES),
              Map.entry(ValueType.INCIDENT, AuditLogOperationCategory.DEPLOYED_RESOURCES),
              Map.entry(
                  ValueType.PROCESS_INSTANCE_CREATION,
                  AuditLogOperationCategory.DEPLOYED_RESOURCES),
              Map.entry(
                  ValueType.PROCESS_INSTANCE_MIGRATION,
                  AuditLogOperationCategory.DEPLOYED_RESOURCES),
              Map.entry(
                  ValueType.PROCESS_INSTANCE_MODIFICATION,
                  AuditLogOperationCategory.DEPLOYED_RESOURCES),
              Map.entry(ValueType.PROCESS_INSTANCE, AuditLogOperationCategory.DEPLOYED_RESOURCES),
              Map.entry(ValueType.RESOURCE, AuditLogOperationCategory.DEPLOYED_RESOURCES),
              Map.entry(ValueType.VARIABLE, AuditLogOperationCategory.DEPLOYED_RESOURCES),
              Map.entry(ValueType.AUTHORIZATION, AuditLogOperationCategory.ADMIN),
              Map.entry(ValueType.GROUP, AuditLogOperationCategory.ADMIN),
              Map.entry(ValueType.MAPPING_RULE, AuditLogOperationCategory.ADMIN),
              Map.entry(ValueType.ROLE, AuditLogOperationCategory.ADMIN),
              Map.entry(ValueType.TENANT, AuditLogOperationCategory.ADMIN),
              Map.entry(ValueType.USER, AuditLogOperationCategory.ADMIN),
              Map.entry(ValueType.USER_TASK, AuditLogOperationCategory.USER_TASKS)));

  // Static map for ValueType to AuditLogEntityType mappings
  private static final Map<ValueType, AuditLogEntityType> ENTITY_TYPE_MAP =
      new EnumMap<>(
          Map.ofEntries(
              Map.entry(ValueType.AUTHORIZATION, AuditLogEntityType.AUTHORIZATION),
              Map.entry(ValueType.PROCESS_INSTANCE, AuditLogEntityType.PROCESS_INSTANCE),
              Map.entry(ValueType.PROCESS_INSTANCE_CREATION, AuditLogEntityType.PROCESS_INSTANCE),
              Map.entry(
                  ValueType.PROCESS_INSTANCE_MODIFICATION, AuditLogEntityType.PROCESS_INSTANCE),
              Map.entry(ValueType.PROCESS_INSTANCE_MIGRATION, AuditLogEntityType.PROCESS_INSTANCE),
              Map.entry(ValueType.INCIDENT, AuditLogEntityType.INCIDENT),
              Map.entry(ValueType.VARIABLE, AuditLogEntityType.VARIABLE),
              Map.entry(ValueType.DECISION, AuditLogEntityType.DECISION),
              Map.entry(ValueType.DECISION_EVALUATION, AuditLogEntityType.DECISION),
              Map.entry(ValueType.DECISION_REQUIREMENTS, AuditLogEntityType.RESOURCE),
              Map.entry(ValueType.BATCH_OPERATION_CREATION, AuditLogEntityType.BATCH),
              Map.entry(ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT, AuditLogEntityType.BATCH),
              Map.entry(ValueType.USER, AuditLogEntityType.USER),
              Map.entry(ValueType.MAPPING_RULE, AuditLogEntityType.MAPPING_RULE),
              Map.entry(ValueType.GROUP, AuditLogEntityType.GROUP),
              Map.entry(ValueType.ROLE, AuditLogEntityType.ROLE),
              Map.entry(ValueType.TENANT, AuditLogEntityType.TENANT),
              Map.entry(ValueType.FORM, AuditLogEntityType.RESOURCE),
              Map.entry(ValueType.PROCESS, AuditLogEntityType.RESOURCE),
              Map.entry(ValueType.RESOURCE, AuditLogEntityType.RESOURCE),
              Map.entry(ValueType.USER_TASK, AuditLogEntityType.USER_TASK)));

  // Static map for Intent to AuditLogOperationType mappings
  private static final Map<Intent, AuditLogOperationType> OPERATION_TYPE_MAP =
      new EnumMap<>(
          Map.ofEntries(
              // Authorization
              Map.entry(AuthorizationIntent.CREATED, AuditLogOperationType.CREATE),
              Map.entry(AuthorizationIntent.UPDATED, AuditLogOperationType.UPDATE),
              Map.entry(AuthorizationIntent.DELETED, AuditLogOperationType.DELETE),

              // BatchOperation
              Map.entry(BatchOperationIntent.CREATED, AuditLogOperationType.CREATE),
              Map.entry(BatchOperationIntent.RESUMED, AuditLogOperationType.RESUME),
              Map.entry(BatchOperationIntent.RESUME, AuditLogOperationType.RESUME),
              Map.entry(BatchOperationIntent.SUSPENDED, AuditLogOperationType.SUSPEND),
              Map.entry(BatchOperationIntent.SUSPEND, AuditLogOperationType.SUSPEND),
              Map.entry(BatchOperationIntent.CANCELED, AuditLogOperationType.CANCEL),
              Map.entry(BatchOperationIntent.CANCEL, AuditLogOperationType.CANCEL),

              // DecisionEvaluation
              Map.entry(DecisionEvaluationIntent.EVALUATED, AuditLogOperationType.EVALUATE),
              Map.entry(DecisionEvaluationIntent.FAILED, AuditLogOperationType.EVALUATE),

              // Decision
              Map.entry(DecisionIntent.CREATED, AuditLogOperationType.CREATE),
              Map.entry(DecisionIntent.DELETED, AuditLogOperationType.DELETE),

              // DecisionRequirements
              Map.entry(DecisionRequirementsIntent.CREATED, AuditLogOperationType.CREATE),
              Map.entry(DecisionRequirementsIntent.DELETED, AuditLogOperationType.DELETE),

              // Form
              Map.entry(FormIntent.CREATED, AuditLogOperationType.CREATE),
              Map.entry(FormIntent.DELETED, AuditLogOperationType.DELETE),

              // Group
              Map.entry(GroupIntent.CREATED, AuditLogOperationType.CREATE),
              Map.entry(GroupIntent.UPDATED, AuditLogOperationType.UPDATE),
              Map.entry(GroupIntent.DELETED, AuditLogOperationType.DELETE),
              Map.entry(GroupIntent.ENTITY_ADDED, AuditLogOperationType.ASSIGN),
              Map.entry(GroupIntent.ENTITY_REMOVED, AuditLogOperationType.UNASSIGN),

              // Incident
              Map.entry(IncidentIntent.RESOLVED, AuditLogOperationType.RESOLVE),
              Map.entry(IncidentIntent.RESOLVE, AuditLogOperationType.RESOLVE),

              // MappingRule
              Map.entry(MappingRuleIntent.CREATED, AuditLogOperationType.CREATE),
              Map.entry(MappingRuleIntent.UPDATED, AuditLogOperationType.UPDATE),
              Map.entry(MappingRuleIntent.DELETED, AuditLogOperationType.DELETE),

              // Process
              Map.entry(ProcessIntent.CREATED, AuditLogOperationType.CREATE),
              Map.entry(ProcessIntent.DELETED, AuditLogOperationType.DELETE),

              // ProcessInstanceCreation
              Map.entry(ProcessInstanceCreationIntent.CREATED, AuditLogOperationType.CREATE),

              // ProcessInstance
              Map.entry(ProcessInstanceIntent.CANCELING, AuditLogOperationType.CANCEL),
              Map.entry(ProcessInstanceIntent.CANCEL, AuditLogOperationType.CANCEL),

              // ProcessInstanceMigration
              Map.entry(ProcessInstanceMigrationIntent.MIGRATED, AuditLogOperationType.MIGRATE),
              Map.entry(ProcessInstanceMigrationIntent.MIGRATE, AuditLogOperationType.MIGRATE),

              // ProcessInstanceModification
              Map.entry(ProcessInstanceModificationIntent.MODIFIED, AuditLogOperationType.MODIFY),
              Map.entry(ProcessInstanceModificationIntent.MODIFY, AuditLogOperationType.MODIFY),

              // Resource
              Map.entry(ResourceIntent.CREATED, AuditLogOperationType.CREATE),
              Map.entry(ResourceIntent.DELETED, AuditLogOperationType.DELETE),

              // Tenant
              Map.entry(TenantIntent.CREATED, AuditLogOperationType.CREATE),
              Map.entry(TenantIntent.UPDATED, AuditLogOperationType.UPDATE),
              Map.entry(TenantIntent.DELETED, AuditLogOperationType.DELETE),
              Map.entry(TenantIntent.ENTITY_ADDED, AuditLogOperationType.ASSIGN),
              Map.entry(TenantIntent.ENTITY_REMOVED, AuditLogOperationType.UNASSIGN),

              // Role
              Map.entry(RoleIntent.CREATED, AuditLogOperationType.CREATE),
              Map.entry(RoleIntent.UPDATED, AuditLogOperationType.UPDATE),
              Map.entry(RoleIntent.DELETED, AuditLogOperationType.DELETE),
              Map.entry(RoleIntent.ENTITY_ADDED, AuditLogOperationType.ASSIGN),
              Map.entry(RoleIntent.ENTITY_REMOVED, AuditLogOperationType.UNASSIGN),

              // User
              Map.entry(UserIntent.CREATED, AuditLogOperationType.CREATE),
              Map.entry(UserIntent.UPDATED, AuditLogOperationType.UPDATE),
              Map.entry(UserIntent.DELETED, AuditLogOperationType.DELETE),

              // UserTask (non-ASSIGNED cases)
              Map.entry(UserTaskIntent.ASSIGN, AuditLogOperationType.ASSIGN),
              Map.entry(UserTaskIntent.UPDATED, AuditLogOperationType.UPDATE),
              Map.entry(UserTaskIntent.UPDATE, AuditLogOperationType.UPDATE),
              Map.entry(UserTaskIntent.COMPLETED, AuditLogOperationType.COMPLETE),
              Map.entry(UserTaskIntent.COMPLETE, AuditLogOperationType.COMPLETE),

              // Variable
              Map.entry(VariableIntent.CREATED, AuditLogOperationType.CREATE),
              Map.entry(VariableIntent.UPDATED, AuditLogOperationType.UPDATE)));

  public static AuditLogInfo of(final Record<?> record) {
    return new AuditLogInfo(
        getOperationCategory(record.getValueType()),
        getEntityType(record.getValueType()),
        getOperationType(record),
        AuditLogActor.of(record),
        AuditLogTenant.of(record));
  }

  private static AuditLogOperationCategory getOperationCategory(final ValueType valueType) {
    return OPERATION_CATEGORY_MAP.getOrDefault(valueType, AuditLogOperationCategory.UNKNOWN);
  }

  private static AuditLogEntityType getEntityType(final ValueType valueType) {
    return ENTITY_TYPE_MAP.getOrDefault(valueType, AuditLogEntityType.UNKNOWN);
  }

  private static AuditLogOperationType getOperationType(final Record<?> record) {
    final Intent intent = record.getIntent();
    if (intent == null) {
      return AuditLogOperationType.UNKNOWN;
    }

    // Special handling for UserTaskIntent.ASSIGNED
    if (intent == UserTaskIntent.ASSIGNED) {
      // Operation type is UNASSIGN if assignee is not provided
      if (!(record.getValue() instanceof UserTaskRecordValue)) {
        return AuditLogOperationType.UNKNOWN;
      }
      final String assignee = ((UserTaskRecordValue) record.getValue()).getAssignee();
      return (assignee == null || assignee.isBlank())
          ? AuditLogOperationType.UNASSIGN
          : AuditLogOperationType.ASSIGN;
    }

    return OPERATION_TYPE_MAP.getOrDefault(intent, AuditLogOperationType.UNKNOWN);
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
