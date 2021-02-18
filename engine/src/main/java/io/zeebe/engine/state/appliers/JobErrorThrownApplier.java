/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.processing.common.ErrorEventHandler;
import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableJobState;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.intent.JobIntent;

public class JobErrorThrownApplier implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableJobState jobState;
  private final MutableElementInstanceState elementInstanceState;
  private final ErrorEventHandler errorEventHandler;

  JobErrorThrownApplier(final ZeebeState state) {
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();

    errorEventHandler =
        new ErrorEventHandler(
            state.getWorkflowState(),
            state.getElementInstanceState(),
            state.getEventScopeInstanceState(),
            state.getKeyGenerator());
  }

  @Override
  public void applyState(final long jobKey, final JobRecord job) {
    jobState.throwError(jobKey, job);

    final var serviceTaskInstanceKey = job.getElementInstanceKey();
    final var serviceTaskInstance = elementInstanceState.getInstance(serviceTaskInstanceKey);

    final var errorCode = job.getErrorCodeBuffer();

    if (errorEventHandler.hasCatchEvent(errorCode, serviceTaskInstance)) {

      // remove job reference to not cancel it while terminating the task
      serviceTaskInstance.setJobKey(-1L);
      elementInstanceState.updateInstance(serviceTaskInstance);

      jobState.delete(jobKey, job);
    }
  }
}
