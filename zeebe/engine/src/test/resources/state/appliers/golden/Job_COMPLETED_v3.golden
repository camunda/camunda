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
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;

class JobCompletedV3Applier implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableJobState jobState;
  private final MutableElementInstanceState elementInstanceState;
  private final MutableJobMetricsState jobMetricsState;

  JobCompletedV3Applier(final MutableProcessingState state) {
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();
    jobMetricsState = state.getJobMetricsState();
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    jobState.complete(key, value);

    final long elementInstanceKey = value.getElementInstanceKey();
    final ElementInstance elementInstance = elementInstanceState.getInstance(elementInstanceKey);

    if (elementInstance != null) {
      elementInstance.setJobKey(-1);
      elementInstanceState.updateInstance(elementInstance);
    }

    jobMetricsState.incrementMetric(value, JobMetricsExportState.COMPLETED);
  }
}
