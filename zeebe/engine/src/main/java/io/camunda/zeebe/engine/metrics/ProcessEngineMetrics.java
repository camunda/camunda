/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.CreationMode;
import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.EngineAction;
import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.EngineKeyNames;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.util.collection.Map3D;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class ProcessEngineMetrics {

  public static final String EXECUTED_EVENT_ELEMENT_TYPE_VALUE = "ROOT_PROCESS_INSTANCE";
  private static final String ORGANIZATION_ID =
      System.getenv().getOrDefault("CAMUNDA_CLOUD_ORGANIZATION_ID", "null");
  private final MeterRegistry registry;

  private final Map<CreationMode, Counter> rootProcessInstances = new EnumMap<>(CreationMode.class);
  private final Map3D<EngineAction, BpmnElementType, BpmnEventType, Counter> elementInstanceEvents =
      Map3D.ofEnum(EngineAction.class, BpmnElementType.class, BpmnEventType.class, Counter[]::new);
  private final Map<EngineAction, Counter> executedEvents = new EnumMap<>(EngineAction.class);
  private final Map<EngineAction, Counter> evaluatedDmnElements = new EnumMap<>(EngineAction.class);

  public ProcessEngineMetrics(final MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "must specify a registry");
  }

  public void processInstanceCreated(final ProcessInstanceCreationRecord instanceCreationRecord) {
    final var creationMode =
        instanceCreationRecord.hasStartInstructions()
            ? CreationMode.CREATION_AT_GIVEN_ELEMENT
            : CreationMode.CREATION_AT_DEFAULT_START_EVENT;

    rootProcessInstances
        .computeIfAbsent(creationMode, this::registerRootProcessInstanceCounter)
        .increment();
  }

  public void elementInstanceActivated(
      final BpmnElementContext context, final BpmnEventType eventType) {
    final var elementType = context.getBpmnElementType();
    final var eventTypeName = extractEventTypeName(eventType);
    elementInstanceEvent(EngineAction.ACTIVATED, elementType, eventTypeName);

    if (isRootProcessInstance(elementType, context.getParentProcessInstanceKey())) {
      increaseRootProcessInstance(EngineAction.ACTIVATED);
    }
  }

  public void elementInstanceCompleted(
      final BpmnElementContext context, final BpmnEventType eventType) {
    final var elementType = context.getBpmnElementType();
    final var eventTypeName = extractEventTypeName(eventType);
    elementInstanceEvent(EngineAction.COMPLETED, elementType, eventTypeName);

    if (isRootProcessInstance(elementType, context.getParentProcessInstanceKey())) {
      increaseRootProcessInstance(EngineAction.COMPLETED);
    }
  }

  public void elementInstanceTerminated(
      final BpmnElementContext context, final BpmnEventType eventType) {
    final var elementType = context.getBpmnElementType();
    final var eventTypeName = extractEventTypeName(eventType);
    elementInstanceEvent(EngineAction.TERMINATED, elementType, eventTypeName);

    if (isRootProcessInstance(elementType, context.getParentProcessInstanceKey())) {
      increaseRootProcessInstance(EngineAction.TERMINATED);
    }
  }

  public void increaseSuccessfullyEvaluatedDmnElements(final int amount) {
    increaseEvaluatedDmnElements(EngineAction.EVALUATED_SUCCESSFULLY, amount);
  }

  public void increaseFailedEvaluatedDmnElements(final int amount) {
    increaseEvaluatedDmnElements(EngineAction.EVALUATED_FAILED, amount);
  }

  private void elementInstanceEvent(
      final EngineAction action, final BpmnElementType elementType, final BpmnEventType eventType) {
    elementInstanceEvents
        .computeIfAbsent(action, elementType, eventType, this::registerElementInstanceEventCounter)
        .increment();
  }

  private void increaseRootProcessInstance(final EngineAction action) {
    executedEvents.computeIfAbsent(action, this::registerExecutedEventCounter).increment();
  }

  private void increaseEvaluatedDmnElements(final EngineAction action, final int amount) {
    evaluatedDmnElements
        .computeIfAbsent(action, this::registerEvaluatedDmnElementCounter)
        .increment(amount);
  }

  private boolean isProcessInstance(final BpmnElementType elementType) {
    return BpmnElementType.PROCESS == elementType;
  }

  private boolean isRootProcessInstance(
      final BpmnElementType elementType, final long parentProcessInstanceKey) {
    return isProcessInstance(elementType) && parentProcessInstanceKey == -1;
  }

  private BpmnEventType extractEventTypeName(final BpmnEventType eventType) {
    return eventType != null ? eventType : BpmnEventType.UNSPECIFIED;
  }

  private Counter registerRootProcessInstanceCounter(final CreationMode creationMode) {
    final var meterDoc = EngineMetricsDoc.ROOT_PROCESS_INSTANCE_COUNT;
    return Counter.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .tag(EngineKeyNames.CREATION_MODE.asString(), creationMode.toString())
        .tag(EngineKeyNames.ORGANIZATION_ID.asString(), ORGANIZATION_ID)
        .register(registry);
  }

  private Counter registerElementInstanceEventCounter(
      final EngineAction engineAction,
      final BpmnElementType bpmnElementType,
      final BpmnEventType bpmnEventType) {
    final var meterDoc = EngineMetricsDoc.ELEMENT_INSTANCE_EVENTS;
    return Counter.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .tag(EngineKeyNames.ACTION.asString(), engineAction.toString())
        .tag(EngineKeyNames.ELEMENT_TYPE.asString(), bpmnElementType.name())
        .tag(EngineKeyNames.EVENT_TYPE.asString(), bpmnEventType.name())
        .register(registry);
  }

  private Counter registerExecutedEventCounter(final EngineAction engineAction) {
    final var meterDoc = EngineMetricsDoc.EXECUTED_EVENTS;
    return Counter.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .tag(EngineKeyNames.ACTION.asString(), engineAction.toString())
        .tag(EngineKeyNames.ELEMENT_TYPE.asString(), EXECUTED_EVENT_ELEMENT_TYPE_VALUE)
        .tag(EngineKeyNames.ORGANIZATION_ID.asString(), ORGANIZATION_ID)
        .register(registry);
  }

  private Counter registerEvaluatedDmnElementCounter(final EngineAction engineAction) {
    final var meterDoc = EngineMetricsDoc.EVALUATED_DMN_ELEMENTS;
    return Counter.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .tag(EngineKeyNames.ACTION.asString(), engineAction.toString())
        .tag(EngineKeyNames.ORGANIZATION_ID.asString(), ORGANIZATION_ID)
        .register(registry);
  }
}
