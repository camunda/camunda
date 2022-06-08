/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.prometheus.client.Counter;

public final class ProcessEngineMetrics {

  static final Counter EXECUTED_INSTANCES =
      Counter.build()
          .namespace("zeebe")
          .name("executed_instances_total")
          .help("Number of executed (root) process instances")
          .labelNames("organizationId", "type", "action", "partition")
          .register();
  static final Counter EVALUATED_DMN_ELEMENTS =
      Counter.build()
          .namespace("zeebe")
          .name("evaluated_dmn_elements_total")
          .help("Number of evaluated DMN elements including required decisions")
          .labelNames("organizationId", "action", "partition")
          .register();
  private static final String ORGANIZATION_ID =
      System.getenv().getOrDefault("CAMUNDA_CLOUD_ORGANIZATION_ID", "null");
  private static final String ACTION_ACTIVATED = "activated";
  private static final String ACTION_COMPLETED = "completed";
  private static final String ACTION_TERMINATED = "terminated";
  private static final String ACTION_EVALUATED_SUCCESSFULLY = "evaluated_successfully";
  private static final String ACTION_EVALUATED_FAILED = "evaluated_failed";
  private static final Counter ELEMENT_INSTANCE_EVENTS =
      Counter.build()
          .namespace("zeebe")
          .name("element_instance_events_total")
          .help("Number of process element instance events")
          .labelNames("action", "type", "partition")
          .register();
  private final String partitionIdLabel;

  public ProcessEngineMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
  }

  private void elementInstanceEvent(final String action, final BpmnElementType elementType) {
    ELEMENT_INSTANCE_EVENTS.labels(action, elementType.name(), partitionIdLabel).inc();
  }

  private void increaseRootProcessInstance(final String action) {
    EXECUTED_INSTANCES
        .labels(ORGANIZATION_ID, "ROOT_PROCESS_INSTANCE", action, partitionIdLabel)
        .inc();
  }

  public void elementInstanceActivated(final BpmnElementContext context) {
    final var elementType = context.getBpmnElementType();
    elementInstanceEvent(ACTION_ACTIVATED, elementType);

    if (isRootProcessInstance(elementType, context.getParentProcessInstanceKey())) {
      increaseRootProcessInstance(ACTION_ACTIVATED);
    }
  }

  public void elementInstanceCompleted(final BpmnElementContext context) {
    final var elementType = context.getBpmnElementType();
    elementInstanceEvent(ACTION_COMPLETED, elementType);

    if (isRootProcessInstance(elementType, context.getParentProcessInstanceKey())) {
      increaseRootProcessInstance(ACTION_COMPLETED);
    }
  }

  public void elementInstanceTerminated(final BpmnElementContext context) {
    final var elementType = context.getBpmnElementType();
    elementInstanceEvent(ACTION_TERMINATED, elementType);

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
    EVALUATED_DMN_ELEMENTS.labels(ORGANIZATION_ID, action, partitionIdLabel).inc(amount);
  }
}
