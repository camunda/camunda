/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class SecretReferenceResolutionRequestedApplierTest {

  private MutableProcessingState processingState;
  private MutableSecretReferenceState state;
  private MutableJobState jobState;
  private SecretReferenceResolutionRequestedApplier applier;

  @BeforeEach
  void setUp() {
    state = processingState.getSecretReferenceState();
    jobState = processingState.getJobState();
    applier = new SecretReferenceResolutionRequestedApplier(processingState);
  }

  @Test
  void shouldMarkSecretReferenceAsPending() {
    // given
    final var record =
        new SecretReferenceRecord()
            .setStoreId("store-1")
            .setSecretReference("secret-a")
            .addJobKey(1L);

    // when
    applier.applyState(100L, record);

    // then
    assertThat(state.isPending("store-1", "secret-a")).isTrue();
  }

  @Test
  void shouldAddAllJobsWaitingForSecretReference() {
    // given
    final var record =
        new SecretReferenceRecord()
            .setStoreId("store-1")
            .setSecretReference("secret-a")
            .addJobKey(1L)
            .addJobKey(2L);

    // when
    applier.applyState(100L, record);

    // then — CF3: jobs indexed by (storeId, secretReference)
    final List<Long> waitingJobs = new ArrayList<>();
    state.visitJobsBySecretReference(
        "store-1",
        "secret-a",
        jobKey -> {
          waitingJobs.add(jobKey);
          return true;
        });
    assertThat(waitingJobs).containsExactlyInAnyOrder(1L, 2L);
  }

  @Test
  void shouldAccumulateJobsAcrossSeparateResolutionRequestedEvents() {
    // given — two separate RESOLUTION_REQUESTED events for the same secret, each adding a different
    // job
    final var record1 =
        new SecretReferenceRecord()
            .setStoreId("store-1")
            .setSecretReference("secret-a")
            .addJobKey(1L);
    final var record2 =
        new SecretReferenceRecord()
            .setStoreId("store-1")
            .setSecretReference("secret-a")
            .addJobKey(2L);

    // when
    applier.applyState(100L, record1);
    applier.applyState(101L, record2);

    // then — both jobs are in the waiting-jobs index and the reference stays pending
    assertThat(state.isPending("store-1", "secret-a")).isTrue();
    final List<Long> waitingJobs = new ArrayList<>();
    state.visitJobsBySecretReference(
        "store-1",
        "secret-a",
        jobKey -> {
          waitingJobs.add(jobKey);
          return true;
        });
    assertThat(waitingJobs).containsExactlyInAnyOrder(1L, 2L);
  }

  @Test
  void shouldIndexSecretReferenceByJob() {
    // given
    final var record =
        new SecretReferenceRecord()
            .setStoreId("store-1")
            .setSecretReference("secret-a")
            .addJobKey(1L);

    // when
    applier.applyState(100L, record);

    // then — CF2: (storeId, secretReference) pairs indexed by job
    final List<Tuple> pairs = new ArrayList<>();
    state.visitSecretReferencesByJob(
        1L,
        (storeId, secretReference) -> {
          pairs.add(tuple(storeId, secretReference));
          return true;
        });
    assertThat(pairs).containsExactly(tuple("store-1", "secret-a"));
  }

  @Test
  void shouldNotDuplicateJobOnRepeatedResolutionRequest() {
    // given
    final var record =
        new SecretReferenceRecord()
            .setStoreId("store-1")
            .setSecretReference("secret-a")
            .addJobKey(1L);

    // when — apply twice, as would happen when the same job triggers a repeated activation attempt
    applier.applyState(100L, record);
    applier.applyState(101L, record);

    // then — no duplicate waiting-job entries
    final List<Long> waitingJobs = new ArrayList<>();
    state.visitJobsBySecretReference(
        "store-1",
        "secret-a",
        jobKey -> {
          waitingJobs.add(jobKey);
          return true;
        });
    assertThat(state.isPending("store-1", "secret-a")).isTrue();
    assertThat(waitingJobs).containsExactly(1L);
  }

  @Test
  void shouldMarkPendingEvenWithoutJobs() {
    // given — a record with no job keys
    final var record =
        new SecretReferenceRecord().setStoreId("store-1").setSecretReference("secret-a");

    // when
    applier.applyState(100L, record);

    // then
    assertThat(state.isPending("store-1", "secret-a")).isTrue();
    final List<Long> waitingJobs = new ArrayList<>();
    state.visitJobsBySecretReference(
        "store-1",
        "secret-a",
        jobKey -> {
          waitingJobs.add(jobKey);
          return true;
        });
    assertThat(waitingJobs).isEmpty();
  }

  @Test
  void shouldIndexMultipleSecretReferencesByJob() {
    // given — the same job key waiting on two different secret references
    final var record1 =
        new SecretReferenceRecord()
            .setStoreId("store-1")
            .setSecretReference("secret-a")
            .addJobKey(1L);
    final var record2 =
        new SecretReferenceRecord()
            .setStoreId("store-1")
            .setSecretReference("secret-b")
            .addJobKey(1L);

    // when
    applier.applyState(100L, record1);
    applier.applyState(101L, record2);

    // then — CF2: both (storeId, secretReference) pairs are indexed under job 1
    final List<Tuple> pairs = new ArrayList<>();
    state.visitSecretReferencesByJob(
        1L,
        (storeId, secretReference) -> {
          pairs.add(tuple(storeId, secretReference));
          return true;
        });
    assertThat(pairs)
        .containsExactlyInAnyOrder(tuple("store-1", "secret-a"), tuple("store-1", "secret-b"));
  }

  @Test
  void shouldMakeWaitingJobNotActivatable() {
    // given — an activatable job that references a not-yet-resolved secret
    final long jobKey = 1L;
    final var job =
        new JobRecord().setType("task-type").setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.insertJobRecordActivatable(jobKey, job);
    jobState.makeJobActivatableByPriority(
        job.getTypeBuffer(), jobKey, job.getTenantId(), job.getPriority());
    assertThat(activatableJobKeys(job)).contains(jobKey);
    final var record =
        new SecretReferenceRecord()
            .setStoreId("store-1")
            .setSecretReference("secret-a")
            .addJobKey(jobKey);

    // when
    applier.applyState(100L, record);

    // then — the job is no longer handed out until it is reactivated after resolution
    assertThat(activatableJobKeys(job)).doesNotContain(jobKey);
  }

  @Test
  void shouldNotFailWhenWaitingJobDoesNotExist() {
    // given — a record for a job that is absent from the job state
    final var record =
        new SecretReferenceRecord()
            .setStoreId("store-1")
            .setSecretReference("secret-a")
            .addJobKey(999L);

    // when / then — applying does not fail and still records the pending reference
    applier.applyState(100L, record);
    assertThat(state.isPending("store-1", "secret-a")).isTrue();
  }

  private List<Long> activatableJobKeys(final JobRecord job) {
    final List<Long> keys = new ArrayList<>();
    jobState.forEachActivatableJobs(
        job.getTypeBuffer(), List.of(job.getTenantId()), (k, j) -> keys.add(k));
    return keys;
  }
}
