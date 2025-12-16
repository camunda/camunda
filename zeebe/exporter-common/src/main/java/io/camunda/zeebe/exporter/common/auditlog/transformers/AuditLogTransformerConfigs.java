/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import static io.camunda.zeebe.protocol.record.ValueType.BATCH_OPERATION_CREATION;
import static io.camunda.zeebe.protocol.record.ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT;
import static io.camunda.zeebe.protocol.record.ValueType.DECISION_EVALUATION;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_CREATION;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_MIGRATION;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_MODIFICATION;
import static io.camunda.zeebe.protocol.record.ValueType.USER;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent.MODIFIED;

import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer.TransformerConfig;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import java.util.Set;

public class AuditLogTransformerConfigs {
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

  public static final TransformerConfig USER_CONFIG =
      TransformerConfig.with(USER)
          .withIntents(UserIntent.CREATED, UserIntent.UPDATED, UserIntent.DELETED);
}
