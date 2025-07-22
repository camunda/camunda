/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
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
import io.camunda.zeebe.protocol.impl.record.value.compensation.CompensationSubscriptionRecord;
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
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class TypedEventRegistry {

  public static final Map<ValueType, Class<? extends UnifiedRecordValue>> EVENT_REGISTRY;
  public static final Map<Class<? extends UnifiedRecordValue>, ValueType> TYPE_REGISTRY;

  static {
    final EnumMap<ValueType, Class<? extends UnifiedRecordValue>> registry =
        new EnumMap<>(ValueType.class);
    registry.put(ValueType.DEPLOYMENT, DeploymentRecord.class);
    registry.put(ValueType.JOB, JobRecord.class);
    registry.put(ValueType.PROCESS_INSTANCE, ProcessInstanceRecord.class);
    registry.put(ValueType.INCIDENT, IncidentRecord.class);
    registry.put(ValueType.MESSAGE, MessageRecord.class);
    registry.put(ValueType.MESSAGE_BATCH, MessageBatchRecord.class);
    registry.put(ValueType.MESSAGE_SUBSCRIPTION, MessageSubscriptionRecord.class);
    registry.put(
        ValueType.MESSAGE_START_EVENT_SUBSCRIPTION, MessageStartEventSubscriptionRecord.class);
    registry.put(ValueType.PROCESS_MESSAGE_SUBSCRIPTION, ProcessMessageSubscriptionRecord.class);
    registry.put(ValueType.JOB_BATCH, JobBatchRecord.class);
    registry.put(ValueType.TIMER, TimerRecord.class);
    registry.put(ValueType.VARIABLE, VariableRecord.class);
    registry.put(ValueType.VARIABLE_DOCUMENT, VariableDocumentRecord.class);
    registry.put(ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationRecord.class);
    registry.put(ValueType.PROCESS_INSTANCE_MODIFICATION, ProcessInstanceModificationRecord.class);
    registry.put(ValueType.PROCESS_INSTANCE_MIGRATION, ProcessInstanceMigrationRecord.class);
    registry.put(ValueType.ERROR, ErrorRecord.class);
    registry.put(ValueType.PROCESS_INSTANCE_RESULT, ProcessInstanceResultRecord.class);
    registry.put(ValueType.PROCESS, ProcessRecord.class);
    registry.put(ValueType.DEPLOYMENT_DISTRIBUTION, DeploymentDistributionRecord.class);
    registry.put(ValueType.PROCESS_EVENT, ProcessEventRecord.class);
    registry.put(ValueType.DECISION, DecisionRecord.class);
    registry.put(ValueType.DECISION_REQUIREMENTS, DecisionRequirementsRecord.class);
    registry.put(ValueType.DECISION_EVALUATION, DecisionEvaluationRecord.class);
    registry.put(ValueType.RESOURCE_DELETION, ResourceDeletionRecord.class);
    registry.put(ValueType.AD_HOC_SUB_PROCESS_INSTRUCTION, AdHocSubProcessInstructionRecord.class);
    registry.put(ValueType.COMMAND_DISTRIBUTION, CommandDistributionRecord.class);

    registry.put(ValueType.CHECKPOINT, CheckpointRecord.class);
    registry.put(ValueType.ESCALATION, EscalationRecord.class);
    registry.put(ValueType.SIGNAL_SUBSCRIPTION, SignalSubscriptionRecord.class);
    registry.put(ValueType.SIGNAL, SignalRecord.class);
    registry.put(ValueType.PROCESS_INSTANCE_BATCH, ProcessInstanceBatchRecord.class);
    registry.put(ValueType.FORM, FormRecord.class);
    registry.put(ValueType.RESOURCE, ResourceRecord.class);
    registry.put(ValueType.USER_TASK, UserTaskRecord.class);
    registry.put(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionRecord.class);
    registry.put(ValueType.MESSAGE_CORRELATION, MessageCorrelationRecord.class);
    registry.put(ValueType.USER, UserRecord.class);
    registry.put(ValueType.CLOCK, ClockRecord.class);
    registry.put(ValueType.AUTHORIZATION, AuthorizationRecord.class);
    registry.put(ValueType.TENANT, TenantRecord.class);
    registry.put(ValueType.ROLE, RoleRecord.class);
    registry.put(ValueType.SCALE, ScaleRecord.class);
    registry.put(ValueType.MAPPING_RULE, MappingRuleRecord.class);
    registry.put(ValueType.GROUP, GroupRecord.class);
    registry.put(ValueType.IDENTITY_SETUP, IdentitySetupRecord.class);
    registry.put(ValueType.BATCH_OPERATION_CREATION, BatchOperationCreationRecord.class);
    registry.put(ValueType.BATCH_OPERATION_EXECUTION, BatchOperationExecutionRecord.class);
    registry.put(ValueType.BATCH_OPERATION_CHUNK, BatchOperationChunkRecord.class);
    registry.put(ValueType.USAGE_METRIC, UsageMetricRecord.class);
    registry.put(
        ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
        BatchOperationLifecycleManagementRecord.class);
    registry.put(
        ValueType.BATCH_OPERATION_PARTITION_LIFECYCLE,
        BatchOperationPartitionLifecycleRecord.class);
    registry.put(
        ValueType.BATCH_OPERATION_INITIALIZATION, BatchOperationInitializationRecord.class);
    registry.put(ValueType.ASYNC_REQUEST, AsyncRequestRecord.class);
    registry.put(ValueType.MULTI_INSTANCE, MultiInstanceRecord.class);
    registry.put(ValueType.RUNTIME_INSTRUCTION, RuntimeInstructionRecord.class);

    EVENT_REGISTRY = Collections.unmodifiableMap(registry);

    final Map<Class<? extends UnifiedRecordValue>, ValueType> typeRegistry = new HashMap<>();
    EVENT_REGISTRY.forEach((e, c) -> typeRegistry.put(c, e));
    TYPE_REGISTRY = Collections.unmodifiableMap(typeRegistry);
  }

  private TypedEventRegistry() {}
}
