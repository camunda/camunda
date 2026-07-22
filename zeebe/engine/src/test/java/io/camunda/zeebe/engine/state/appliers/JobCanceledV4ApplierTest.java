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
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class JobCanceledV4ApplierTest {

  private static final String STORE_ID = "store-1";

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableJobState jobState;
  private MutableSecretReferenceState secretReferenceState;
  private JobCanceledV4Applier applier;

  @BeforeEach
  void setUp() {
    jobState = processingState.getJobState();
    secretReferenceState = processingState.getSecretReferenceState();
    applier = new JobCanceledV4Applier(processingState);
  }

  @Test
  void shouldRemoveWaitingSecretReferenceEntriesOfCanceledJob() {
    // given - a parked job waiting for two secrets, and another job waiting for one of them
    final long jobKey = 1L;
    final long otherJobKey = 2L;
    final var job = createJob(jobKey);
    createJob(otherJobKey);
    secretReferenceState.addPendingSecretReference(STORE_ID, "secret-a");
    secretReferenceState.addPendingSecretReference(STORE_ID, "secret-b");
    secretReferenceState.addWaitingJob(STORE_ID, "secret-a", jobKey);
    secretReferenceState.addWaitingJob(STORE_ID, "secret-b", jobKey);
    secretReferenceState.addWaitingJob(STORE_ID, "secret-a", otherJobKey);

    // when
    applier.applyState(jobKey, job);

    // then - the canceled job no longer waits for any secret, in either index direction
    assertThat(secretReferencesOf(jobKey)).isEmpty();
    assertThat(waitingJobsFor("secret-a")).containsExactly(otherJobKey);
    assertThat(waitingJobsFor("secret-b")).isEmpty();
    // and - the other job and the pending references are untouched: the background resolution
    // still resolves them and cleans them up through its own terminal events
    assertThat(secretReferencesOf(otherJobKey)).containsExactly("secret-a");
    assertThat(secretReferenceState.isPending(STORE_ID, "secret-a")).isTrue();
    assertThat(secretReferenceState.isPending(STORE_ID, "secret-b")).isTrue();
  }

  @Test
  void shouldCancelJobWithoutWaitingSecretReferences() {
    // given - a job that does not wait for any secret
    final long jobKey = 3L;
    final var job = createJob(jobKey);

    // when
    applier.applyState(jobKey, job);

    // then - the job is canceled like with the previous applier version
    assertThat(jobState.getState(jobKey)).isEqualTo(State.NOT_FOUND);
  }

  private JobRecord createJob(final long key) {
    final var job =
        new JobRecord().setType("task-type").setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.insertJobRecordActivatable(key, job);
    jobState.makeJobActivatableByPriority(
        job.getTypeBuffer(), key, job.getTenantId(), job.getPriority());
    return job;
  }

  private List<String> secretReferencesOf(final long jobKey) {
    final List<String> references = new ArrayList<>();
    secretReferenceState.visitSecretReferencesByJob(
        jobKey,
        (storeId, reference) -> {
          references.add(reference);
          return true;
        });
    return references;
  }

  private List<Long> waitingJobsFor(final String reference) {
    final List<Long> jobKeys = new ArrayList<>();
    secretReferenceState.visitJobsBySecretReference(
        STORE_ID,
        reference,
        jobKey -> {
          jobKeys.add(jobKey);
          return true;
        });
    return jobKeys;
  }
}
