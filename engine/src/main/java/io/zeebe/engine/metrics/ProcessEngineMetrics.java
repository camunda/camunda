/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.protocol.record.value.BpmnElementType;

public final class ProcessEngineMetrics {

  private static final String ORGANIZATION_ID =
      System.getenv().getOrDefault("CAMUNDA_CLOUD_ORGANIZATION_ID", "null");

  private static final String ACTION_ACTIVATED = "activated";
  private static final String ACTION_COMPLETED = "completed";
  private static final String ACTION_TERMINATED = "terminated";

  private static final Counter ELEMENT_INSTANCE_EVENTS =
      Counter.build()
          .namespace("zeebe")
          .name("element_instance_events_total")
          .help("Number of process element instance events")
          .labelNames("action", "type", "partition")
          .register();

  private static final Gauge RUNNING_PROCESS_INSTANCES =
      Gauge.build()
          .namespace("zeebe")
          .name("running_process_instances_total")
          .help("Number of running process instances")
          .labelNames("partition")
          .register();

  private static final Counter EXECUTED_INSTANCES =
      Counter.build()
          .namespace("zeebe")
          .name("executed_instances_total")
          .help("Number of executed instances")
          .labelNames("organizationId", "type", "action", "partition")
          .register();

  private final String partitionIdLabel;

  public ProcessEngineMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
  }

  private void elementInstanceEvent(final String action, final BpmnElementType elementType) {
    ELEMENT_INSTANCE_EVENTS.labels(action, elementType.name(), partitionIdLabel).inc();
  }

  private void processInstanceCreated() {
    RUNNING_PROCESS_INSTANCES.labels(partitionIdLabel).inc();
  }

  private void processInstanceFinished() {
    RUNNING_PROCESS_INSTANCES.labels(partitionIdLabel).dec();
  }

  private void increaseRootProcessInstance(final String action) {
    EXECUTED_INSTANCES
        .labels(ORGANIZATION_ID, "ROOT_PROCESS_INSTANCE", action, partitionIdLabel)
        .inc();
  }

  public void elementInstanceActivated(final BpmnElementContext context) {
    final BpmnElementType elementType = context.getBpmnElementType();
    elementInstanceEvent(ACTION_ACTIVATED, elementType);

    if (isProcessInstance(elementType)) {
      processInstanceCreated();
    }

    if (isRootProcessInstance(elementType, context.getParentProcessInstanceKey())) {
      increaseRootProcessInstance(ACTION_ACTIVATED);
    }
  }

  public void elementInstanceCompleted(final BpmnElementContext context) {
    final BpmnElementType elementType = context.getBpmnElementType();
    elementInstanceEvent(ACTION_COMPLETED, elementType);

    if (isProcessInstance(elementType)) {
      processInstanceFinished();
    }

    if (isRootProcessInstance(elementType, context.getParentProcessInstanceKey())) {
      increaseRootProcessInstance(ACTION_COMPLETED);
    }
  }

  public void elementInstanceTerminated(final BpmnElementContext context) {
    final BpmnElementType elementType = context.getBpmnElementType();
    elementInstanceEvent(ACTION_TERMINATED, elementType);

    if (isProcessInstance(elementType)) {
      processInstanceFinished();
    }

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
}
