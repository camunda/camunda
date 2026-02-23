/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import static io.camunda.zeebe.protocol.record.ValueType.AUTHORIZATION;
import static io.camunda.zeebe.protocol.record.ValueType.BATCH_OPERATION_CREATION;
import static io.camunda.zeebe.protocol.record.ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT;
import static io.camunda.zeebe.protocol.record.ValueType.DECISION;
import static io.camunda.zeebe.protocol.record.ValueType.DECISION_EVALUATION;
import static io.camunda.zeebe.protocol.record.ValueType.DECISION_REQUIREMENTS;
import static io.camunda.zeebe.protocol.record.ValueType.GROUP;
import static io.camunda.zeebe.protocol.record.ValueType.MAPPING_RULE;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_CREATION;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_MIGRATION;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_MODIFICATION;
import static io.camunda.zeebe.protocol.record.ValueType.ROLE;
import static io.camunda.zeebe.protocol.record.ValueType.TENANT;
import static io.camunda.zeebe.protocol.record.ValueType.USER;
import static io.camunda.zeebe.protocol.record.ValueType.USER_TASK;
import static io.camunda.zeebe.protocol.record.ValueType.VARIABLE;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent.MODIFIED;

import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer.TransformerConfig;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Map;

public class AuditLogTransformerConfigs {
  public static final TransformerConfig AUTHORIZATION_CONFIG =
      TransformerConfig.with(AUTHORIZATION)
          .withIntents(
              AuthorizationIntent.CREATED, AuthorizationIntent.UPDATED, AuthorizationIntent.DELETED)
          .withDataCleanupIntents(AuthorizationIntent.DELETED);

  public static final TransformerConfig BATCH_OPERATION_CREATION_CONFIG =
      TransformerConfig.with(BATCH_OPERATION_CREATION).withIntents(BatchOperationIntent.CREATED);

  public static final TransformerConfig BATCH_OPERATION_LIFECYCLE_MANAGEMENT_CONFIG =
      TransformerConfig.with(BATCH_OPERATION_LIFECYCLE_MANAGEMENT)
          .withIntents(
              BatchOperationIntent.RESUMED,
              BatchOperationIntent.SUSPENDED,
              BatchOperationIntent.CANCELED)
          .withRejections(
              BatchOperationIntent.RESUME,
              BatchOperationIntent.SUSPEND,
              BatchOperationIntent.CANCEL)
          .withRejectionTypes(RejectionType.INVALID_STATE);

  public static final TransformerConfig DECISION_EVALUATION_CONFIG =
      TransformerConfig.with(DECISION_EVALUATION)
          .withIntents(DecisionEvaluationIntent.EVALUATED, DecisionEvaluationIntent.FAILED)
          .withDataCleanupIntents(
              DecisionEvaluationIntent.EVALUATED, DecisionEvaluationIntent.FAILED);

  public static final TransformerConfig DECISION_CONFIG =
      TransformerConfig.with(DECISION).withIntents(DecisionIntent.CREATED, DecisionIntent.DELETED);

  public static final TransformerConfig DECISION_REQUIREMENTS_CONFIG =
      TransformerConfig.with(DECISION_REQUIREMENTS)
          .withIntents(DecisionRequirementsIntent.CREATED, DecisionRequirementsIntent.DELETED);

  public static final TransformerConfig FORM_CONFIG =
      TransformerConfig.with(ValueType.FORM)
          .withIntents(FormIntent.CREATED, FormIntent.DELETED)
          .withDataCleanupIntents(FormIntent.DELETED);

  public static final TransformerConfig GROUP_CONFIG =
      TransformerConfig.with(GROUP)
          .withIntents(GroupIntent.CREATED, GroupIntent.UPDATED, GroupIntent.DELETED)
          .withDataCleanupIntents(GroupIntent.DELETED);

  public static final TransformerConfig GROUP_ENTITY_CONFIG =
      TransformerConfig.with(GROUP)
          .withIntents(GroupIntent.ENTITY_ADDED, GroupIntent.ENTITY_REMOVED);

  public static final TransformerConfig INCIDENT_RESOLUTION_CONFIG =
      TransformerConfig.with(ValueType.INCIDENT)
          .withIntents(IncidentIntent.RESOLVED)
          .withRejections(IncidentIntent.RESOLVE, RejectionType.INVALID_STATE);

  public static final TransformerConfig MAPPING_RULE_CONFIG =
      TransformerConfig.with(MAPPING_RULE)
          .withIntents(
              MappingRuleIntent.CREATED, MappingRuleIntent.UPDATED, MappingRuleIntent.DELETED)
          .withDataCleanupIntents(MappingRuleIntent.DELETED);

  public static final TransformerConfig PROCESS_CONFIG =
      TransformerConfig.with(PROCESS).withIntents(ProcessIntent.CREATED, ProcessIntent.DELETED);

  public static final TransformerConfig PROCESS_INSTANCE_CANCEL_CONFIG =
      TransformerConfig.with(ValueType.PROCESS_INSTANCE)
          .withIntents(ProcessInstanceIntent.CANCELING)
          .withRejections(ProcessInstanceIntent.CANCEL, RejectionType.INVALID_STATE);

  public static final TransformerConfig PROCESS_INSTANCE_CREATION_CONFIG =
      TransformerConfig.with(PROCESS_INSTANCE_CREATION)
          .withIntents(ProcessInstanceCreationIntent.CREATED);

  public static final TransformerConfig PROCESS_INSTANCE_MIGRATION_CONFIG =
      TransformerConfig.with(PROCESS_INSTANCE_MIGRATION)
          .withIntents(ProcessInstanceMigrationIntent.MIGRATED)
          .withRejections(
              ProcessInstanceMigrationIntent.MIGRATE,
              RejectionType.INVALID_STATE,
              RejectionType.PROCESSING_ERROR);

  public static final TransformerConfig PROCESS_INSTANCE_MODIFICATION_CONFIG =
      TransformerConfig.with(PROCESS_INSTANCE_MODIFICATION).withIntents(MODIFIED);

  public static final TransformerConfig RESOURCE_CONFIG =
      TransformerConfig.with(ValueType.RESOURCE)
          .withIntents(ResourceIntent.CREATED, ResourceIntent.DELETED);

  public static final TransformerConfig TENANT_CONFIG =
      TransformerConfig.with(TENANT)
          .withIntents(TenantIntent.CREATED, TenantIntent.UPDATED, TenantIntent.DELETED)
          .withDataCleanupIntents(TenantIntent.DELETED);

  public static final TransformerConfig TENANT_ENTITY_CONFIG =
      TransformerConfig.with(TENANT)
          .withIntents(TenantIntent.ENTITY_ADDED, TenantIntent.ENTITY_REMOVED);

  public static final TransformerConfig ROLE_CONFIG =
      TransformerConfig.with(ROLE)
          .withIntents(RoleIntent.CREATED, RoleIntent.UPDATED, RoleIntent.DELETED)
          .withDataCleanupIntents(RoleIntent.DELETED);

  public static final TransformerConfig ROLE_ENTITY_CONFIG =
      TransformerConfig.with(ROLE).withIntents(RoleIntent.ENTITY_ADDED, RoleIntent.ENTITY_REMOVED);

  public static final TransformerConfig USER_CONFIG =
      TransformerConfig.with(USER)
          .withIntents(UserIntent.CREATED, UserIntent.UPDATED, UserIntent.DELETED)
          .withDataCleanupIntents(UserIntent.DELETED);

  public static final TransformerConfig USER_TASK_CONFIG =
      TransformerConfig.with(USER_TASK)
          .withIntents(UserTaskIntent.UPDATED, UserTaskIntent.ASSIGNED, UserTaskIntent.COMPLETED)
          .withRejections(UserTaskIntent.UPDATE, UserTaskIntent.ASSIGN, UserTaskIntent.COMPLETE)
          .withRejectionTypes(RejectionType.INVALID_STATE);

  public static final TransformerConfig VARIABLE_ADD_UPDATE_CONFIG =
      TransformerConfig.with(VARIABLE).withIntents(VariableIntent.CREATED, VariableIntent.UPDATED);

  private static final Map<EntityType, AuditLogEntityType> ENTITY_TYPE_AUDIT_LOG_ENTITY_TYPE_MAP =
      Map.ofEntries(
          Map.entry(EntityType.USER, AuditLogEntityType.USER),
          Map.entry(EntityType.CLIENT, AuditLogEntityType.USER),
          Map.entry(EntityType.GROUP, AuditLogEntityType.GROUP),
          Map.entry(EntityType.ROLE, AuditLogEntityType.ROLE),
          Map.entry(EntityType.MAPPING_RULE, AuditLogEntityType.MAPPING_RULE));

  public static AuditLogEntityType mapEntityTypeToAuditLogEntityType(final EntityType entityType) {
    if (entityType == null) {
      return null;
    }
    return ENTITY_TYPE_AUDIT_LOG_ENTITY_TYPE_MAP.getOrDefault(entityType, null);
  }
}
