/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers.v1;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;

public class JobRecurredApplier implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableJobState jobState;

  public JobRecurredApplier(final MutableProcessingState processingState) {
    jobState = processingState.getJobState();
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    jobState.recurAfterBackoff(key, value);
  }
}
