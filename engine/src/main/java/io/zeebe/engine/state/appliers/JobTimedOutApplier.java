/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.mutable.MutableJobState;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.intent.JobIntent;

public class JobTimedOutApplier implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableJobState jobState;

  JobTimedOutApplier(final MutableZeebeState state) {
    jobState = state.getJobState();
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    jobState.timeout(key, value);
  }
}
