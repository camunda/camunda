/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.protocol.record;

import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.CompensationSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.protocol.record.intent.EscalationIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.MessageBatchIntent;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MultiInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessEventIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceResultIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.RuntimeInstructionIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
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
import io.camunda.zeebe.protocol.record.value.MultiInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessEventRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceResultRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ResourceDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.RuntimeInstructionRecordValue;
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
import io.camunda.zeebe.protocol.record.value.management.CheckpointRecordValue;
import io.camunda.zeebe.protocol.record.value.scaling.ScaleRecordValue;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Provides a mapping of all {@link ValueType} to their concrete implementations. It should be an
 * exhaustive map of all possible {@link ValueType}, so if you add one, make sure to update the
 * mapping here as well.
 */
@SuppressWarnings("java:S1452")
public final class ValueTypeMapping {
  private final Map<ValueType, Mapping<?, ?>> types;
  private final Set<ValueType> acceptedValueTypes;

  private ValueTypeMapping() {
    types = Collections.unmodifiableMap(loadValueTypes());
    acceptedValueTypes =
        EnumSet.complementOf(EnumSet.of(ValueType.SBE_UNKNOWN, ValueType.NULL_VAL));
  }

  /**
   * Returns the mapping for the given value type.
   *
   * @param valueType the value type of the mapping
   * @return a mapping from this type to a {@link RecordValue} and {@link Intent}
   * @throws IllegalArgumentException if no such mapping exists
   */
  public static Mapping<?, ?> get(final ValueType valueType) {
    final Mapping<?, ?> typeInfo = Singleton.INSTANCE.types.get(valueType);
    if (typeInfo == null) {
      throw new IllegalArgumentException(
          String.format(
              "Expected value type to be one of %s, but was %s",
              Singleton.INSTANCE.types.keySet(), valueType));
    }

    return typeInfo;
  }

  /**
   * @return the set of mappable value types
   */
  public static Set<ValueType> getAcceptedValueTypes() {
    return Singleton.INSTANCE.acceptedValueTypes;
  }

  // suppressed warning about method length; this is simply populating a map, which while tedious,
  // isn't incredibly complex
  @SuppressWarnings("java:S138")
  private Map<ValueType, Mapping<?, ?>> loadValueTypes() {
    final Map<ValueType, Mapping<?, ?>> mapping = new EnumMap<>(ValueType.class);

    mapping.put(ValueType.DECISION, new Mapping<>(DecisionRecordValue.class, DecisionIntent.class));
    mapping.put(
        ValueType.DECISION_EVALUATION,
        new Mapping<>(DecisionEvaluationRecordValue.class, DecisionEvaluationIntent.class));
    mapping.put(
        ValueType.DECISION_REQUIREMENTS,
        new Mapping<>(DecisionRequirementsRecordValue.class, DecisionRequirementsIntent.class));
    mapping.put(
        ValueType.DEPLOYMENT, new Mapping<>(DeploymentRecordValue.class, DeploymentIntent.class));
    mapping.put(
        ValueType.DEPLOYMENT_DISTRIBUTION,
        new Mapping<>(DeploymentDistributionRecordValue.class, DeploymentDistributionIntent.class));
    mapping.put(ValueType.ERROR, new Mapping<>(ErrorRecordValue.class, ErrorIntent.class));
    mapping.put(ValueType.INCIDENT, new Mapping<>(IncidentRecordValue.class, IncidentIntent.class));
    mapping.put(ValueType.JOB, new Mapping<>(JobRecordValue.class, JobIntent.class));
    mapping.put(
        ValueType.JOB_BATCH, new Mapping<>(JobBatchRecordValue.class, JobBatchIntent.class));
    mapping.put(ValueType.MESSAGE, new Mapping<>(MessageRecordValue.class, MessageIntent.class));
    mapping.put(
        ValueType.MESSAGE_BATCH,
        new Mapping<>(MessageBatchRecordValue.class, MessageBatchIntent.class));
    mapping.put(
        ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
        new Mapping<>(
            MessageStartEventSubscriptionRecordValue.class,
            MessageStartEventSubscriptionIntent.class));
    mapping.put(
        ValueType.MESSAGE_SUBSCRIPTION,
        new Mapping<>(MessageSubscriptionRecordValue.class, MessageSubscriptionIntent.class));
    mapping.put(ValueType.PROCESS, new Mapping<>(Process.class, ProcessIntent.class));
    mapping.put(
        ValueType.PROCESS_EVENT,
        new Mapping<>(ProcessEventRecordValue.class, ProcessEventIntent.class));
    mapping.put(
        ValueType.PROCESS_INSTANCE,
        new Mapping<>(ProcessInstanceRecordValue.class, ProcessInstanceIntent.class));
    mapping.put(
        ValueType.PROCESS_INSTANCE_CREATION,
        new Mapping<>(
            ProcessInstanceCreationRecordValue.class, ProcessInstanceCreationIntent.class));
    mapping.put(
        ValueType.PROCESS_INSTANCE_MODIFICATION,
        new Mapping<>(
            ProcessInstanceModificationRecordValue.class, ProcessInstanceModificationIntent.class));
    mapping.put(
        ValueType.PROCESS_INSTANCE_RESULT,
        new Mapping<>(ProcessInstanceResultRecordValue.class, ProcessInstanceResultIntent.class));
    mapping.put(
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        new Mapping<>(
            ProcessMessageSubscriptionRecordValue.class, ProcessMessageSubscriptionIntent.class));
    mapping.put(ValueType.TIMER, new Mapping<>(TimerRecordValue.class, TimerIntent.class));
    mapping.put(ValueType.VARIABLE, new Mapping<>(VariableRecordValue.class, VariableIntent.class));
    mapping.put(
        ValueType.VARIABLE_DOCUMENT,
        new Mapping<>(VariableDocumentRecordValue.class, VariableDocumentIntent.class));
    mapping.put(
        ValueType.CHECKPOINT, new Mapping<>(CheckpointRecordValue.class, CheckpointIntent.class));
    mapping.put(
        ValueType.ESCALATION, new Mapping<>(EscalationRecordValue.class, EscalationIntent.class));
    mapping.put(ValueType.SIGNAL, new Mapping<>(SignalRecordValue.class, SignalIntent.class));
    mapping.put(
        ValueType.SIGNAL_SUBSCRIPTION,
        new Mapping<>(SignalSubscriptionRecordValue.class, SignalSubscriptionIntent.class));
    mapping.put(
        ValueType.RESOURCE_DELETION,
        new Mapping<>(ResourceDeletionRecordValue.class, ResourceDeletionIntent.class));
    mapping.put(
        ValueType.COMMAND_DISTRIBUTION,
        new Mapping<>(CommandDistributionRecordValue.class, CommandDistributionIntent.class));
    mapping.put(
        ValueType.PROCESS_INSTANCE_BATCH,
        new Mapping<>(ProcessInstanceBatchRecordValue.class, ProcessInstanceBatchIntent.class));
    mapping.put(
        ValueType.AD_HOC_SUB_PROCESS_INSTRUCTION,
        new Mapping<>(
            AdHocSubProcessInstructionRecordValue.class, AdHocSubProcessInstructionIntent.class));
    mapping.put(ValueType.FORM, new Mapping<>(Form.class, FormIntent.class));
    mapping.put(ValueType.RESOURCE, new Mapping<>(Resource.class, ResourceIntent.class));
    mapping.put(
        ValueType.USER_TASK, new Mapping<>(UserTaskRecordValue.class, UserTaskIntent.class));
    mapping.put(
        ValueType.PROCESS_INSTANCE_MIGRATION,
        new Mapping<>(
            ProcessInstanceMigrationRecordValue.class, ProcessInstanceMigrationIntent.class));
    mapping.put(
        ValueType.COMPENSATION_SUBSCRIPTION,
        new Mapping<>(
            CompensationSubscriptionRecordValue.class, CompensationSubscriptionIntent.class));
    mapping.put(
        ValueType.MESSAGE_CORRELATION,
        new Mapping<>(MessageCorrelationRecordValue.class, MessageCorrelationIntent.class));
    mapping.put(ValueType.USER, new Mapping<>(UserRecordValue.class, UserIntent.class));
    mapping.put(ValueType.CLOCK, new Mapping<>(ClockRecordValue.class, ClockIntent.class));
    mapping.put(
        ValueType.AUTHORIZATION,
        new Mapping<>(AuthorizationRecordValue.class, AuthorizationIntent.class));
    mapping.put(ValueType.ROLE, new Mapping<>(RoleRecordValue.class, RoleIntent.class));
    mapping.put(ValueType.TENANT, new Mapping<>(TenantRecordValue.class, TenantIntent.class));
    mapping.put(ValueType.SCALE, new Mapping<>(ScaleRecordValue.class, ScaleIntent.class));
    mapping.put(ValueType.GROUP, new Mapping<>(GroupRecordValue.class, GroupIntent.class));
    mapping.put(
        ValueType.MAPPING_RULE,
        new Mapping<>(MappingRuleRecordValue.class, MappingRuleIntent.class));
    mapping.put(
        ValueType.IDENTITY_SETUP,
        new Mapping<>(IdentitySetupRecordValue.class, IdentitySetupIntent.class));
    mapping.put(
        ValueType.BATCH_OPERATION_CREATION,
        new Mapping<>(BatchOperationCreationRecordValue.class, BatchOperationIntent.class));
    mapping.put(
        ValueType.BATCH_OPERATION_CHUNK,
        new Mapping<>(BatchOperationChunkRecordValue.class, BatchOperationChunkIntent.class));
    mapping.put(
        ValueType.BATCH_OPERATION_EXECUTION,
        new Mapping<>(
            BatchOperationExecutionRecordValue.class, BatchOperationExecutionIntent.class));
    mapping.put(
        ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
        new Mapping<>(
            BatchOperationLifecycleManagementRecordValue.class, BatchOperationIntent.class));
    mapping.put(
        ValueType.BATCH_OPERATION_PARTITION_LIFECYCLE,
        new Mapping<>(
            BatchOperationPartitionLifecycleRecordValue.class, BatchOperationIntent.class));
    mapping.put(
        ValueType.BATCH_OPERATION_INITIALIZATION,
        new Mapping<>(BatchOperationInitializationRecordValue.class, BatchOperationIntent.class));
    mapping.put(
        ValueType.ASYNC_REQUEST,
        new Mapping<>(AsyncRequestRecordValue.class, AsyncRequestIntent.class));
    mapping.put(
        ValueType.USAGE_METRIC,
        new Mapping<>(UsageMetricRecordValue.class, UsageMetricIntent.class));
    mapping.put(
        ValueType.MULTI_INSTANCE,
        new Mapping<>(MultiInstanceRecordValue.class, MultiInstanceIntent.class));
    mapping.put(
        ValueType.RUNTIME_INSTRUCTION,
        new Mapping<>(RuntimeInstructionRecordValue.class, RuntimeInstructionIntent.class));
    return mapping;
  }

  /**
   * A mapping between value type, an abstract protocol type extending {@link RecordValue}, and the
   * equivalent intent.
   *
   * @param <T> the value type
   * @param <I> the intent type
   */
  public static final class Mapping<T extends RecordValue, I extends Enum<I> & Intent> {
    private final Class<T> valueClass;
    private final Class<I> intentClass;

    private Mapping(final Class<T> valueClass, final Class<I> intentClass) {
      this.valueClass = Objects.requireNonNull(valueClass, "must specify a value class");
      this.intentClass = Objects.requireNonNull(intentClass, "must specify an intent");
    }

    public Class<? extends T> getValueClass() {
      return valueClass;
    }

    public Class<I> getIntentClass() {
      return intentClass;
    }
  }

  private static final class Singleton {
    private static final ValueTypeMapping INSTANCE = new ValueTypeMapping();
  }
}
