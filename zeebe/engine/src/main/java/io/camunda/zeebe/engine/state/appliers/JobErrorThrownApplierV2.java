/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.processing.job.JobThrowErrorProcessor;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.jobmetrics.JobMetricsExportState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;

public class JobErrorThrownApplierV2 implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableJobState jobState;
  private final MutableElementInstanceState elementInstanceState;
  private final MutableJobMetricsState jobMetricsState;

  JobErrorThrownApplierV2(final MutableProcessingState state) {
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();
    jobMetricsState = state.getJobMetricsState();
  }

  @Override
  public void applyState(final long jobKey, final JobRecord job) {
    final var serviceTaskInstanceId = job.getElementId();
    jobState.throwError(jobKey, job);

    if (!JobThrowErrorProcessor.NO_CATCH_EVENT_FOUND.equals(serviceTaskInstanceId)) {
      final var serviceTaskInstance = elementInstanceState.getInstance(job.getElementInstanceKey());

      removeJobReference(jobKey, job, serviceTaskInstance);
    }

    jobMetricsState.incrementMetric(
        job.getType(), job.getTenantId(), job.getWorker(), JobMetricsExportState.FAILED);
  }

  private void removeJobReference(
      final long jobKey, final JobRecord job, final ElementInstance serviceTaskInstance) {
    // remove job reference to not cancel it while terminating the task
    serviceTaskInstance.setJobKey(-1L);
    elementInstanceState.updateInstance(serviceTaskInstance);

    jobState.delete(jobKey, job);
  }
}
