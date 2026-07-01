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
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import java.util.Iterator;

/**
 * First half of the two-step job batch activation flow introduced to demonstrate state-machine
 * evolution under ECV. For each job in the batch the applier persists the worker-supplied record,
 * transitions the job from {@code State.ACTIVATABLE} to {@code State.RESERVED}, removes the job
 * from the activatable column families (so a parallel batch can't re-claim it), and registers the
 * activation deadline. The follow-up {@code JobBatchActivatedV2Applier} flips the state column from
 * {@code RESERVED} to {@code ACTIVATED} in the same processor invocation.
 *
 * <p>Gated under {@link Capability#JOB_BATCH_RESERVATION_STATE}: registration is rejected unless
 * the catalog lists {@code (JobBatchIntent.RESERVED, 1)} there. Below that ordinal {@code
 * JobBatchActivateProcessor} does not emit {@code RESERVED}, so this applier is never invoked and
 * the legacy v=1 of {@code JobBatchActivatedApplier} runs unchanged on the single {@code ACTIVATED}
 * follow-up — record stream and state mutations stay byte-identical to a pre-feature broker.
 */
public final class JobBatchReservedApplier
    implements TypedEventApplier<JobBatchIntent, JobBatchRecord> {

  private final MutableJobState jobState;

  public JobBatchReservedApplier(final MutableProcessingState state) {
    jobState = state.getJobState();
  }

  @Override
  public void applyState(final long key, final JobBatchRecord value) {
    final Iterator<JobRecord> jobs = value.jobs().iterator();
    final Iterator<LongValue> keys = value.jobKeys().iterator();
    while (jobs.hasNext() && keys.hasNext()) {
      final long jobKey = keys.next().getValue();
      final JobRecord jobRecord = jobs.next();
      jobState.reserve(jobKey, jobRecord);
    }
  }

  @Override
  public Capability gatedBy() {
    return Capability.JOB_BATCH_RESERVATION_STATE;
  }
}
