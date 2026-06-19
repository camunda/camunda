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
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import java.util.Iterator;

/**
 * Second half of the two-step job batch activation flow gated under {@link
 * Capability#JOB_BATCH_RESERVATION_STATE}. Picks up jobs that {@code JobBatchReservedApplier}
 * already moved to {@code State.RESERVED} and flips them to {@code State.ACTIVATED}; every other
 * invariant ({@code makeJobNotActivatable}, deadline registration, record persistence) was
 * established by the reserve half in the same processor invocation, so this applier only touches
 * the state column.
 *
 * <p>Below the gate {@code JobBatchActivatedApplier} (v=1) remains the selected applier — it does
 * the legacy single-step {@code ACTIVATABLE → ACTIVATED} transition, unchanged from pre-feature
 * behavior. Above the gate {@code selectVersionFor(JobBatchIntent.ACTIVATED)} returns {@code 2} and
 * this applier runs instead, consuming the {@code RESERVED} predecessor state laid down by the
 * reserve applier in the same transaction.
 *
 * <p>The original v=1 applier is deliberately left untouched, in line with the engine rule that
 * released event appliers must not change in logic. The state-machine evolution is expressed by
 * registering a new version, not by editing the old one.
 */
public final class JobBatchActivatedV2Applier
    implements TypedEventApplier<JobBatchIntent, JobBatchRecord> {

  private final MutableJobState jobState;

  public JobBatchActivatedV2Applier(final MutableProcessingState state) {
    jobState = state.getJobState();
  }

  @Override
  public void applyState(final long key, final JobBatchRecord value) {
    final Iterator<LongValue> keys = value.jobKeys().iterator();
    while (keys.hasNext()) {
      jobState.confirmReservation(keys.next().getValue());
    }
  }

  @Override
  public Capability gatedBy() {
    return Capability.JOB_BATCH_RESERVATION_STATE;
  }
}
