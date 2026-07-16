/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.secretreference;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class DbSecretReferenceStateTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableSecretReferenceState state;

  @Before
  public void setUp() {
    state = stateRule.getProcessingState().getSecretReferenceState();
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
    state.visitJobsBySecretReference(storeId, secretRef, visitedJobs::add);
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
    state.visitJobsBySecretReference(storeId, secretRef, visitedBySecret::add);
    assertThat(visitedBySecret).isEmpty();

    final List<String> visitedByJob = new ArrayList<>();
    state.visitSecretReferencesByJob(jobKey, (sid, sref) -> visitedByJob.add(sid));
    assertThat(visitedByJob).isEmpty();
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
    state.visitJobsBySecretReference(storeId, secretRef1, visitedJobs::add);

    // then only jobKey1 is returned, not jobKey2
    assertThat(visitedJobs).containsExactly(jobKey1);
  }
}
