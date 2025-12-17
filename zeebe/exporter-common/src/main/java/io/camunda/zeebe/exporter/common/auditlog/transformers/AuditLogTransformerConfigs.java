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
import static io.camunda.zeebe.protocol.record.ValueType.DECISION_EVALUATION;
import static io.camunda.zeebe.protocol.record.ValueType.MAPPING_RULE;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_CREATION;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_MIGRATION;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_MODIFICATION;
import static io.camunda.zeebe.protocol.record.ValueType.TENANT;
import static io.camunda.zeebe.protocol.record.ValueType.USER;
import static io.camunda.zeebe.protocol.record.ValueType.USER_TASK;
import static io.camunda.zeebe.protocol.record.ValueType.VARIABLE;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent.MODIFIED;

import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer.TransformerConfig;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import java.util.Set;

public class AuditLogTransformerConfigs {
  public static final TransformerConfig AUTHORIZATION_CONFIG =
      TransformerConfig.with(AUTHORIZATION)
          .withIntents(
              AuthorizationIntent.CREATED,
              AuthorizationIntent.UPDATED,
              AuthorizationIntent.DELETED);

  public static final TransformerConfig BATCH_OPERATION_CREATION_CONFIG =
      TransformerConfig.with(BATCH_OPERATION_CREATION).withIntents(BatchOperationIntent.CREATED);

  public static final TransformerConfig BATCH_OPERATION_LIFECYCLE_MANAGEMENT_CONFIG =
      new TransformerConfig(
          BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
          Set.of(
              BatchOperationIntent.RESUMED,
              BatchOperationIntent.SUSPENDED,
              BatchOperationIntent.CANCELED),
          Set.of(
              BatchOperationIntent.RESUME,
              BatchOperationIntent.SUSPEND,
              BatchOperationIntent.CANCEL),
          Set.of(RejectionType.INVALID_STATE));

  public static final TransformerConfig DECISION_EVALUATION_CONFIG =
      TransformerConfig.with(DECISION_EVALUATION)
          .withIntents(DecisionEvaluationIntent.EVALUATED, DecisionEvaluationIntent.FAILED);

  public static final TransformerConfig INCIDENT_RESOLUTION_CONFIG =
      TransformerConfig.with(ValueType.INCIDENT)
          .withIntents(IncidentIntent.RESOLVED)
          .withRejections(IncidentIntent.RESOLVE, RejectionType.INVALID_STATE);

  public static final TransformerConfig MAPPING_RULE_CONFIG =
      TransformerConfig.with(MAPPING_RULE)
          .withIntents(
              MappingRuleIntent.CREATED, MappingRuleIntent.UPDATED, MappingRuleIntent.DELETED);

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
          .withIntents(TenantIntent.CREATED, TenantIntent.UPDATED, TenantIntent.DELETED);

  public static final TransformerConfig TENANT_ENTITY_CONFIG =
      TransformerConfig.with(TENANT)
          .withIntents(TenantIntent.ENTITY_ADDED, TenantIntent.ENTITY_REMOVED);

  public static final TransformerConfig USER_CONFIG =
      TransformerConfig.with(USER)
          .withIntents(UserIntent.CREATED, UserIntent.UPDATED, UserIntent.DELETED);

  public static final TransformerConfig USER_TASK_CONFIG =
      new TransformerConfig(
          USER_TASK,
          Set.of(UserTaskIntent.UPDATED, UserTaskIntent.ASSIGNED, UserTaskIntent.COMPLETED),
          Set.of(UserTaskIntent.UPDATE, UserTaskIntent.ASSIGN, UserTaskIntent.COMPLETE),
          Set.of(RejectionType.INVALID_STATE));

  public static final TransformerConfig VARIABLE_ADD_UPDATE_CONFIG =
      TransformerConfig.with(VARIABLE).withIntents(VariableIntent.CREATED, VariableIntent.UPDATED);
}
