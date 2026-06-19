/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;

/**
 * Priority-aware v=3 applier for {@code JobIntent.CREATED}. Selected by the write side only when
 * ECV has activated {@link Capability#JOB_PRIORITIZATION}; below the gate the v=2 applier is
 * selected, which routes the job to the legacy {@code JOB_ACTIVATABLE} column family without the
 * priority index write. Apart from the state-write path, semantics match the v=2 applier — same
 * element-instance updates and metric increments.
 */
final class JobCreatedV3Applier implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableJobState jobState;
  private final MutableJobMetricsState jobMetricsState;

  JobCreatedV3Applier(final MutableProcessingState state) {
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();
    jobMetricsState = state.getJobMetricsState();
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    jobState.createWithPriorityActivation(key, value);

    final long elementInstanceKey = value.getElementInstanceKey();
    if (elementInstanceKey > 0) {
      final ElementInstance elementInstance = elementInstanceState.getInstance(elementInstanceKey);

      if (elementInstance != null) {
        if (value.getJobKind() == JobKind.EXECUTION_LISTENER) {
          elementInstance.incrementExecutionListenerIndex();
        }
        if (value.getJobKind() == JobKind.TASK_LISTENER) {
          final var eventType = toTaskListenerEventType(value.getJobListenerEventType());
          elementInstance.incrementTaskListenerIndex(eventType);
        }
        elementInstance.setJobKey(key);
        elementInstanceState.updateInstance(elementInstance);
      }
    }

    jobMetricsState.incrementMetric(value, JobMetricsExportState.CREATED);
  }

  @Override
  public Capability gatedBy() {
    return Capability.JOB_PRIORITIZATION;
  }

  private ZeebeTaskListenerEventType toTaskListenerEventType(final JobListenerEventType eventType) {
    return switch (eventType) {
      case CREATING -> ZeebeTaskListenerEventType.creating;
      case ASSIGNING -> ZeebeTaskListenerEventType.assigning;
      case UPDATING -> ZeebeTaskListenerEventType.updating;
      case COMPLETING -> ZeebeTaskListenerEventType.completing;
      case CANCELING -> ZeebeTaskListenerEventType.canceling;
      default -> throw new IllegalStateException("Unexpected JobListenerEventType: " + eventType);
    };
  }
}
