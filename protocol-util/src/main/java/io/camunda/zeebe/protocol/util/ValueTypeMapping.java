/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.util;

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessEventIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
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
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBatchRecordValue;
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

  /** @return the set of mappable value types */
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
        ValueType.PROCESS_INSTANCE_BATCH,
        new Mapping<>(ProcessInstanceBatchRecordValue.class, ProcessInstanceBatchIntent.class));

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
