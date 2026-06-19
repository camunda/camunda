/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class JobBatchReservedApplierTest {

  /** Injected by {@link ProcessingStateExtension}. */
  private MutableProcessingState processingState;

  private MutableJobState jobState;
  private JobBatchReservedApplier reservedApplier;
  private JobBatchActivatedV2Applier activatedV2Applier;

  @BeforeEach
  public void setup() {
    jobState = processingState.getJobState();
    reservedApplier = new JobBatchReservedApplier(processingState);
    activatedV2Applier = new JobBatchActivatedV2Applier(processingState);
  }

  @Test
  void shouldMoveJobFromActivatableToReserved() {
    // given — a job in ACTIVATABLE state (the create path leaves it there)
    final long jobKey = 100L;
    final var seedJob = newJobRecord();
    jobState.create(jobKey, seedJob);
    assertThat(jobState.getState(jobKey)).isEqualTo(State.ACTIVATABLE);

    // when — the reserved applier runs against a batch carrying this job
    final var batch = newBatchWith(jobKey, seedJob);
    reservedApplier.applyState(42L, batch);

    // then — state column is now RESERVED, not yet ACTIVATED
    assertThat(jobState.getState(jobKey)).isEqualTo(State.RESERVED);
  }

  @Test
  void shouldCompleteToActivatedAfterV2ActivatedApplier() {
    // given — reservation half has run
    final long jobKey = 101L;
    final var seedJob = newJobRecord();
    jobState.create(jobKey, seedJob);
    reservedApplier.applyState(42L, newBatchWith(jobKey, seedJob));
    assertThat(jobState.getState(jobKey)).isEqualTo(State.RESERVED);

    // when — the v=2 activated applier flips the state column
    activatedV2Applier.applyState(42L, newBatchWith(jobKey, seedJob));

    // then — the job is now ACTIVATED, mirroring the legacy single-step flow's end state
    assertThat(jobState.getState(jobKey)).isEqualTo(State.ACTIVATED);
  }

  @Test
  void shouldDeclareJobBatchReservationStateGateForBothAppliers() {
    // when / then — the catalog cross-check at registration relies on these matching
    assertThat(reservedApplier.gatedBy()).isEqualTo(Capability.JOB_BATCH_RESERVATION_STATE);
    assertThat(activatedV2Applier.gatedBy()).isEqualTo(Capability.JOB_BATCH_RESERVATION_STATE);
  }

  private static JobRecord newJobRecord() {
    return new JobRecord()
        .setRetries(3)
        .setDeadline(1_000L)
        .setType("svc")
        .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  private static JobBatchRecord newBatchWith(final long jobKey, final JobRecord jobRecord) {
    final var batch = new JobBatchRecord().setType("svc");
    batch.jobKeys().add().setValue(jobKey);
    batch.jobs().add().wrap(jobRecord);
    return batch;
  }
}
