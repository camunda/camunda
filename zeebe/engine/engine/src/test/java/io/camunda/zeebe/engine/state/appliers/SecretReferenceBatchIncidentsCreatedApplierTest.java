/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public final class SecretReferenceBatchIncidentsCreatedApplierTest {

  private static final String STORE_ID = "storeA";
  private static final String SECRET_REF = "secret1";

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableSecretReferenceState secretReferenceState;
  private MutableJobState jobState;
  private SecretReferenceBatchIncidentsCreatedApplier applier;

  @BeforeEach
  public void setup() {
    secretReferenceState = processingState.getSecretReferenceState();
    jobState = processingState.getJobState();
    applier = new SecretReferenceBatchIncidentsCreatedApplier(secretReferenceState);
  }

  @Test
  void shouldRemoveWaitingJobEntriesFromState() {
    // given - two jobs waiting on the secret reference
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 1L);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 2L);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L)
            .addJobKey(2L);

    // when
    applier.applyState(100L, value);

    // then - no job is waiting on the secret reference anymore
    assertThat(waitingJobs(STORE_ID, SECRET_REF)).isEmpty();
  }

  @Test
  void shouldLeaveEntriesOfOtherSecretReferencesIntact() {
    // given - the job waits on two secret references; only secret1's batch is applied
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 3L);
    secretReferenceState.addPendingSecretReference(STORE_ID, "secret2");
    secretReferenceState.addWaitingJob(STORE_ID, "secret2", 3L);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(3L);

    // when
    applier.applyState(100L, value);

    // then - the secret1 entry is removed but the secret2 entry remains
    assertThat(waitingJobs(STORE_ID, SECRET_REF)).isEmpty();
    assertThat(waitingJobs(STORE_ID, "secret2")).containsExactly(3L);
  }

  @Test
  void shouldKeepJobParked() {
    // given - a parked job waiting on the secret reference
    final long jobKey = 4L;
    final var jobRecord =
        new JobRecord()
            .setType("test")
            .setRetries(3)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.create(jobKey, jobRecord);
    jobState.makeJobNotActivatable(jobKey, jobRecord);
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, jobKey);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(jobKey);

    // when
    applier.applyState(100L, value);

    // then - the job is not made activatable; the incident holds it until resolution
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

  private List<Long> waitingJobs(final String storeId, final String secretReference) {
    final var jobKeys = new ArrayList<Long>();
    secretReferenceState.visitJobsBySecretReference(
        storeId,
        secretReference,
        jobKey -> {
          jobKeys.add(jobKey);
          return true;
        });
    return jobKeys;
  }
}
