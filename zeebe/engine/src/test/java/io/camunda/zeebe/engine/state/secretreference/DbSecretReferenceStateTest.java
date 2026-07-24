/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.secretreference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public final class DbSecretReferenceStateTest {

  private MutableProcessingState processingState;

  private MutableSecretReferenceState state;

  @BeforeEach
  public void setUp() {
    state = processingState.getSecretReferenceState();
  }

  @Test
  public void shouldReturnFalseForNonExistentPendingSecretReference() {
    // given / when / then
    assertThat(state.isPending("storeA", "secret1")).isFalse();
  }

  @Test
  public void shouldAddAndReturnPendingSecretReference() {
    // given
    final String storeId = "storeA";
    final String secretRef = "secret1";

    // when
    state.addPendingSecretReference(storeId, secretRef);

    // then
    assertThat(state.isPending(storeId, secretRef)).isTrue();
  }

  @Test
  public void shouldRemovePendingSecretReference() {
    // given
    final String storeId = "storeA";
    final String secretRef = "secret1";
    state.addPendingSecretReference(storeId, secretRef);

    // when
    state.removePendingSecretReference(storeId, secretRef);

    // then
    assertThat(state.isPending(storeId, secretRef)).isFalse();
  }

  @Test
  public void shouldAddWaitingJobAndVisitBySecretReference() {
    // given
    final String storeId = "storeA";
    final String secretRef = "secret1";
    final long jobKey = 42L;

    // when
    state.addPendingSecretReference(storeId, secretRef);
    state.addWaitingJob(storeId, secretRef, jobKey);

    // then
    final List<Long> visitedJobs = new ArrayList<>();
    state.visitJobsBySecretReference(
        storeId,
        secretRef,
        key -> {
          visitedJobs.add(key);
          return true;
        });
    assertThat(visitedJobs).containsExactly(jobKey);
  }

  @Test
  public void shouldAddWaitingJobAndVisitByJobKey() {
    // given
    final String storeId = "storeA";
    final String secretRef = "secret1";
    final long jobKey = 42L;

    // when
    state.addPendingSecretReference(storeId, secretRef);
    state.addWaitingJob(storeId, secretRef, jobKey);

    // then
    final List<String> visitedStoreIds = new ArrayList<>();
    final List<String> visitedSecretRefs = new ArrayList<>();
    state.visitSecretReferencesByJob(
        jobKey,
        (sid, sref) -> {
          visitedStoreIds.add(sid);
          visitedSecretRefs.add(sref);
          return true;
        });
    assertThat(visitedStoreIds).containsExactly(storeId);
    assertThat(visitedSecretRefs).containsExactly(secretRef);
  }

  @Test
  public void shouldRemoveWaitingJobFromBothIndexes() {
    // given
    final String storeId = "storeA";
    final String secretRef = "secret1";
    final long jobKey = 42L;
    state.addPendingSecretReference(storeId, secretRef);
    state.addWaitingJob(storeId, secretRef, jobKey);

    // when
    state.removeWaitingJob(storeId, secretRef, jobKey);

    // then
    final List<Long> visitedBySecret = new ArrayList<>();
    state.visitJobsBySecretReference(
        storeId,
        secretRef,
        key -> {
          visitedBySecret.add(key);
          return true;
        });
    assertThat(visitedBySecret).isEmpty();

    final List<String> visitedByJob = new ArrayList<>();
    state.visitSecretReferencesByJob(
        jobKey,
        (sid, sref) -> {
          visitedByJob.add(sid);
          return true;
        });
    assertThat(visitedByJob).isEmpty();
  }

  @Test
  public void shouldRejectWaitingJobWithoutPendingSecretReference() {
    // given — no addPendingSecretReference call
    final String storeId = "storeA";
    final String secretRef = "secret1";

    // when / then — FK constraint must fire
    assertThatThrownBy(() -> state.addWaitingJob(storeId, secretRef, 42L))
        .isInstanceOf(ZeebeDbInconsistentException.class);
  }

  @Test
  public void shouldNotVisitOtherSecretReferencesWhenPrefixing() {
    // given
    final String storeId = "storeA";
    final String secretRef1 = "secret1";
    final String secretRef2 = "secret2";
    final long jobKey1 = 10L;
    final long jobKey2 = 20L;
    state.addPendingSecretReference(storeId, secretRef1);
    state.addPendingSecretReference(storeId, secretRef2);
    state.addWaitingJob(storeId, secretRef1, jobKey1);
    state.addWaitingJob(storeId, secretRef2, jobKey2);

    // when visiting only secretRef1
    final List<Long> visitedJobs = new ArrayList<>();
    state.visitJobsBySecretReference(
        storeId,
        secretRef1,
        key -> {
          visitedJobs.add(key);
          return true;
        });

    // then only jobKey1 is returned, not jobKey2
    assertThat(visitedJobs).containsExactly(jobKey1);
  }

  @Test
  public void shouldVisitMultipleJobsWaitingOnSameSecretReference() {
    // given
    final String storeId = "storeA";
    final String secretRef = "secret1";
    final long jobKey1 = 10L;
    final long jobKey2 = 20L;
    state.addPendingSecretReference(storeId, secretRef);
    state.addWaitingJob(storeId, secretRef, jobKey1);
    state.addWaitingJob(storeId, secretRef, jobKey2);

    // when
    final List<Long> visitedJobs = new ArrayList<>();
    state.visitJobsBySecretReference(
        storeId,
        secretRef,
        key -> {
          visitedJobs.add(key);
          return true;
        });

    // then both jobs are returned
    assertThat(visitedJobs).containsExactlyInAnyOrder(jobKey1, jobKey2);
  }

  @Test
  public void shouldVisitMultipleSecretReferencesForSameJob() {
    // given
    final String storeId = "storeA";
    final String secretRef1 = "secret1";
    final String secretRef2 = "secret2";
    final long jobKey = 42L;
    state.addPendingSecretReference(storeId, secretRef1);
    state.addPendingSecretReference(storeId, secretRef2);
    state.addWaitingJob(storeId, secretRef1, jobKey);
    state.addWaitingJob(storeId, secretRef2, jobKey);

    // when
    final List<String> visitedSecretRefs = new ArrayList<>();
    state.visitSecretReferencesByJob(
        jobKey,
        (sid, sref) -> {
          visitedSecretRefs.add(sref);
          return true;
        });

    // then both secret references are returned
    assertThat(visitedSecretRefs).containsExactlyInAnyOrder(secretRef1, secretRef2);
  }

  @Test
  public void shouldNotVisitSecretReferencesForOtherJob() {
    // given
    final String storeId = "storeA";
    final String secretRef = "secret1";
    final long jobKey1 = 10L;
    final long jobKey2 = 20L;
    state.addPendingSecretReference(storeId, secretRef);
    state.addWaitingJob(storeId, secretRef, jobKey1);
    state.addWaitingJob(storeId, secretRef, jobKey2);

    // when visiting secret references for jobKey1 only
    final List<String> visitedSecretRefs = new ArrayList<>();
    state.visitSecretReferencesByJob(
        jobKey1,
        (sid, sref) -> {
          visitedSecretRefs.add(sref);
          return true;
        });

    // then only the secret reference for jobKey1 is returned, not jobKey2
    assertThat(visitedSecretRefs).containsExactly(secretRef);
  }

  @Test
  void shouldVisitAllPendingSecretReferences() {
    // given
    state.addPendingSecretReference("store-a", "ref-1");
    state.addPendingSecretReference("store-a", "ref-2");
    state.addPendingSecretReference("store-b", "ref-3");

    // when
    final var collected = new ArrayList<String>();
    state.visitPendingSecretReferences(
        (storeId, secretRef) -> collected.add(storeId + ":" + secretRef));

    // then
    assertThat(collected)
        .containsExactlyInAnyOrder("store-a:ref-1", "store-a:ref-2", "store-b:ref-3");
  }

  @Test
  void shouldNotVisitRemovedPendingSecretReferences() {
    // given
    state.addPendingSecretReference("store-a", "ref-1");
    state.addPendingSecretReference("store-a", "ref-2");
    state.removePendingSecretReference("store-a", "ref-1");

    // when
    final var collected = new ArrayList<String>();
    state.visitPendingSecretReferences(
        (storeId, secretRef) -> collected.add(storeId + ":" + secretRef));

    // then
    assertThat(collected).containsExactly("store-a:ref-2");
  }

  @Test
  public void shouldPreserveRemainingEntryWhenOneWaitingJobIsRemoved() {
    // given — two jobs waiting on the same secret; remove one, the other must remain
    final String storeId = "storeA";
    final String secretRef = "secret1";
    final long jobKey1 = 10L;
    final long jobKey2 = 20L;
    state.addPendingSecretReference(storeId, secretRef);
    state.addWaitingJob(storeId, secretRef, jobKey1);
    state.addWaitingJob(storeId, secretRef, jobKey2);

    // when
    state.removeWaitingJob(storeId, secretRef, jobKey1);

    // then jobKey2 is still indexed under the secret reference
    final List<Long> visitedBySecret = new ArrayList<>();
    state.visitJobsBySecretReference(
        storeId,
        secretRef,
        key -> {
          visitedBySecret.add(key);
          return true;
        });
    assertThat(visitedBySecret).containsExactly(jobKey2);

    // and jobKey1 no longer appears in the by-job index
    final List<String> visitedByJob1 = new ArrayList<>();
    state.visitSecretReferencesByJob(
        jobKey1,
        (sid, sref) -> {
          visitedByJob1.add(sref);
          return true;
        });
    assertThat(visitedByJob1).isEmpty();
  }
}
