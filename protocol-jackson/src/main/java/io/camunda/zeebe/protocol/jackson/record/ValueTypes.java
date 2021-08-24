/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson.record;

import io.camunda.zeebe.protocol.jackson.record.DeploymentDistributionRecordValueBuilder.ImmutableDeploymentDistributionRecordValue;
import io.camunda.zeebe.protocol.jackson.record.DeploymentRecordValueBuilder.ImmutableDeploymentRecordValue;
import io.camunda.zeebe.protocol.jackson.record.ErrorRecordValueBuilder.ImmutableErrorRecordValue;
import io.camunda.zeebe.protocol.jackson.record.IncidentRecordValueBuilder.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.jackson.record.JobBatchRecordValueBuilder.ImmutableJobBatchRecordValue;
import io.camunda.zeebe.protocol.jackson.record.JobRecordValueBuilder.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.jackson.record.MessageRecordValueBuilder.ImmutableMessageRecordValue;
import io.camunda.zeebe.protocol.jackson.record.MessageStartEventSubscriptionRecordValueBuilder.ImmutableMessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.jackson.record.MessageSubscriptionRecordValueBuilder.ImmutableMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.jackson.record.ProcessBuilder.ImmutableProcess;
import io.camunda.zeebe.protocol.jackson.record.ProcessEventRecordValueBuilder.ImmutableProcessEventRecordValue;
import io.camunda.zeebe.protocol.jackson.record.ProcessInstanceCreationRecordValueBuilder.ImmutableProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.jackson.record.ProcessInstanceRecordValueBuilder.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.jackson.record.ProcessInstanceResultRecordValueBuilder.ImmutableProcessInstanceResultRecordValue;
import io.camunda.zeebe.protocol.jackson.record.ProcessMessageSubscriptionRecordValueBuilder.ImmutableProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.jackson.record.TimerRecordValueBuilder.ImmutableTimerRecordValue;
import io.camunda.zeebe.protocol.jackson.record.VariableDocumentRecordValueBuilder.ImmutableVariableDocumentRecordValue;
import io.camunda.zeebe.protocol.jackson.record.VariableRecordValueBuilder.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessEventIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceResultIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import java.util.EnumMap;
import java.util.Map;

/**
 * Provides a mapping of all {@link ValueType} to their concrete implementations. It should be an
 * exhaustive map of all possible {@link ValueType}, so if you add one, make sure to update the
 * mapping here as well.
 */
@SuppressWarnings("java:S1452")
final class ValueTypes {
  private static final Map<ValueType, ValueTypeInfo<?>> TYPES;

  static {
    final Map<ValueType, ValueTypeInfo<?>> mapping = new EnumMap<>(ValueType.class);

    mapping.put(
        ValueType.DEPLOYMENT,
        new ValueTypeInfo<>(ImmutableDeploymentRecordValue.class, DeploymentIntent.class));
    mapping.put(
        ValueType.DEPLOYMENT_DISTRIBUTION,
        new ValueTypeInfo<>(
            ImmutableDeploymentDistributionRecordValue.class, DeploymentDistributionIntent.class));
    mapping.put(
        ValueType.ERROR, new ValueTypeInfo<>(ImmutableErrorRecordValue.class, ErrorIntent.class));
    mapping.put(
        ValueType.INCIDENT,
        new ValueTypeInfo<>(ImmutableIncidentRecordValue.class, IncidentIntent.class));
    mapping.put(ValueType.JOB, new ValueTypeInfo<>(ImmutableJobRecordValue.class, JobIntent.class));
    mapping.put(
        ValueType.JOB_BATCH,
        new ValueTypeInfo<>(ImmutableJobBatchRecordValue.class, JobBatchIntent.class));
    mapping.put(
        ValueType.MESSAGE,
        new ValueTypeInfo<>(ImmutableMessageRecordValue.class, MessageIntent.class));
    mapping.put(
        ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
        new ValueTypeInfo<>(
            ImmutableMessageStartEventSubscriptionRecordValue.class,
            MessageStartEventSubscriptionIntent.class));
    mapping.put(
        ValueType.MESSAGE_SUBSCRIPTION,
        new ValueTypeInfo<>(
            ImmutableMessageSubscriptionRecordValue.class, MessageSubscriptionIntent.class));
    mapping.put(
        ValueType.PROCESS, new ValueTypeInfo<>(ImmutableProcess.class, ProcessIntent.class));
    mapping.put(
        ValueType.PROCESS_EVENT,
        new ValueTypeInfo<>(ImmutableProcessEventRecordValue.class, ProcessEventIntent.class));
    mapping.put(
        ValueType.PROCESS_INSTANCE,
        new ValueTypeInfo<>(
            ImmutableProcessInstanceRecordValue.class, ProcessInstanceIntent.class));
    mapping.put(
        ValueType.PROCESS_INSTANCE_CREATION,
        new ValueTypeInfo<>(
            ImmutableProcessInstanceCreationRecordValue.class,
            ProcessInstanceCreationIntent.class));
    mapping.put(
        ValueType.PROCESS_INSTANCE_RESULT,
        new ValueTypeInfo<>(
            ImmutableProcessInstanceResultRecordValue.class, ProcessInstanceResultIntent.class));
    mapping.put(
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        new ValueTypeInfo<>(
            ImmutableProcessMessageSubscriptionRecordValue.class,
            ProcessMessageSubscriptionIntent.class));
    mapping.put(
        ValueType.TIMER, new ValueTypeInfo<>(ImmutableTimerRecordValue.class, TimerIntent.class));
    mapping.put(
        ValueType.VARIABLE,
        new ValueTypeInfo<>(ImmutableVariableRecordValue.class, VariableIntent.class));
    mapping.put(
        ValueType.VARIABLE_DOCUMENT,
        new ValueTypeInfo<>(
            ImmutableVariableDocumentRecordValue.class, VariableDocumentIntent.class));

    TYPES = mapping;
  }

  private ValueTypes() {}

  static ValueTypeInfo<?> getTypeInfo(final ValueType valueType) {
    final var typeInfo = TYPES.get(valueType);
    if (typeInfo == null) {
      throw new IllegalArgumentException(
          String.format(
              "Expected value type to be one of %s, but was %s", TYPES.keySet(), valueType));
    }

    return typeInfo;
  }

  static ValueTypeInfo<?> getTypeInfoOrNull(final ValueType valueType) {
    return TYPES.get(valueType);
  }
}
