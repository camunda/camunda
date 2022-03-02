/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotationForFields;
import edu.umd.cs.findbugs.annotations.DefaultAnnotationForParameters;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.ReturnValuesAreNonnullByDefault;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
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
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.DeploymentDistributionRecordValue;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.ErrorRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessEventRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceResultRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Provides a mapping of all {@link ValueType} to their concrete implementations. It should be an
 * exhaustive map of all possible {@link ValueType}, so if you add one, make sure to update the
 * mapping here as well.
 */
@SuppressWarnings("java:S1452")
@ThreadSafe
@Immutable
@ReturnValuesAreNonnullByDefault
@DefaultAnnotationForParameters(NonNull.class)
@DefaultAnnotationForFields(NonNull.class)
final class ValueTypes {
  private final Map<ValueType, ValueTypeInfo<?, ?>> types;

  private ValueTypes() {
    types = Collections.unmodifiableMap(loadValueTypes());
  }

  static ValueTypeInfo<?, ?> getTypeInfo(final ValueType valueType) {
    final ValueTypeInfo<?, ?> typeInfo = Singleton.INSTANCE.types.get(valueType);
    if (typeInfo == null) {
      throw new IllegalArgumentException(
          String.format(
              "Expected value type to be one of %s, but was %s",
              Singleton.INSTANCE.types.keySet(), valueType));
    }

    return typeInfo;
  }

  @Nullable
  @CheckForNull
  static ValueTypeInfo<?, ?> getTypeInfoOrNull(final ValueType valueType) {
    return Singleton.INSTANCE.types.get(valueType);
  }

  // suppressed warning about method length; this is simply populating a map, which while tedious,
  // isn't incredibly complex
  @SuppressWarnings("java:S138")
  @NonNull
  private Map<ValueType, ValueTypeInfo<?, ?>> loadValueTypes() {
    final Map<ValueType, ValueTypeInfo<?, ?>> mapping = new EnumMap<>(ValueType.class);

    mapping.put(
        ValueType.DECISION, new ValueTypeInfo<>(DecisionRecordValue.class, DecisionIntent.class));
    mapping.put(
        ValueType.DECISION_EVALUATION,
        new ValueTypeInfo<>(DecisionEvaluationRecordValue.class, DecisionEvaluationIntent.class));
    mapping.put(
        ValueType.DECISION_REQUIREMENTS,
        new ValueTypeInfo<>(
            DecisionRequirementsRecordValue.class, DecisionRequirementsIntent.class));
    mapping.put(
        ValueType.DEPLOYMENT,
        new ValueTypeInfo<>(DeploymentRecordValue.class, DeploymentIntent.class));
    mapping.put(
        ValueType.DEPLOYMENT_DISTRIBUTION,
        new ValueTypeInfo<>(
            DeploymentDistributionRecordValue.class, DeploymentDistributionIntent.class));
    mapping.put(ValueType.ERROR, new ValueTypeInfo<>(ErrorRecordValue.class, ErrorIntent.class));
    mapping.put(
        ValueType.INCIDENT, new ValueTypeInfo<>(IncidentRecordValue.class, IncidentIntent.class));
    mapping.put(ValueType.JOB, new ValueTypeInfo<>(JobRecordValue.class, JobIntent.class));
    mapping.put(
        ValueType.JOB_BATCH, new ValueTypeInfo<>(JobBatchRecordValue.class, JobBatchIntent.class));
    mapping.put(
        ValueType.MESSAGE, new ValueTypeInfo<>(MessageRecordValue.class, MessageIntent.class));
    mapping.put(
        ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
        new ValueTypeInfo<>(
            MessageStartEventSubscriptionRecordValue.class,
            MessageStartEventSubscriptionIntent.class));
    mapping.put(
        ValueType.MESSAGE_SUBSCRIPTION,
        new ValueTypeInfo<>(MessageSubscriptionRecordValue.class, MessageSubscriptionIntent.class));
    mapping.put(ValueType.PROCESS, new ValueTypeInfo<>(Process.class, ProcessIntent.class));
    mapping.put(
        ValueType.PROCESS_EVENT,
        new ValueTypeInfo<>(ProcessEventRecordValue.class, ProcessEventIntent.class));
    mapping.put(
        ValueType.PROCESS_INSTANCE,
        new ValueTypeInfo<>(ProcessInstanceRecordValue.class, ProcessInstanceIntent.class));
    mapping.put(
        ValueType.PROCESS_INSTANCE_CREATION,
        new ValueTypeInfo<>(
            ProcessInstanceCreationRecordValue.class, ProcessInstanceCreationIntent.class));
    mapping.put(
        ValueType.PROCESS_INSTANCE_RESULT,
        new ValueTypeInfo<>(
            ProcessInstanceResultRecordValue.class, ProcessInstanceResultIntent.class));
    mapping.put(
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        new ValueTypeInfo<>(
            ProcessMessageSubscriptionRecordValue.class, ProcessMessageSubscriptionIntent.class));
    mapping.put(ValueType.TIMER, new ValueTypeInfo<>(TimerRecordValue.class, TimerIntent.class));
    mapping.put(
        ValueType.VARIABLE, new ValueTypeInfo<>(VariableRecordValue.class, VariableIntent.class));
    mapping.put(
        ValueType.VARIABLE_DOCUMENT,
        new ValueTypeInfo<>(VariableDocumentRecordValue.class, VariableDocumentIntent.class));

    return mapping;
  }

  private static final class Singleton {
    private static final ValueTypes INSTANCE = new ValueTypes();
  }
}
