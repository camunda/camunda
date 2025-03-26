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
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;

final class JobCreatedApplier implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableJobState jobState;

  JobCreatedApplier(final MutableProcessingState state) {
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    jobState.create(key, value);

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
