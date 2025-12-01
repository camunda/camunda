/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.broker.protocol;

import io.camunda.zeebe.protocol.impl.record.value.AsyncRequestRecord;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.IdentitySetupRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationInitializationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationLifecycleManagementRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationPartitionLifecycleRecord;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import io.camunda.zeebe.protocol.impl.record.value.compensation.CompensationSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.conditionalevaluation.ConditionalEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.impl.record.value.escalation.EscalationRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.history.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.metrics.UsageMetricRecord;
import io.camunda.zeebe.protocol.impl.record.value.multiinstance.MultiInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessEventRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.RuntimeInstructionRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.ValueTypeMapping;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Provides a mapping of {@link ValueType} to their implementation classes in the protocol-impl
 * module. This is useful for tests that need to work with the concrete implementations rather than
 * the interfaces.
 */
public final class ValueTypeImplMapping {
  private final Map<ValueType, Class<? extends RecordValue>> implMapping;

  private ValueTypeImplMapping() {
    implMapping = Collections.unmodifiableMap(loadImplMapping());
  }

  /**
   * Returns the implementation class for the given value type.
   *
   * @param valueType the value type
   * @return the implementation class
   * @throws IllegalArgumentException if no such mapping exists
   */
  public static Class<? extends RecordValue> getImplClass(final ValueType valueType) {
    final Class<? extends RecordValue> implClass = Singleton.INSTANCE.implMapping.get(valueType);
    if (implClass == null) {
      throw new IllegalArgumentException(
          String.format(
              "Expected value type to be one of %s, but was %s",
              Singleton.INSTANCE.implMapping.keySet(), valueType));
    }

    return implClass;
  }

  private Map<ValueType, Class<? extends RecordValue>> loadImplMapping() {
    final Map<ValueType, Class<? extends RecordValue>> mapping = new EnumMap<>(ValueType.class);

    // Map each ValueType to its implementation class (sorted alphabetically)
    mapping.put(ValueType.AD_HOC_SUB_PROCESS_INSTRUCTION, AdHocSubProcessInstructionRecord.class);
    mapping.put(ValueType.ASYNC_REQUEST, AsyncRequestRecord.class);
    mapping.put(ValueType.AUTHORIZATION, AuthorizationRecord.class);
    mapping.put(ValueType.BATCH_OPERATION_CHUNK, BatchOperationChunkRecord.class);
    mapping.put(ValueType.BATCH_OPERATION_CREATION, BatchOperationCreationRecord.class);
    mapping.put(ValueType.BATCH_OPERATION_EXECUTION, BatchOperationExecutionRecord.class);
    mapping.put(ValueType.BATCH_OPERATION_INITIALIZATION, BatchOperationInitializationRecord.class);
    mapping.put(
        ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
        BatchOperationLifecycleManagementRecord.class);
    mapping.put(
        ValueType.BATCH_OPERATION_PARTITION_LIFECYCLE,
        BatchOperationPartitionLifecycleRecord.class);
    mapping.put(ValueType.CHECKPOINT, CheckpointRecord.class);
    mapping.put(ValueType.CLOCK, ClockRecord.class);
    mapping.put(ValueType.CLUSTER_VARIABLE, ClusterVariableRecord.class);
    mapping.put(ValueType.COMMAND_DISTRIBUTION, CommandDistributionRecord.class);
    mapping.put(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionRecord.class);
    mapping.put(ValueType.CONDITIONAL_EVALUATION, ConditionalEvaluationRecord.class);
    mapping.put(ValueType.CONDITIONAL_SUBSCRIPTION, ConditionalSubscriptionRecord.class);
    mapping.put(ValueType.DECISION, DecisionRecord.class);
    mapping.put(ValueType.DECISION_EVALUATION, DecisionEvaluationRecord.class);
    mapping.put(ValueType.DECISION_REQUIREMENTS, DecisionRequirementsRecord.class);
    mapping.put(ValueType.DEPLOYMENT, DeploymentRecord.class);
    mapping.put(ValueType.DEPLOYMENT_DISTRIBUTION, DeploymentDistributionRecord.class);
    mapping.put(ValueType.ERROR, ErrorRecord.class);
    mapping.put(ValueType.ESCALATION, EscalationRecord.class);
    mapping.put(ValueType.FORM, FormRecord.class);
    mapping.put(ValueType.GROUP, GroupRecord.class);
    mapping.put(ValueType.HISTORY_DELETION, HistoryDeletionRecord.class);
    mapping.put(ValueType.IDENTITY_SETUP, IdentitySetupRecord.class);
    mapping.put(ValueType.INCIDENT, IncidentRecord.class);
    mapping.put(ValueType.JOB, JobRecord.class);
    mapping.put(ValueType.JOB_BATCH, JobBatchRecord.class);
    mapping.put(ValueType.MAPPING_RULE, MappingRuleRecord.class);
    mapping.put(ValueType.MESSAGE, MessageRecord.class);
    mapping.put(ValueType.MESSAGE_BATCH, MessageBatchRecord.class);
    mapping.put(ValueType.MESSAGE_CORRELATION, MessageCorrelationRecord.class);
    mapping.put(
        ValueType.MESSAGE_START_EVENT_SUBSCRIPTION, MessageStartEventSubscriptionRecord.class);
    mapping.put(ValueType.MESSAGE_SUBSCRIPTION, MessageSubscriptionRecord.class);
    mapping.put(ValueType.MULTI_INSTANCE, MultiInstanceRecord.class);
    mapping.put(ValueType.PROCESS, ProcessRecord.class);
    mapping.put(ValueType.PROCESS_EVENT, ProcessEventRecord.class);
    mapping.put(ValueType.PROCESS_INSTANCE, ProcessInstanceRecord.class);
    mapping.put(ValueType.PROCESS_INSTANCE_BATCH, ProcessInstanceBatchRecord.class);
    mapping.put(ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationRecord.class);
    mapping.put(ValueType.PROCESS_INSTANCE_MIGRATION, ProcessInstanceMigrationRecord.class);
    mapping.put(ValueType.PROCESS_INSTANCE_MODIFICATION, ProcessInstanceModificationRecord.class);
    mapping.put(ValueType.PROCESS_INSTANCE_RESULT, ProcessInstanceResultRecord.class);
    mapping.put(ValueType.PROCESS_MESSAGE_SUBSCRIPTION, ProcessMessageSubscriptionRecord.class);
    mapping.put(ValueType.RESOURCE, ResourceRecord.class);
    mapping.put(ValueType.RESOURCE_DELETION, ResourceDeletionRecord.class);
    mapping.put(ValueType.ROLE, RoleRecord.class);
    mapping.put(ValueType.RUNTIME_INSTRUCTION, RuntimeInstructionRecord.class);
    mapping.put(ValueType.SCALE, ScaleRecord.class);
    mapping.put(ValueType.SIGNAL, SignalRecord.class);
    mapping.put(ValueType.SIGNAL_SUBSCRIPTION, SignalSubscriptionRecord.class);
    mapping.put(ValueType.TENANT, TenantRecord.class);
    mapping.put(ValueType.TIMER, TimerRecord.class);
    mapping.put(ValueType.USER, UserRecord.class);
    mapping.put(ValueType.USER_TASK, UserTaskRecord.class);
    mapping.put(ValueType.USAGE_METRIC, UsageMetricRecord.class);
    mapping.put(ValueType.VARIABLE, VariableRecord.class);
    mapping.put(ValueType.VARIABLE_DOCUMENT, VariableDocumentRecord.class);

    // Validate that all accepted value types have a mapping
    for (final ValueType valueType : ValueTypeMapping.getAcceptedValueTypes()) {
      if (!mapping.containsKey(valueType)) {
        throw new IllegalStateException(
            "Missing implementation mapping for value type: " + valueType);
      }
    }

    return mapping;
  }

  private static final class Singleton {
    private static final ValueTypeImplMapping INSTANCE = new ValueTypeImplMapping();
  }
}
