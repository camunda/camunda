/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.AsyncRequestRecord;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import io.camunda.zeebe.protocol.impl.record.value.agentdefinition.AgentDefinitionRecord;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.impl.record.value.agenthistorybatch.AgentHistoryBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
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
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceReexportRecord;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.impl.record.value.escalation.EscalationRecord;
import io.camunda.zeebe.protocol.impl.record.value.expression.ExpressionRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.history.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.jobmetrics.JobMetricsBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartCorrelationKeyLockReleaseRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.metrics.UsageMetricRecord;
import io.camunda.zeebe.protocol.impl.record.value.multiinstance.MultiInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessEventRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBusinessIdRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.RuntimeInstructionRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
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
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UnifiedRecordValue extends UnpackedObject implements RecordValue {

  // A factory per value type rather than one method constructing every type: the JIT inlines each
  // constructor a switch reaches, which made that one method a very large compilation.
  private static final List<Supplier<UnifiedRecordValue>> FACTORIES =
      Arrays.stream(ValueType.values()).map(UnifiedRecordValue::factory).toList();

  /**
   * Creates a new {@link UnifiedRecordValue}.
   *
   * @param expectedDeclaredProperties the expected number of declared properties. Providing the
   *     correct number helps to avoid allocations and memory copies.
   */
  public UnifiedRecordValue(final int expectedDeclaredProperties) {
    super(expectedDeclaredProperties);
  }

  @Override
  @JsonIgnore
  public int getLength() {
    return super.getLength();
  }

  @Override
  @JsonIgnore
  public int getEncodedLength() {
    return super.getEncodedLength();
  }

  @Override
  @JsonIgnore
  public boolean isEmpty() {
    return super.isEmpty();
  }

  @Override
  public String toJson() {
    return MsgPackConverter.convertJsonSerializableObjectToJson(this);
  }

  /**
   * NOTE: this is different from {@link CommandDistributionRecord#getValueType()} or {@link
   * AsyncRequestRecord#getValueType()} as that is referring to the value they are wrapping
   *
   * <p>It needs to be a different method, otherwise those methods will be ignored in the json.
   *
   * @return the valueType associated to this record
   */
  @JsonIgnore
  public ValueType valueType() {
    return ClassToValueType.MAP.get(getClass());
  }

  public static Stream<UnifiedRecordValue> allRecords() {
    return Arrays.stream(ValueType.values())
        .map(UnifiedRecordValue::fromValueType)
        .filter(Objects::nonNull);
  }

  public static EnumMap<ValueType, UnifiedRecordValue> allRecordsMap() {
    return new EnumMap<>(
        UnifiedRecordValue.allRecords()
            .collect(Collectors.toMap(UnifiedRecordValue::valueType, Function.identity())));
  }

  public static UnifiedRecordValue fromValueType(final ValueType valueType) {
    final var factory = FACTORIES.get(valueType.ordinal());
    return factory == null ? null : factory.get();
  }

  private static Supplier<UnifiedRecordValue> factory(final ValueType valueType) {
    return switch (valueType) {
      case ValueType.DEPLOYMENT -> DeploymentRecord::new;
      case ValueType.JOB -> JobRecord::new;
      case ValueType.PROCESS_INSTANCE -> ProcessInstanceRecord::new;
      case ValueType.MESSAGE -> MessageRecord::new;
      case ValueType.MESSAGE_BATCH -> MessageBatchRecord::new;
      case ValueType.PROCESS_MESSAGE_SUBSCRIPTION -> ProcessMessageSubscriptionRecord::new;
      case ValueType.JOB_BATCH -> JobBatchRecord::new;
      case ValueType.INCIDENT -> IncidentRecord::new;
      case ValueType.TIMER -> TimerRecord::new;
      case ValueType.MESSAGE_START_EVENT_SUBSCRIPTION -> MessageStartEventSubscriptionRecord::new;
      case ValueType.MESSAGE_START_PROCESS_INSTANCE_REQUEST ->
          MessageStartProcessInstanceRequestRecord::new;
      case ValueType.MESSAGE_START_CORRELATION_KEY_LOCK_RELEASE ->
          MessageStartCorrelationKeyLockReleaseRecord::new;
      case ValueType.VARIABLE -> VariableRecord::new;
      case ValueType.VARIABLE_DOCUMENT -> VariableDocumentRecord::new;
      case ValueType.CLUSTER_VARIABLE -> ClusterVariableRecord::new;
      case ValueType.PROCESS_INSTANCE_CREATION -> ProcessInstanceCreationRecord::new;
      case ValueType.ERROR -> ErrorRecord::new;
      case ValueType.PROCESS_INSTANCE_RESULT -> ProcessInstanceResultRecord::new;
      case ValueType.PROCESS -> ProcessRecord::new;
      case ValueType.DEPLOYMENT_DISTRIBUTION -> DeploymentDistributionRecord::new;
      case ValueType.PROCESS_EVENT -> ProcessEventRecord::new;
      case ValueType.DECISION -> DecisionRecord::new;
      case ValueType.DECISION_REQUIREMENTS -> DecisionRequirementsRecord::new;
      case ValueType.DECISION_EVALUATION -> DecisionEvaluationRecord::new;
      case ValueType.PROCESS_INSTANCE_MODIFICATION -> ProcessInstanceModificationRecord::new;
      case ValueType.ESCALATION -> EscalationRecord::new;
      case ValueType.SIGNAL_SUBSCRIPTION -> SignalSubscriptionRecord::new;
      case ValueType.SIGNAL -> SignalRecord::new;
      case ValueType.COMMAND_DISTRIBUTION -> CommandDistributionRecord::new;
      case ValueType.PROCESS_INSTANCE_BATCH -> ProcessInstanceBatchRecord::new;
      case ValueType.BUFFERED_COMMAND -> BufferedCommandRecord::new;
      case ValueType.PROCESS_INSTANCE_BUSINESS_ID -> ProcessInstanceBusinessIdRecord::new;
      case ValueType.RESOURCE_DELETION -> ResourceDeletionRecord::new;
      case ValueType.FORM -> FormRecord::new;
      case ValueType.USER_TASK -> UserTaskRecord::new;
      case ValueType.PROCESS_INSTANCE_MIGRATION -> ProcessInstanceMigrationRecord::new;
      case ValueType.BATCH_OPERATION_EXECUTION -> BatchOperationExecutionRecord::new;
      case ValueType.BATCH_OPERATION_CHUNK -> BatchOperationChunkRecord::new;
      case ValueType.AD_HOC_SUB_PROCESS_INSTRUCTION -> AdHocSubProcessInstructionRecord::new;
      case ValueType.COMPENSATION_SUBSCRIPTION -> CompensationSubscriptionRecord::new;
      case ValueType.MESSAGE_CORRELATION -> MessageCorrelationRecord::new;
      case ValueType.USER -> UserRecord::new;
      case ValueType.CLOCK -> ClockRecord::new;
      case ValueType.AUTHORIZATION -> AuthorizationRecord::new;
      case ValueType.ROLE -> RoleRecord::new;
      case ValueType.TENANT -> TenantRecord::new;
      case ValueType.RESOURCE_REEXPORT -> ResourceReexportRecord::new;
      case ValueType.SCALE -> ScaleRecord::new;
      case ValueType.GROUP -> GroupRecord::new;
      case ValueType.MAPPING_RULE -> MappingRuleRecord::new;
      case ValueType.IDENTITY_SETUP -> IdentitySetupRecord::new;
      case ValueType.RESOURCE -> ResourceRecord::new;
      case ValueType.BATCH_OPERATION_CREATION -> BatchOperationCreationRecord::new;
      case ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT ->
          BatchOperationLifecycleManagementRecord::new;
      case ValueType.BATCH_OPERATION_PARTITION_LIFECYCLE ->
          BatchOperationPartitionLifecycleRecord::new;
      case ValueType.ASYNC_REQUEST -> AsyncRequestRecord::new;
      case ValueType.USAGE_METRIC -> UsageMetricRecord::new;
      case ValueType.HISTORY_DELETION -> HistoryDeletionRecord::new;
      case ValueType.CONDITIONAL_SUBSCRIPTION -> ConditionalSubscriptionRecord::new;
      case ValueType.CONDITIONAL_EVALUATION -> ConditionalEvaluationRecord::new;
      case ValueType.EXPRESSION -> ExpressionRecord::new;
      case ValueType.MULTI_INSTANCE -> MultiInstanceRecord::new;
      case ValueType.RUNTIME_INSTRUCTION -> RuntimeInstructionRecord::new;
      case ValueType.BATCH_OPERATION_INITIALIZATION -> BatchOperationInitializationRecord::new;
      case ValueType.CHECKPOINT -> CheckpointRecord::new;
      case ValueType.MESSAGE_SUBSCRIPTION -> MessageSubscriptionRecord::new;
      case ValueType.GLOBAL_LISTENER_BATCH -> GlobalListenerBatchRecord::new;
      case ValueType.JOB_METRICS_BATCH -> JobMetricsBatchRecord::new;
      case ValueType.GLOBAL_LISTENER -> GlobalListenerRecord::new;
      case ValueType.AGENT_HISTORY -> AgentHistoryRecord::new;
      case ValueType.AGENT_INSTANCE -> AgentInstanceRecord::new;
      case ValueType.AGENT_DEFINITION -> AgentDefinitionRecord::new;
      case ValueType.AGENT_HISTORY_BATCH -> AgentHistoryBatchRecord::new;
      case ValueType.SECRET_REFERENCE -> SecretReferenceRecord::new;
      case ValueType.SBE_UNKNOWN -> null;
      case ValueType.NULL_VAL -> null;
    };
  }

  /**
   * Because of how java static initializers works, it need to be in a separate class: /* inside the
   * static{} block we use {@link UnifiedRecordValue#fromValueType} from the outer class
   */
  private static final class ClassToValueType {
    private static final Map<Class<? extends RecordValue>, ValueType> MAP = new HashMap<>();

    static {
      Arrays.stream(ValueType.values())
          .forEach(
              v -> {
                final var record = fromValueType(v);
                if (record != null) {
                  MAP.put(record.getClass(), v);
                }
              });
    }
  }
}
