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

public final class WorkflowEngineMetrics {

  private static final Counter ELEMENT_INSTANCE_EVENTS =
      Counter.build()
          .namespace("zeebe")
          .name("element_instance_events_total")
          .help("Number of workflow element instance events")
          .labelNames("action", "type", "partition")
          .register();

  private static final Gauge RUNNING_WORKFLOW_INSTANCES =
      Gauge.build()
          .namespace("zeebe")
          .name("running_workflow_instances_total")
          .help("Number of running workflow instances")
          .labelNames("partition")
          .register();

  private final String partitionIdLabel;

  public WorkflowEngineMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
  }

  private void elementInstanceEvent(final String action, final BpmnElementType elementType) {
    ELEMENT_INSTANCE_EVENTS.labels(action, elementType.name(), partitionIdLabel).inc();
  }

  private void workflowInstanceCreated() {
    RUNNING_WORKFLOW_INSTANCES.labels(partitionIdLabel).inc();
  }

  private void workflowInstanceFinished() {
    RUNNING_WORKFLOW_INSTANCES.labels(partitionIdLabel).dec();
  }

  public void elementInstanceActivated(final BpmnElementType elementType) {
    elementInstanceEvent("activated", elementType);

    if (isWorkflowInstance(elementType)) {
      workflowInstanceCreated();
    }
  }

  public void elementInstanceCompleted(final BpmnElementType elementType) {
    elementInstanceEvent("completed", elementType);

    if (isWorkflowInstance(elementType)) {
      workflowInstanceFinished();
    }
  }

  public void elementInstanceTerminated(final BpmnElementType elementType) {
    elementInstanceEvent("terminated", elementType);

    if (isWorkflowInstance(elementType)) {
      workflowInstanceFinished();
    }
  }

  private boolean isWorkflowInstance(final BpmnElementType elementType) {
    return BpmnElementType.PROCESS == elementType;
  }
}
