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
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
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

  private void createActivatedJob(final long jobKey) {
    final var jobRecord =
        new JobRecord()
            .setType("test")
            .setRetries(3)
            .setDeadline(256L)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.create(jobKey, jobRecord);
    jobState.activate(jobKey, jobRecord);
  }

  @Test
  void shouldMakeJobActivatable() {
    // given
    final long jobKey = 1L;
    createActivatedJob(jobKey);
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
  }

  @Test
  void shouldRemoveWaitingJobEntryFromState() {
    // given
    final long jobKey = 2L;
    createActivatedJob(jobKey);
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
    // given - job is activated and waiting on two references; one is being resolved (secret1),
    //         but the other (secret2) is still pending
    final long jobKey = 4L;
    createActivatedJob(jobKey);
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

    // but job is NOT made activatable — it still waits on secret2
    assertThat(jobState.getState(jobKey)).isNotEqualTo(State.ACTIVATABLE);
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
