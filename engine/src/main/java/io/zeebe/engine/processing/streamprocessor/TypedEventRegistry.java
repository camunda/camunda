/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor;

import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentDistributionRecord;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessEventRecord;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.zeebe.protocol.record.ValueType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class TypedEventRegistry {

  public static final Map<ValueType, Class<? extends UnifiedRecordValue>> EVENT_REGISTRY;

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
    registry.put(ValueType.ERROR, ErrorRecord.class);
    registry.put(ValueType.PROCESS_INSTANCE_RESULT, ProcessInstanceResultRecord.class);
    registry.put(ValueType.PROCESS, ProcessRecord.class);
    registry.put(ValueType.DEPLOYMENT_DISTRIBUTION, DeploymentDistributionRecord.class);
    registry.put(ValueType.PROCESS_EVENT, ProcessEventRecord.class);

    EVENT_REGISTRY = Collections.unmodifiableMap(registry);
  }
}
