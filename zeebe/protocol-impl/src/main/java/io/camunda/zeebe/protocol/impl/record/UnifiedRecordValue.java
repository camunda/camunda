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
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
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
import io.camunda.zeebe.protocol.impl.record.value.multiinstance.MultiInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessEventRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class UnifiedRecordValue extends UnpackedObject implements RecordValue {

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
   * NOTE: this is different from {@link CommandDistributionRecord#getValueType()} as that is
   * referring to the value they are wrapping.
   *
   * <p>It needs to be a different method, otherwise those methods will be ignored in the json.
   *
   * @return the valueType associated to this record
   */
  @JsonIgnore
  public ValueType valueType() {
    return ClassToValueType.MAP.get(getClass());
  }

  public static UnifiedRecordValue fromValueType(final ValueType valueType) {
    return switch (valueType) {
      case ValueType.DEPLOYMENT -> new DeploymentRecord();
      case ValueType.JOB -> new JobRecord();
      case ValueType.PROCESS_INSTANCE -> new ProcessInstanceRecord();
      case ValueType.MESSAGE -> new MessageRecord();
      case ValueType.MESSAGE_BATCH -> new MessageBatchRecord();
      case ValueType.PROCESS_MESSAGE_SUBSCRIPTION -> new ProcessMessageSubscriptionRecord();
      case ValueType.JOB_BATCH -> new JobBatchRecord();
      case ValueType.INCIDENT -> new IncidentRecord();
      case ValueType.TIMER -> new TimerRecord();
      case ValueType.MESSAGE_START_EVENT_SUBSCRIPTION -> new MessageStartEventSubscriptionRecord();
      case ValueType.VARIABLE -> new VariableRecord();
      case ValueType.VARIABLE_DOCUMENT -> new VariableDocumentRecord();
      case ValueType.PROCESS_INSTANCE_CREATION -> new ProcessInstanceCreationRecord();
      case ValueType.ERROR -> new ErrorRecord();
      case ValueType.PROCESS_INSTANCE_RESULT -> new ProcessInstanceResultRecord();
      case ValueType.PROCESS -> new ProcessRecord();
      case ValueType.DEPLOYMENT_DISTRIBUTION -> new DeploymentDistributionRecord();
      case ValueType.PROCESS_EVENT -> new ProcessEventRecord();
      case ValueType.DECISION -> new DecisionRecord();
      case ValueType.DECISION_REQUIREMENTS -> new DecisionRequirementsRecord();
      case ValueType.DECISION_EVALUATION -> new DecisionEvaluationRecord();
      case ValueType.PROCESS_INSTANCE_MODIFICATION -> new ProcessInstanceModificationRecord();
      case ValueType.ESCALATION -> new EscalationRecord();
      case ValueType.SIGNAL_SUBSCRIPTION -> new SignalSubscriptionRecord();
      case ValueType.SIGNAL -> new SignalRecord();
      case ValueType.COMMAND_DISTRIBUTION -> new CommandDistributionRecord();
      case ValueType.PROCESS_INSTANCE_BATCH -> new ProcessInstanceBatchRecord();
      case ValueType.RESOURCE_DELETION -> new ResourceDeletionRecord();
      case ValueType.FORM -> new FormRecord();
      case ValueType.USER_TASK -> new UserTaskRecord();
      case ValueType.PROCESS_INSTANCE_MIGRATION -> new ProcessInstanceMigrationRecord();
      case ValueType.COMPENSATION_SUBSCRIPTION -> new CompensationSubscriptionRecord();
      case ValueType.MESSAGE_CORRELATION -> new MessageCorrelationRecord();
      case ValueType.USER -> new UserRecord();
      case ValueType.CLOCK -> new ClockRecord();
      case ValueType.AUTHORIZATION -> new AuthorizationRecord();
      case ValueType.RESOURCE -> new ResourceRecord();
      case ValueType.MULTI_INSTANCE -> new MultiInstanceRecord();
      case ValueType.CHECKPOINT -> new CheckpointRecord();
      case ValueType.MESSAGE_SUBSCRIPTION -> new MessageSubscriptionRecord();
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
