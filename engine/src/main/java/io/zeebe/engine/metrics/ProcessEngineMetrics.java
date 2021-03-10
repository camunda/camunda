/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.zeebe.protocol.record.value.BpmnElementType;

public final class ProcessEngineMetrics {

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

  public void elementInstanceActivated(final BpmnElementType elementType) {
    elementInstanceEvent("activated", elementType);

    if (isProcessInstance(elementType)) {
      processInstanceCreated();
    }
  }

  public void elementInstanceCompleted(final BpmnElementType elementType) {
    elementInstanceEvent("completed", elementType);

    if (isProcessInstance(elementType)) {
      processInstanceFinished();
    }
  }

  public void elementInstanceTerminated(final BpmnElementType elementType) {
    elementInstanceEvent("terminated", elementType);

    if (isProcessInstance(elementType)) {
      processInstanceFinished();
    }
  }

  private boolean isProcessInstance(final BpmnElementType elementType) {
    return BpmnElementType.PROCESS == elementType;
  }
}
