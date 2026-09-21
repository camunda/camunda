/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import org.jspecify.annotations.NullMarked;

/**
 * Drops the reservation from a job, which is what makes it visible to job workers again: the hide
 * in {@code JobBatchCollector} and {@code BpmnJobActivationBehavior#publishWork} both read the
 * token off the stored job record. The job's state and its place in the activatable index are left
 * untouched — a released job was activatable all along, it was only skipped.
 */
@NullMarked
public final class JobReleasedApplier implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableJobState jobState;

  JobReleasedApplier(final MutableProcessingState state) {
    jobState = state.getJobState();
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    jobState.updateJobRecord(key, value);
  }
}
