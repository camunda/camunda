/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.prometheus.client.Counter;

public final class ProcessEngineMetrics {

  private static final String NAMESPACE = "zeebe";

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
  static final Counter EVALUATED_DMN_ELEMENTS =
      Counter.build()
          .namespace(NAMESPACE)
          .name("evaluated_dmn_elements_total")
          .help("Number of evaluated DMN elements including required decisions")
          .labelNames(ORGANIZATION_ID_LABEL, ACTION_LABEL, PARTITION_LABEL)
          .register();
  private static final String TYPE_LABEL = "type";
  static final Counter EXECUTED_INSTANCES =
      Counter.build()
          .namespace(NAMESPACE)
          .name("executed_instances_total")
          .help("Number of executed (root) process instances")
          .labelNames(ORGANIZATION_ID_LABEL, TYPE_LABEL, ACTION_LABEL, PARTITION_LABEL)
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
          .namespace(NAMESPACE)
          .name("element_instance_events_total")
          .help("Number of process element instance events")
          .labelNames(ACTION_LABEL, TYPE_LABEL, PARTITION_LABEL)
          .register();
  private static final String CREATION_MODE_LABEL = "creation_mode";
  static final Counter CREATED_PROCESS_INSTANCES =
      Counter.build()
          .namespace(NAMESPACE)
          .name("process_instance_creations_total")
          .help("Number of created (root) process instances")
          .labelNames(PARTITION_LABEL, CREATION_MODE_LABEL)
          .register();
  private final String partitionIdLabel;

  public ProcessEngineMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
  }

  public void processInstanceCreated(final ProcessInstanceCreationRecord instanceCreationRecord) {
    final var creationMode =
        instanceCreationRecord.hasStartInstructions()
            ? CreationMode.CREATION_AT_GIVEN_ELEMENT
            : CreationMode.CREATION_AT_DEFAULT_START_EVENT;

    CREATED_PROCESS_INSTANCES.labels(partitionIdLabel, creationMode.toString()).inc();
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

  private enum CreationMode {
    CREATION_AT_DEFAULT_START_EVENT,
    CREATION_AT_GIVEN_ELEMENT;

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }
}
