/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.micrometer.core.instrument.Metrics;

public final class ProcessEngineMetrics {
  private static final io.micrometer.core.instrument.MeterRegistry METER_REGISTRY =
      Metrics.globalRegistry;

  private static final String NAMESPACE = "zeebe";
  static final io.micrometer.core.instrument.Counter.Builder EVALUATED_DMN_ELEMENTS_BUILDER =
      io.micrometer.core.instrument.Counter.builder(NAMESPACE + "_evaluated_dmn_elements_total")
          .description("Number of evaluated DMN elements including required decisions");
  static final io.micrometer.core.instrument.Counter.Builder EXECUTED_INSTANCES_BUILDER =
      io.micrometer.core.instrument.Counter.builder(NAMESPACE + "_executed_instances_total")
          .description("Number of executed (root) process instances");
  static final io.micrometer.core.instrument.Counter.Builder ELEMENT_INSTANCE_EVENTS_BUILDER =
      io.micrometer.core.instrument.Counter.builder(NAMESPACE + "_element_instance_events_total")
          .description("Number of process element instance events");
  static final io.micrometer.core.instrument.Counter.Builder CREATED_PROCESS_INSTANCES_BUILDER =
      io.micrometer.core.instrument.Counter.builder(NAMESPACE + "_process_instance_creations_total")
          .description("Number of created (root) process instances");

  /**
   * Metrics that are annotated with this label are vitally important for usage tracking and
   * data-based decision-making as part of Camunda's SaaS offering.
   *
   * <p>DO NOT REMOVE this label from existing metrics without previous discussion within the team.
   *
   * <p>At the same time, NEW METRICS MAY NOT NEED THIS label. In that case, it is preferable to not
   * add this label to a metric as Prometheus best practices warn against using labels with a high
   * cardinality of possible values.
   */
  private static final String ORGANIZATION_ID_LABEL = "organizationId";

  private static final String PARTITION_LABEL = "partition";
  private static final String ACTION_LABEL = "action";
  private static final String EVENT_TYPE = "eventType";
  private static final String TYPE_LABEL = "type";
  private static final String ORGANIZATION_ID =
      System.getenv().getOrDefault("CAMUNDA_CLOUD_ORGANIZATION_ID", "null");
  private static final String ACTION_ACTIVATED = "activated";
  private static final String ACTION_COMPLETED = "completed";
  private static final String ACTION_TERMINATED = "terminated";
  private static final String ACTION_EVALUATED_SUCCESSFULLY = "evaluated_successfully";
  private static final String ACTION_EVALUATED_FAILED = "evaluated_failed";
  private static final String CREATION_MODE_LABEL = "creation_mode";
  private final String partitionIdLabel;

  public ProcessEngineMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
  }

  public void processInstanceCreated(final ProcessInstanceCreationRecord instanceCreationRecord) {
    final var creationMode =
        instanceCreationRecord.hasStartInstructions()
            ? CreationMode.CREATION_AT_GIVEN_ELEMENT
            : CreationMode.CREATION_AT_DEFAULT_START_EVENT;

    CREATED_PROCESS_INSTANCES_BUILDER
        .tags(PARTITION_LABEL, partitionIdLabel, CREATION_MODE_LABEL, creationMode.toString())
        .register(METER_REGISTRY)
        .increment();
  }

  private void elementInstanceEvent(
      final String action, final BpmnElementType elementType, final String eventType) {
    ELEMENT_INSTANCE_EVENTS_BUILDER
        .tags(
            ACTION_LABEL,
            action,
            TYPE_LABEL,
            "ROOT_PROCESS_INSTANCE",
            PARTITION_LABEL,
            partitionIdLabel,
            EVENT_TYPE,
            eventType)
        .register(METER_REGISTRY)
        .increment();
  }

  private void increaseRootProcessInstance(final String action) {
    EXECUTED_INSTANCES_BUILDER
        .tags(
            ORGANIZATION_ID_LABEL,
            ORGANIZATION_ID,
            TYPE_LABEL,
            "ROOT_PROCESS_INSTANCE",
            ACTION_LABEL,
            action,
            PARTITION_LABEL,
            partitionIdLabel)
        .register(METER_REGISTRY)
        .increment();
  }

  public void elementInstanceActivated(
      final BpmnElementContext context, final BpmnEventType eventType) {
    final var elementType = context.getBpmnElementType();
    final String eventTypeName = extractEventTypeName(eventType);
    elementInstanceEvent(ACTION_ACTIVATED, elementType, eventTypeName);

    if (isRootProcessInstance(elementType, context.getParentProcessInstanceKey())) {
      increaseRootProcessInstance(ACTION_ACTIVATED);
    }
  }

  public void elementInstanceCompleted(
      final BpmnElementContext context, final BpmnEventType eventType) {
    final var elementType = context.getBpmnElementType();
    final String eventTypeName = extractEventTypeName(eventType);
    elementInstanceEvent(ACTION_COMPLETED, elementType, eventTypeName);

    if (isRootProcessInstance(elementType, context.getParentProcessInstanceKey())) {
      increaseRootProcessInstance(ACTION_COMPLETED);
    }
  }

  public void elementInstanceTerminated(
      final BpmnElementContext context, final BpmnEventType eventType) {
    final var elementType = context.getBpmnElementType();
    final String eventTypeName = extractEventTypeName(eventType);
    elementInstanceEvent(ACTION_TERMINATED, elementType, eventTypeName);

    if (isRootProcessInstance(elementType, context.getParentProcessInstanceKey())) {
      increaseRootProcessInstance(ACTION_TERMINATED);
    }
  }

  private boolean isProcessInstance(final BpmnElementType elementType) {
    return BpmnElementType.PROCESS == elementType;
  }

  private boolean isRootProcessInstance(
      final BpmnElementType elementType, final long parentProcessInstanceKey) {
    return isProcessInstance(elementType) && parentProcessInstanceKey == -1;
  }

  public void increaseSuccessfullyEvaluatedDmnElements(final int amount) {
    increaseEvaluatedDmnElements(ACTION_EVALUATED_SUCCESSFULLY, amount);
  }

  public void increaseFailedEvaluatedDmnElements(final int amount) {
    increaseEvaluatedDmnElements(ACTION_EVALUATED_FAILED, amount);
  }

  private void increaseEvaluatedDmnElements(final String action, final int amount) {
    EVALUATED_DMN_ELEMENTS_BUILDER
        .tags(
            ORGANIZATION_ID_LABEL,
            ORGANIZATION_ID,
            ACTION_LABEL,
            action,
            PARTITION_LABEL,
            partitionIdLabel)
        .register(METER_REGISTRY)
        .increment();
  }

  private String extractEventTypeName(final BpmnEventType eventType) {
    return eventType != null ? eventType.name() : BpmnEventType.UNSPECIFIED.name();
  }

  private enum CreationMode {
    CREATION_AT_DEFAULT_START_EVENT,
    CREATION_AT_GIVEN_ELEMENT;

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }
}
