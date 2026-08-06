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
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public final class SecretReferenceBatchJobsReactivatedApplierTest {

  private static final String STORE_ID = "storeA";
  private static final String SECRET_REF = "secret1";

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableSecretReferenceState secretReferenceState;
  private MutableJobState jobState;
  private SecretReferenceBatchJobsReactivatedApplier applier;

  @BeforeEach
  public void setup() {
    secretReferenceState = processingState.getSecretReferenceState();
    jobState = processingState.getJobState();
    applier = new SecretReferenceBatchJobsReactivatedApplier(processingState);
  }

  /**
   * Parks the job the way {@link SecretReferenceResolutionRequestedApplier} does: out of the
   * activatable index, but keeping the {@code ACTIVATABLE} state.
   */
  private JobRecord createJobParkedForSecretResolution(final long jobKey) {
    final var jobRecord =
        new JobRecord()
            .setType("test")
            .setRetries(3)
            .setDeadline(256L)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.create(jobKey, jobRecord);
    jobState.makeJobNotActivatable(jobKey, jobRecord);
    return jobRecord;
  }

  @Test
  void shouldMakeJobActivatable() {
    // given
    final long jobKey = 1L;
    final var job = createJobParkedForSecretResolution(jobKey);
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, jobKey);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(jobKey);

    // when
    applier.applyState(100L, value);

    // then
    assertThat(jobState.getState(jobKey)).isEqualTo(State.ACTIVATABLE);
    final var activatableJobs = new ArrayList<>();
    jobState.forEachActivatableJobs(
        job.getTypeBuffer(),
        List.of(job.getTenantId()),
        (key, record) -> {
          activatableJobs.add(key);
          return true;
        });
    assertThat(activatableJobs).containsExactly(jobKey);
  }

  @Test
  void shouldRemoveWaitingJobEntryFromState() {
    // given
    final long jobKey = 2L;
    createJobParkedForSecretResolution(jobKey);
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, jobKey);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(jobKey);

    // when
    applier.applyState(100L, value);

    // then - the (storeId, secretReference) prefix no longer visits this job key
    final var stillWaiting = new java.util.concurrent.atomic.AtomicBoolean(false);
    secretReferenceState.visitJobsBySecretReference(
        STORE_ID,
        SECRET_REF,
        visitedJobKey -> {
          if (visitedJobKey == jobKey) {
            stillWaiting.set(true);
          }
          return true;
        });
    assertThat(stillWaiting.get()).isFalse();
  }

  @Test
  void shouldNotMakeJobActivatableWhenOtherRefsArePending() {
    // given - job is parked and waiting on two references; one is being resolved (secret1),
    //         but the other (secret2) is still pending
    final long jobKey = 4L;
    final var job = createJobParkedForSecretResolution(jobKey);
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, jobKey);
    secretReferenceState.addPendingSecretReference(STORE_ID, "secret2");
    secretReferenceState.addWaitingJob(STORE_ID, "secret2", jobKey);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(jobKey);

    // when
    applier.applyState(100L, value);

    // then - the secret1 waiting entry is removed
    final var stillWaiting = new java.util.concurrent.atomic.AtomicBoolean(false);
    secretReferenceState.visitJobsBySecretReference(
        STORE_ID,
        SECRET_REF,
        visitedJobKey -> {
          if (visitedJobKey == jobKey) {
            stillWaiting.set(true);
          }
          return true;
        });
    assertThat(stillWaiting.get()).isFalse();

    // but the job is NOT put back into the activatable index — it still waits on secret2
    final var activatableJobs = new ArrayList<Long>();
    jobState.forEachActivatableJobs(
        job.getTypeBuffer(),
        List.of(job.getTenantId()),
        (key, record) -> {
          activatableJobs.add(key);
          return true;
        });
    assertThat(activatableJobs).isEmpty();
  }

  @Test
  void shouldNotMakeJobActivatableWhenJobHasOpenIncident() {
    // given - a parked job waiting on the secret reference, with an open incident raised for an
    //         earlier failed secret reference of the same job
    final long jobKey = 5L;
    final var jobRecord =
        new JobRecord()
            .setType("test")
            .setRetries(3)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.create(jobKey, jobRecord);
    jobState.makeJobNotActivatable(jobKey, jobRecord);
    processingState
        .getIncidentState()
        .createIncident(
            55L,
            new IncidentRecord()
                .setErrorType(ErrorType.SECRET_RESOLUTION_ERROR)
                .setJobKey(jobKey)
                .setProcessInstanceKey(7L)
                .setElementInstanceKey(11L));
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, jobKey);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(jobKey);

    // when
    applier.applyState(100L, value);

    // then - the waiting entry is removed, but the job stays parked; resolving the incident is
    //        the only path that reactivates it
    final var activatableJobs = new ArrayList<Long>();
    jobState.forEachActivatableJobs(
        jobRecord.getTypeBuffer(),
        List.of(jobRecord.getTenantId()),
        (key, record) -> {
          activatableJobs.add(key);
          return true;
        });
    assertThat(activatableJobs).isEmpty();
  }

  @Test
  void shouldSkipActivationIfJobNoLongerExistsInState() {
    // given - a waiting-job entry exists but the job itself was already removed
    final long jobKey = 3L;
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, jobKey);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(jobKey);

    // when / then - no exception is thrown and the waiting entry is still removed
    applier.applyState(100L, value);

    final var stillWaiting = new java.util.concurrent.atomic.AtomicBoolean(false);
    secretReferenceState.visitJobsBySecretReference(
        STORE_ID,
        SECRET_REF,
        visitedJobKey -> {
          if (visitedJobKey == jobKey) {
            stillWaiting.set(true);
          }
          return true;
        });
    assertThat(stillWaiting.get()).isFalse();
  }
}
