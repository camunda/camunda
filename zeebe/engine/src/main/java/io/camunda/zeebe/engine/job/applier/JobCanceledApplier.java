/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.job.applier;

import io.camunda.zeebe.engine.common.state.TypedEventApplier;
import io.camunda.zeebe.engine.common.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.common.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;

public class JobCanceledApplier implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableJobState jobState;

  public JobCanceledApplier(final MutableProcessingState state) {
    jobState = state.getJobState();
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    jobState.cancel(key, value);
  }
}
