/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class SecretReferenceResolutionRequestedApplierTest {

  private static final String TENANT = TenantOwned.DEFAULT_TENANT_IDENTIFIER;

  private MutableProcessingState processingState;
  private MutableSecretReferenceState state;
  private MutableJobState jobState;
  private SecretReferenceResolutionRequestedApplier applier;

  @BeforeEach
  void setUp() {
    state = processingState.getSecretReferenceState();
    jobState = processingState.getJobState();
    applier = new SecretReferenceResolutionRequestedApplier(state, jobState);
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
  void shouldParkWaitingJobSoItIsNoLongerActivatable() {
    // given - an activatable job waiting for an uncached secret
    final DirectBuffer type = wrapString("type-a");
    createActivatableJob(1L, type);
    final var record =
        new SecretReferenceRecord()
            .setStoreId("store-1")
            .setSecretReference("secret-a")
            .addJobKey(1L);

    // when
    applier.applyState(100L, record);

    // then - the job keeps its ACTIVATABLE state but is removed from the activatable index, so a
    // long poll does not collect it again until it is reactivated
    assertThat(jobState.isInState(1L, State.ACTIVATABLE)).isTrue();
    assertThat(activatableKeys(type)).isEmpty();
  }

  @Test
  void shouldParkOnlyTheWaitingJobs() {
    // given - two activatable jobs, only one waiting for the uncached secret
    final DirectBuffer type = wrapString("type-a");
    createActivatableJob(1L, type);
    createActivatableJob(2L, type);
    final var record =
        new SecretReferenceRecord()
            .setStoreId("store-1")
            .setSecretReference("secret-a")
            .addJobKey(1L);

    // when
    applier.applyState(100L, record);

    // then - only the waiting job is parked; the other stays activatable
    assertThat(activatableKeys(type)).containsExactly(2L);
  }

  @Test
  void shouldNotFailWhenWaitingJobDoesNotExist() {
    // given - a job key with no job in state (e.g. already completed or canceled)
    final var record =
        new SecretReferenceRecord()
            .setStoreId("store-1")
            .setSecretReference("secret-a")
            .addJobKey(404L);

    // when / then - the apply does not fail and still records the waiting reference
    applier.applyState(100L, record);
    assertThat(state.isPending("store-1", "secret-a")).isTrue();
    final List<Long> waitingJobs = new ArrayList<>();
    state.visitJobsBySecretReference(
        "store-1",
        "secret-a",
        jobKey -> {
          waitingJobs.add(jobKey);
          return true;
        });
    assertThat(waitingJobs).containsExactly(404L);
  }

  private void createActivatableJob(final long key, final DirectBuffer type) {
    final JobRecord record = new JobRecord().setType(type).setTenantId(TENANT).setRetries(1);
    jobState.insertJobRecordActivatable(key, record);
    jobState.makeJobActivatableByPriority(type, key, TENANT, record.getPriority());
  }

  private List<Long> activatableKeys(final DirectBuffer type) {
    final List<Long> keys = new ArrayList<>();
    jobState.forEachActivatableJobs(
        type,
        List.of(TENANT),
        (key, job) -> {
          keys.add(key);
          return true;
        });
    return keys;
  }
}
