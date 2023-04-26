/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessEventRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
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
    registry.put(ValueType.ERROR, ErrorRecord.class);
    registry.put(ValueType.PROCESS_INSTANCE_RESULT, ProcessInstanceResultRecord.class);
    registry.put(ValueType.PROCESS, ProcessRecord.class);
    registry.put(ValueType.DEPLOYMENT_DISTRIBUTION, DeploymentDistributionRecord.class);
    registry.put(ValueType.PROCESS_EVENT, ProcessEventRecord.class);
    registry.put(ValueType.DECISION, DecisionRecord.class);
    registry.put(ValueType.DECISION_REQUIREMENTS, DecisionRequirementsRecord.class);
    registry.put(ValueType.DECISION_EVALUATION, DecisionEvaluationRecord.class);
    registry.put(ValueType.PROCESS_INSTANCE_BATCH, ProcessInstanceBatchRecord.class);

    registry.put(ValueType.CHECKPOINT, CheckpointRecord.class);

    EVENT_REGISTRY = Collections.unmodifiableMap(registry);

    final Map<Class<? extends UnifiedRecordValue>, ValueType> typeRegistry = new HashMap<>();
    EVENT_REGISTRY.forEach((e, c) -> typeRegistry.put(c, e));
    TYPE_REGISTRY = Collections.unmodifiableMap(typeRegistry);
  }

  private TypedEventRegistry() {}
}
