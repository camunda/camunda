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

import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.util.collection.Tuple;

public class AuditLogTransformerConfigs {
  public static final TransformerConfig AUTHORIZATION_CONFIG =
      TransformerConfig.with(AUTHORIZATION)
          .withIntents(
              Tuple.of(AuthorizationIntent.CREATED, AuditLogOperationType.CREATE),
              Tuple.of(AuthorizationIntent.UPDATED, AuditLogOperationType.UPDATE),
              Tuple.of(AuthorizationIntent.DELETED, AuditLogOperationType.DELETE))
          .withCategory(AuditLogOperationCategory.ADMIN)
          .withEntityType(AuditLogEntityType.AUTHORIZATION);

  public static final TransformerConfig BATCH_OPERATION_CREATION_CONFIG =
      TransformerConfig.with(BATCH_OPERATION_CREATION)
          .withIntents(Tuple.of(BatchOperationIntent.CREATED, AuditLogOperationType.CREATE))
          .withCategory(AuditLogOperationCategory.DEPLOYED_RESOURCES)
          .withEntityType(AuditLogEntityType.BATCH);

  public static final TransformerConfig BATCH_OPERATION_LIFECYCLE_MANAGEMENT_CONFIG =
      TransformerConfig.with(BATCH_OPERATION_LIFECYCLE_MANAGEMENT)
          .withIntents(
              Tuple.of(BatchOperationIntent.RESUMED, AuditLogOperationType.CREATE),
              Tuple.of(BatchOperationIntent.SUSPENDED, AuditLogOperationType.SUSPEND),
              Tuple.of(BatchOperationIntent.CANCELED, AuditLogOperationType.CANCEL))
          .withRejections(
              BatchOperationIntent.RESUME,
              BatchOperationIntent.SUSPEND,
              BatchOperationIntent.CANCEL)
          .withRejectionTypes(RejectionType.INVALID_STATE)
          .withCategory(AuditLogOperationCategory.DEPLOYED_RESOURCES)
          .withEntityType(AuditLogEntityType.BATCH);

  public static final TransformerConfig DECISION_EVALUATION_CONFIG =
      TransformerConfig.with(DECISION_EVALUATION)
          .withIntents(
              Tuple.of(DecisionEvaluationIntent.EVALUATED, AuditLogOperationType.EVALUATE),
              Tuple.of(DecisionEvaluationIntent.FAILED, AuditLogOperationType.EVALUATE))
          .withCategory(AuditLogOperationCategory.DEPLOYED_RESOURCES)
          .withEntityType(AuditLogEntityType.DECISION);

  public static final TransformerConfig DECISION_CONFIG =
      TransformerConfig.with(DECISION)
          .withIntents(
              Tuple.of(DecisionIntent.CREATED, AuditLogOperationType.CREATE),
              Tuple.of(DecisionIntent.DELETED, AuditLogOperationType.DELETE))
          .withCategory(AuditLogOperationCategory.DEPLOYED_RESOURCES)
          .withEntityType(AuditLogEntityType.DECISION);

  public static final TransformerConfig DECISION_REQUIREMENTS_CONFIG =
      TransformerConfig.with(DECISION_REQUIREMENTS)
          .withIntents(
              Tuple.of(DecisionRequirementsIntent.CREATED, AuditLogOperationType.CREATE),
              Tuple.of(DecisionRequirementsIntent.DELETED, AuditLogOperationType.DELETE))
          .withCategory(AuditLogOperationCategory.DEPLOYED_RESOURCES)
          .withEntityType(AuditLogEntityType.RESOURCE);

  public static final TransformerConfig FORM_CONFIG =
      TransformerConfig.with(ValueType.FORM)
          .withIntents(
              Tuple.of(FormIntent.CREATED, AuditLogOperationType.CREATE),
              Tuple.of(FormIntent.DELETED, AuditLogOperationType.DELETE))
          .withCategory(AuditLogOperationCategory.DEPLOYED_RESOURCES)
          .withEntityType(AuditLogEntityType.RESOURCE);

  public static final TransformerConfig GROUP_CONFIG =
      TransformerConfig.with(GROUP)
          .withIntents(
              Tuple.of(GroupIntent.CREATED, AuditLogOperationType.CREATE),
              Tuple.of(GroupIntent.UPDATED, AuditLogOperationType.UPDATE),
              Tuple.of(GroupIntent.DELETED, AuditLogOperationType.DELETE))
          .withCategory(AuditLogOperationCategory.ADMIN)
          .withEntityType(AuditLogEntityType.GROUP);

  public static final TransformerConfig GROUP_ENTITY_CONFIG =
      TransformerConfig.with(GROUP)
          .withIntents(
              Tuple.of(GroupIntent.ENTITY_ADDED, AuditLogOperationType.ASSIGN),
              Tuple.of(GroupIntent.ENTITY_REMOVED, AuditLogOperationType.UNASSIGN))
          .withCategory(AuditLogOperationCategory.ADMIN)
          .withEntityType(AuditLogEntityType.GROUP);

  public static final TransformerConfig INCIDENT_RESOLUTION_CONFIG =
      TransformerConfig.with(ValueType.INCIDENT)
          .withIntents(Tuple.of(IncidentIntent.RESOLVED, AuditLogOperationType.RESOLVE))
          .withRejections(IncidentIntent.RESOLVE, RejectionType.INVALID_STATE)
          .withCategory(AuditLogOperationCategory.DEPLOYED_RESOURCES)
          .withEntityType(AuditLogEntityType.INCIDENT);

  public static final TransformerConfig MAPPING_RULE_CONFIG =
      TransformerConfig.with(MAPPING_RULE)
          .withIntents(
              Tuple.of(MappingRuleIntent.CREATED, AuditLogOperationType.CREATE),
              Tuple.of(MappingRuleIntent.UPDATED, AuditLogOperationType.UPDATE),
              Tuple.of(MappingRuleIntent.DELETED, AuditLogOperationType.DELETE))
          .withCategory(AuditLogOperationCategory.ADMIN)
          .withEntityType(AuditLogEntityType.MAPPING_RULE);

  public static final TransformerConfig PROCESS_CONFIG =
      TransformerConfig.with(PROCESS)
          .withIntents(
              Tuple.of(ProcessIntent.CREATED, AuditLogOperationType.CREATE),
              Tuple.of(ProcessIntent.DELETED, AuditLogOperationType.DELETE))
          .withCategory(AuditLogOperationCategory.DEPLOYED_RESOURCES)
          .withEntityType(AuditLogEntityType.RESOURCE);

  public static final TransformerConfig PROCESS_INSTANCE_CANCEL_CONFIG =
      TransformerConfig.with(ValueType.PROCESS_INSTANCE)
          .withIntents(Tuple.of(ProcessInstanceIntent.CANCELING, AuditLogOperationType.CANCEL))
          .withRejections(ProcessInstanceIntent.CANCEL, RejectionType.INVALID_STATE)
          .withCategory(AuditLogOperationCategory.DEPLOYED_RESOURCES)
          .withEntityType(AuditLogEntityType.PROCESS_INSTANCE);

  public static final TransformerConfig PROCESS_INSTANCE_CREATION_CONFIG =
      TransformerConfig.with(PROCESS_INSTANCE_CREATION)
          .withIntents(
              Tuple.of(ProcessInstanceCreationIntent.CREATED, AuditLogOperationType.CREATE))
          .withCategory(AuditLogOperationCategory.DEPLOYED_RESOURCES)
          .withEntityType(AuditLogEntityType.PROCESS_INSTANCE);

  public static final TransformerConfig PROCESS_INSTANCE_MIGRATION_CONFIG =
      TransformerConfig.with(PROCESS_INSTANCE_MIGRATION)
          .withIntents(
              Tuple.of(ProcessInstanceMigrationIntent.MIGRATED, AuditLogOperationType.MIGRATE))
          .withRejections(
              ProcessInstanceMigrationIntent.MIGRATE,
              RejectionType.INVALID_STATE,
              RejectionType.PROCESSING_ERROR)
          .withCategory(AuditLogOperationCategory.DEPLOYED_RESOURCES)
          .withEntityType(AuditLogEntityType.PROCESS_INSTANCE);

  public static final TransformerConfig PROCESS_INSTANCE_MODIFICATION_CONFIG =
      TransformerConfig.with(PROCESS_INSTANCE_MODIFICATION)
          .withIntents(
              Tuple.of(ProcessInstanceModificationIntent.MODIFIED, AuditLogOperationType.MODIFY))
          .withCategory(AuditLogOperationCategory.DEPLOYED_RESOURCES)
          .withEntityType(AuditLogEntityType.PROCESS_INSTANCE);

  public static final TransformerConfig RESOURCE_CONFIG =
      TransformerConfig.with(ValueType.RESOURCE)
          .withIntents(
              Tuple.of(ResourceIntent.CREATED, AuditLogOperationType.CREATE),
              Tuple.of(ResourceIntent.DELETED, AuditLogOperationType.DELETE))
          .withCategory(AuditLogOperationCategory.DEPLOYED_RESOURCES)
          .withEntityType(AuditLogEntityType.RESOURCE);

  public static final TransformerConfig TENANT_CONFIG =
      TransformerConfig.with(TENANT)
          .withIntents(
              Tuple.of(TenantIntent.CREATED, AuditLogOperationType.CREATE),
              Tuple.of(TenantIntent.UPDATED, AuditLogOperationType.UPDATE),
              Tuple.of(TenantIntent.DELETED, AuditLogOperationType.DELETE))
          .withCategory(AuditLogOperationCategory.ADMIN)
          .withEntityType(AuditLogEntityType.TENANT);

  public static final TransformerConfig TENANT_ENTITY_CONFIG =
      TransformerConfig.with(TENANT)
          .withIntents(
              Tuple.of(TenantIntent.ENTITY_ADDED, AuditLogOperationType.ASSIGN),
              Tuple.of(TenantIntent.ENTITY_REMOVED, AuditLogOperationType.UNASSIGN))
          .withCategory(AuditLogOperationCategory.ADMIN)
          .withEntityType(AuditLogEntityType.TENANT);

  public static final TransformerConfig ROLE_CONFIG =
      TransformerConfig.with(ROLE)
          .withIntents(
              Tuple.of(RoleIntent.CREATED, AuditLogOperationType.CREATE),
              Tuple.of(RoleIntent.UPDATED, AuditLogOperationType.UPDATE),
              Tuple.of(RoleIntent.DELETED, AuditLogOperationType.DELETE))
          .withCategory(AuditLogOperationCategory.ADMIN)
          .withEntityType(AuditLogEntityType.ROLE);

  public static final TransformerConfig ROLE_ENTITY_CONFIG =
      TransformerConfig.with(ROLE)
          .withIntents(
              Tuple.of(RoleIntent.ENTITY_ADDED, AuditLogOperationType.ASSIGN),
              Tuple.of(RoleIntent.ENTITY_REMOVED, AuditLogOperationType.UNASSIGN))
          .withCategory(AuditLogOperationCategory.ADMIN)
          .withEntityType(AuditLogEntityType.ROLE);

  public static final TransformerConfig USER_CONFIG =
      TransformerConfig.with(USER)
          .withIntents(
              Tuple.of(UserIntent.CREATED, AuditLogOperationType.CREATE),
              Tuple.of(UserIntent.UPDATED, AuditLogOperationType.UPDATE),
              Tuple.of(UserIntent.DELETED, AuditLogOperationType.DELETE))
          .withCategory(AuditLogOperationCategory.ADMIN)
          .withEntityType(AuditLogEntityType.USER);

  public static final TransformerConfig USER_TASK_CONFIG =
      TransformerConfig.with(USER_TASK)
          .withIntents(
              Tuple.of(UserTaskIntent.UPDATED, AuditLogOperationType.UPDATE),
              Tuple.of(UserTaskIntent.ASSIGNED, AuditLogOperationType.ASSIGN),
              Tuple.of(UserTaskIntent.COMPLETED, AuditLogOperationType.COMPLETE))
          .withRejections(UserTaskIntent.UPDATE, UserTaskIntent.ASSIGN, UserTaskIntent.COMPLETE)
          .withRejectionTypes(RejectionType.INVALID_STATE)
          .withCategory(AuditLogOperationCategory.DEPLOYED_RESOURCES)
          .withEntityType(AuditLogEntityType.USER_TASK);

  public static final TransformerConfig VARIABLE_ADD_UPDATE_CONFIG =
      TransformerConfig.with(VARIABLE)
          .withIntents(
              Tuple.of(VariableIntent.CREATED, AuditLogOperationType.CREATE),
              Tuple.of(VariableIntent.UPDATED, AuditLogOperationType.UPDATE),
              Tuple.of(UserIntent.DELETED, AuditLogOperationType.DELETE))
          .withCategory(AuditLogOperationCategory.DEPLOYED_RESOURCES)
          .withEntityType(AuditLogEntityType.VARIABLE);
}
