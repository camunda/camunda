/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.EscalationIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.MessageBatchIntent;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessInstructionRecordValue;
import io.camunda.zeebe.protocol.record.value.AsyncRequestRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationExecutionRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationInitializationRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationLifecycleManagementRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationPartitionLifecycleRecordValue;
import io.camunda.zeebe.protocol.record.value.ClockRecordValue;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.protocol.record.value.CompensationSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.DeploymentDistributionRecordValue;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.ErrorRecordValue;
import io.camunda.zeebe.protocol.record.value.EscalationRecordValue;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.IdentitySetupRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageCorrelationRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceResultRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ResourceDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.SignalRecordValue;
import io.camunda.zeebe.protocol.record.value.SignalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.Form;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.protocol.record.value.deployment.Resource;
import io.camunda.zeebe.protocol.record.value.scaling.ScaleRecordValue;
import java.time.Duration;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class RecordingExporter implements Exporter {
  public static final long DEFAULT_MAX_WAIT_TIME = Duration.ofSeconds(5).toMillis();

  private static final ConcurrentSkipListMap<Integer, Record<?>> RECORDS =
      new ConcurrentSkipListMap<Integer, Record<?>>();
  private static final Lock LOCK = new ReentrantLock();
  private static final Condition IS_EMPTY = LOCK.newCondition();

  private static long maximumWaitTime = DEFAULT_MAX_WAIT_TIME;
  private static volatile boolean autoAcknowledge = true;

  private Controller controller;

  public static void setMaximumWaitTime(final long maximumWaitTime) {
    RecordingExporter.maximumWaitTime = maximumWaitTime;
  }

  /**
   * Disables the default awaiting behavior of the recording exporter by setting the wait time to 0.
   *
   * <p>By default, the RecordingExporter awaits incoming records until all expected records are
   * retrieved (the expectation is controlled with limit), or a maximumWaitTime is surpassed. The
   * wait time can also be controlled with {@link #setMaximumWaitTime(long)} and is reset along with
   * the recorded records by {@link #reset()}.
   */
  public static void disableAwaitingIncomingRecords() {
    setMaximumWaitTime(0);
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
  }

  @Override
  public void export(final Record<?> record) {
    LOCK.lock();
    try {
      RECORDS.put(RECORDS.size(), record.copyOf());
      IS_EMPTY.signal();
      if (controller != null && autoAcknowledge) { // the engine tests do not open the exporter
        controller.updateLastExportedRecordPosition(record.getPosition());
      }
    } finally {
      LOCK.unlock();
    }
  }

  @Override
  public void purge() throws Exception {
    LOCK.lock();
    try {
      RECORDS.clear();
    } finally {
      LOCK.unlock();
    }
  }

  public static Collection<Record<?>> getRecords() {
    return RECORDS.values();
  }

  public static void reset() {
    LOCK.lock();
    try {
      maximumWaitTime = DEFAULT_MAX_WAIT_TIME;
      RECORDS.clear();
      autoAcknowledge = true;
    } finally {
      LOCK.unlock();
    }
  }

  @SuppressWarnings("unchecked")
  protected static <T extends RecordValue> Stream<Record<T>> records(
      final ValueType valueType, final Class<T> valueClass) {
    final Spliterator<Record<?>> spliterator =
        Spliterators.spliteratorUnknownSize(new AwaitingRecordIterator(), Spliterator.ORDERED);
    return StreamSupport.stream(spliterator, false)
        .filter(r -> r.getValueType() == valueType)
        .map(r -> (Record<T>) r);
  }

  public static RecordStream records() {
    final Spliterator<Record<? extends RecordValue>> spliterator =
        Spliterators.spliteratorUnknownSize(new AwaitingRecordIterator(), Spliterator.ORDERED);
    return new RecordStream(
        StreamSupport.stream(spliterator, false).map(r -> (Record<RecordValue>) r));
  }

  public static MessageSubscriptionRecordStream messageSubscriptionRecords() {
    return new MessageSubscriptionRecordStream(
        records(ValueType.MESSAGE_SUBSCRIPTION, MessageSubscriptionRecordValue.class));
  }

  public static MessageSubscriptionRecordStream messageSubscriptionRecords(
      final MessageSubscriptionIntent intent) {
    return messageSubscriptionRecords().withIntent(intent);
  }

  public static MessageStartEventSubscriptionRecordStream messageStartEventSubscriptionRecords() {
    return new MessageStartEventSubscriptionRecordStream(
        records(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            MessageStartEventSubscriptionRecordValue.class));
  }

  public static MessageStartEventSubscriptionRecordStream messageStartEventSubscriptionRecords(
      final MessageStartEventSubscriptionIntent intent) {
    return messageStartEventSubscriptionRecords().withIntent(intent);
  }

  public static MessageCorrelationRecordStream messageCorrelationRecords() {
    return new MessageCorrelationRecordStream(
        records(ValueType.MESSAGE_CORRELATION, MessageCorrelationRecordValue.class));
  }

  public static MessageCorrelationRecordStream messageCorrelationRecords(
      final MessageCorrelationIntent intent) {
    return messageCorrelationRecords().withIntent(intent);
  }

  public static DeploymentRecordStream deploymentRecords() {
    return new DeploymentRecordStream(records(ValueType.DEPLOYMENT, DeploymentRecordValue.class));
  }

  public static DeploymentRecordStream deploymentRecords(final DeploymentIntent intent) {
    return deploymentRecords().withIntent(intent);
  }

  public static UsageMetricsStream usageMetricsRecords() {
    return new UsageMetricsStream(records(ValueType.USAGE_METRIC, UsageMetricRecordValue.class));
  }

  public static UsageMetricsStream usageMetricsRecords(final UsageMetricIntent intent) {
    return usageMetricsRecords().withIntent(intent);
  }

  public static CommandDistributionRecordStream commandDistributionRecords() {
    return new CommandDistributionRecordStream(
        records(ValueType.COMMAND_DISTRIBUTION, CommandDistributionRecordValue.class));
  }

  public static CommandDistributionRecordStream commandDistributionRecords(
      final CommandDistributionIntent intent) {
    return commandDistributionRecords().withIntent(intent);
  }

  public static ProcessRecordStream processRecords() {
    return new ProcessRecordStream(records(ValueType.PROCESS, Process.class));
  }

  public static ProcessRecordStream processRecords(final ProcessIntent intent) {
    return processRecords().withIntent(intent);
  }

  public static DeploymentDistributionRecordStream deploymentDistributionRecords() {
    return new DeploymentDistributionRecordStream(
        records(ValueType.DEPLOYMENT_DISTRIBUTION, DeploymentDistributionRecordValue.class));
  }

  public static JobRecordStream jobRecords() {
    return new JobRecordStream(records(ValueType.JOB, JobRecordValue.class));
  }

  public static JobRecordStream jobRecords(final JobIntent intent) {
    return jobRecords().withIntent(intent);
  }

  public static JobBatchRecordStream jobBatchRecords() {
    return new JobBatchRecordStream(records(ValueType.JOB_BATCH, JobBatchRecordValue.class));
  }

  public static JobBatchRecordStream jobBatchRecords(final JobBatchIntent intent) {
    return jobBatchRecords().withIntent(intent);
  }

  public static IncidentRecordStream incidentRecords() {
    return new IncidentRecordStream(records(ValueType.INCIDENT, IncidentRecordValue.class));
  }

  public static IncidentRecordStream incidentRecords(final IncidentIntent intent) {
    return incidentRecords().withIntent(intent);
  }

  public static ProcessMessageSubscriptionRecordStream processMessageSubscriptionRecords() {
    return new ProcessMessageSubscriptionRecordStream(
        records(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION, ProcessMessageSubscriptionRecordValue.class));
  }

  public static ProcessMessageSubscriptionRecordStream processMessageSubscriptionRecords(
      final ProcessMessageSubscriptionIntent intent) {
    return processMessageSubscriptionRecords().withIntent(intent);
  }

  public static MessageRecordStream messageRecords() {
    return new MessageRecordStream(records(ValueType.MESSAGE, MessageRecordValue.class));
  }

  public static MessageRecordStream messageRecords(final MessageIntent intent) {
    return messageRecords().withIntent(intent);
  }

  public static MessageBatchRecordStream messageBatchRecords() {
    return new MessageBatchRecordStream(
        records(ValueType.MESSAGE_BATCH, MessageBatchRecordValue.class));
  }

  public static MessageBatchRecordStream messageBatchRecords(final MessageIntent intent) {
    return messageBatchRecords().withIntent(intent);
  }

  public static MessageBatchRecordStream messageBatchRecords(final MessageBatchIntent intent) {
    return messageBatchRecords().withIntent(intent);
  }

  public static ProcessInstanceRecordStream processInstanceRecords() {
    return new ProcessInstanceRecordStream(
        records(ValueType.PROCESS_INSTANCE, ProcessInstanceRecordValue.class));
  }

  public static ProcessInstanceRecordStream processInstanceRecords(
      final ProcessInstanceIntent intent) {
    return processInstanceRecords().withIntent(intent);
  }

  public static ProcessInstanceBatchRecordStream processInstanceBatchRecords() {
    return new ProcessInstanceBatchRecordStream(
        records(ValueType.PROCESS_INSTANCE_BATCH, ProcessInstanceBatchRecordValue.class));
  }

  public static ProcessInstanceBatchRecordStream processInstanceBatchRecords(
      final ProcessInstanceBatchIntent intent) {
    return processInstanceBatchRecords().withIntent(intent);
  }

  public static TimerRecordStream timerRecords() {
    return new TimerRecordStream(records(ValueType.TIMER, TimerRecordValue.class));
  }

  public static TimerRecordStream timerRecords(final TimerIntent intent) {
    return timerRecords().withIntent(intent);
  }

  public static EscalationRecordStream escalationRecords() {
    return new EscalationRecordStream(records(ValueType.ESCALATION, EscalationRecordValue.class));
  }

  public static EscalationRecordStream escalationRecords(final EscalationIntent intent) {
    return escalationRecords().withIntent(intent);
  }

  public static VariableRecordStream variableRecords() {
    return new VariableRecordStream(records(ValueType.VARIABLE, VariableRecordValue.class));
  }

  public static VariableRecordStream variableRecords(final VariableIntent intent) {
    return variableRecords().withIntent(intent);
  }

  public static VariableDocumentRecordStream variableDocumentRecords() {
    return new VariableDocumentRecordStream(
        records(ValueType.VARIABLE_DOCUMENT, VariableDocumentRecordValue.class));
  }

  public static VariableDocumentRecordStream variableDocumentRecords(
      final VariableDocumentIntent intent) {
    return variableDocumentRecords().withIntent(intent);
  }

  public static ProcessInstanceCreationRecordStream processInstanceCreationRecords() {
    return new ProcessInstanceCreationRecordStream(
        records(ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationRecordValue.class));
  }

  public static ProcessInstanceModificationRecordStream processInstanceModificationRecords() {
    return new ProcessInstanceModificationRecordStream(
        records(
            ValueType.PROCESS_INSTANCE_MODIFICATION, ProcessInstanceModificationRecordValue.class));
  }

  public static ProcessInstanceModificationRecordStream processInstanceModificationRecords(
      final ProcessInstanceModificationIntent intent) {
    return processInstanceModificationRecords().withIntent(intent);
  }

  public static ProcessInstanceMigrationRecordStream processInstanceMigrationRecords() {
    return new ProcessInstanceMigrationRecordStream(
        records(ValueType.PROCESS_INSTANCE_MIGRATION, ProcessInstanceMigrationRecordValue.class));
  }

  public static ProcessInstanceMigrationRecordStream processInstanceMigrationRecords(
      final ProcessInstanceMigrationIntent intent) {
    return processInstanceMigrationRecords().withIntent(intent);
  }

  public static ProcessInstanceResultRecordStream processInstanceResultRecords() {
    return new ProcessInstanceResultRecordStream(
        records(ValueType.PROCESS_INSTANCE_RESULT, ProcessInstanceResultRecordValue.class));
  }

  public static DecisionRecordStream decisionRecords() {
    return new DecisionRecordStream(records(ValueType.DECISION, DecisionRecordValue.class));
  }

  public static DecisionRequirementsRecordStream decisionRequirementsRecords() {
    return new DecisionRequirementsRecordStream(
        records(ValueType.DECISION_REQUIREMENTS, DecisionRequirementsRecordValue.class));
  }

  public static DecisionEvaluationRecordStream decisionEvaluationRecords() {
    return new DecisionEvaluationRecordStream(
        records(ValueType.DECISION_EVALUATION, DecisionEvaluationRecordValue.class));
  }

  public static DecisionEvaluationRecordStream decisionEvaluationRecords(
      final DecisionEvaluationIntent intent) {
    return decisionEvaluationRecords().withIntent(intent);
  }

  public static SignalSubscriptionRecordStream signalSubscriptionRecords() {
    return new SignalSubscriptionRecordStream(
        records(ValueType.SIGNAL_SUBSCRIPTION, SignalSubscriptionRecordValue.class));
  }

  public static SignalSubscriptionRecordStream signalSubscriptionRecords(
      final SignalSubscriptionIntent intent) {
    return signalSubscriptionRecords().withIntent(intent);
  }

  public static SignalRecordStream signalRecords() {
    return new SignalRecordStream(records(ValueType.SIGNAL, SignalRecordValue.class));
  }

  public static SignalRecordStream signalRecords(final SignalIntent intent) {
    return signalRecords().withIntent(intent);
  }

  public static ResourceDeletionRecordStream resourceDeletionRecords() {
    return new ResourceDeletionRecordStream(
        records(ValueType.RESOURCE_DELETION, ResourceDeletionRecordValue.class));
  }

  public static ResourceDeletionRecordStream resourceDeletionRecords(
      final ResourceDeletionIntent intent) {
    return resourceDeletionRecords().withIntent(intent);
  }

  public static AdHocSubProcessInstructionRecordStream adHocSubProcessInstructionRecords() {
    return new AdHocSubProcessInstructionRecordStream(
        records(
            ValueType.AD_HOC_SUB_PROCESS_INSTRUCTION, AdHocSubProcessInstructionRecordValue.class));
  }

  public static FormRecordStream formRecords() {
    return new FormRecordStream(records(ValueType.FORM, Form.class));
  }

  public static ResourceRecordStream resourceRecords() {
    return new ResourceRecordStream(records(ValueType.RESOURCE, Resource.class));
  }

  public static ErrorRecordStream errorRecords() {
    return new ErrorRecordStream(records(ValueType.ERROR, ErrorRecordValue.class));
  }

  public static UserTaskRecordStream userTaskRecords() {
    return new UserTaskRecordStream(records(ValueType.USER_TASK, UserTaskRecordValue.class));
  }

  public static UserTaskRecordStream userTaskRecords(final UserTaskIntent intent) {
    return userTaskRecords().withIntent(intent);
  }

  public static CompensationSubscriptionRecordStream compensationSubscriptionRecords() {
    return new CompensationSubscriptionRecordStream(
        records(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionRecordValue.class));
  }

  public static UserRecordStream userRecords() {
    return new UserRecordStream(records(ValueType.USER, UserRecordValue.class));
  }

  public static UserRecordStream userRecords(final UserIntent intent) {
    return userRecords().withIntent(intent);
  }

  public static ClockRecordStream clockRecords() {
    return new ClockRecordStream(records(ValueType.CLOCK, ClockRecordValue.class));
  }

  public static ClockRecordStream clockRecords(final ClockIntent intent) {
    return clockRecords().withIntent(intent);
  }

  public static AuthorizationRecordStream authorizationRecords() {
    return new AuthorizationRecordStream(
        records(ValueType.AUTHORIZATION, AuthorizationRecordValue.class));
  }

  public static AuthorizationRecordStream authorizationRecords(final AuthorizationIntent intent) {
    return authorizationRecords().withIntent(intent);
  }

  public static RoleRecordStream roleRecords() {
    return new RoleRecordStream(records(ValueType.ROLE, RoleRecordValue.class));
  }

  public static RoleRecordStream roleRecords(final RoleIntent intent) {
    return roleRecords().withIntent(intent);
  }

  public static ScaleRecordStream scaleRecords() {
    return new ScaleRecordStream(records(ValueType.SCALE, ScaleRecordValue.class));
  }

  public static ScaleRecordStream scaleRecords(final ScaleIntent intent) {
    return scaleRecords().withIntent(intent);
  }

  public static TenantRecordStream tenantRecords() {
    return new TenantRecordStream(records(ValueType.TENANT, TenantRecordValue.class));
  }

  public static TenantRecordStream tenantRecords(final TenantIntent intent) {
    return tenantRecords().withIntent(intent);
  }

  public static MappingRuleRecordStream mappingRuleRecords() {
    return new MappingRuleRecordStream(
        records(ValueType.MAPPING_RULE, MappingRuleRecordValue.class));
  }

  public static MappingRuleRecordStream mappingRuleRecords(final MappingRuleIntent intent) {
    return mappingRuleRecords().withIntent(intent);
  }

  public static GroupRecordStream groupRecords() {
    return new GroupRecordStream(records(ValueType.GROUP, GroupRecordValue.class));
  }

  public static GroupRecordStream groupRecords(final GroupIntent intent) {
    return groupRecords().withIntent(intent);
  }

  public static IdentitySetupRecordStream identitySetupRecords() {
    return new IdentitySetupRecordStream(
        records(ValueType.IDENTITY_SETUP, IdentitySetupRecordValue.class));
  }

  public static IdentitySetupRecordStream identitySetupRecords(final IdentitySetupIntent intent) {
    return identitySetupRecords().withIntent(intent);
  }

  public static BatchOperationCreationRecordStream batchOperationCreationRecords() {
    return new BatchOperationCreationRecordStream(
        records(ValueType.BATCH_OPERATION_CREATION, BatchOperationCreationRecordValue.class));
  }

  public static BatchOperationCreationRecordStream batchOperationCreationRecords(
      final BatchOperationIntent intent) {
    return batchOperationCreationRecords().withIntent(intent);
  }

  public static BatchOperationChunkRecordStream batchOperationChunkRecords() {
    return new BatchOperationChunkRecordStream(
        records(ValueType.BATCH_OPERATION_CHUNK, BatchOperationChunkRecordValue.class));
  }

  public static BatchOperationChunkRecordStream batchOperationChunkRecords(
      final BatchOperationChunkIntent intent) {
    return batchOperationChunkRecords().withIntent(intent);
  }

  public static BatchOperationExecutionRecordStream batchOperationExecutionRecords() {
    return new BatchOperationExecutionRecordStream(
        records(ValueType.BATCH_OPERATION_EXECUTION, BatchOperationExecutionRecordValue.class));
  }

  public static BatchOperationExecutionRecordStream batchOperationExecutionRecords(
      final BatchOperationIntent intent) {
    return batchOperationExecutionRecords().withIntent(intent);
  }

  public static BatchOperationInitializationRecordStream batchOperationInitializationRecords() {
    return new BatchOperationInitializationRecordStream(
        records(
            ValueType.BATCH_OPERATION_INITIALIZATION,
            BatchOperationInitializationRecordValue.class));
  }

  public static BatchOperationInitializationRecordStream batchOperationInitializationRecords(
      final BatchOperationIntent intent) {
    return batchOperationInitializationRecords().withIntent(intent);
  }

  public static BatchOperationLifecycleRecordStream batchOperationLifecycleRecords() {
    return new BatchOperationLifecycleRecordStream(
        records(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
            BatchOperationLifecycleManagementRecordValue.class));
  }

  public static BatchOperationLifecycleRecordStream batchOperationLifecycleRecords(
      final BatchOperationIntent intent) {
    return batchOperationLifecycleRecords().withIntent(intent);
  }

  public static BatchOperationPartitionLifecycleRecordStream
      batchOperationPartitionLifecycleRecords() {
    return new BatchOperationPartitionLifecycleRecordStream(
        records(
            ValueType.BATCH_OPERATION_PARTITION_LIFECYCLE,
            BatchOperationPartitionLifecycleRecordValue.class));
  }

  public static AsyncRequestRecordStream asyncRequestRecords() {
    return new AsyncRequestRecordStream(
        records(ValueType.ASYNC_REQUEST, AsyncRequestRecordValue.class));
  }

  public static BatchOperationPartitionLifecycleRecordStream
      batchOperationPartitionLifecycleRecords(final BatchOperationIntent intent) {
    return batchOperationPartitionLifecycleRecords().withIntent(intent);
  }

  public static void autoAcknowledge(final boolean shouldAcknowledgeRecords) {
    autoAcknowledge = shouldAcknowledgeRecords;
  }

  public static class AwaitingRecordIterator implements Iterator<Record<?>> {

    private int nextIndex = 0;

    private boolean isEmpty() {
      return nextIndex >= RECORDS.size();
    }

    @Override
    public boolean hasNext() {
      LOCK.lock();
      try {
        long now = System.currentTimeMillis();
        final long endTime = now + maximumWaitTime;
        while (isEmpty() && endTime > now) {
          final long waitTime = endTime - now;
          try {
            IS_EMPTY.await(waitTime, TimeUnit.MILLISECONDS);
          } catch (final InterruptedException ignored) {
            // ignored
          }
          now = System.currentTimeMillis();
        }
        return !isEmpty();
      } finally {
        LOCK.unlock();
      }
    }

    @Override
    public Record<?> next() {
      return RECORDS.get(nextIndex++);
    }
  }
}
