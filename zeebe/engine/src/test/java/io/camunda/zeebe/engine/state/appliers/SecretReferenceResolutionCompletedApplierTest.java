/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class SecretReferenceResolutionCompletedApplierTest {

  private MutableProcessingState processingState;
  private MutableSecretReferenceState state;
  private SecretReferenceResolutionCompletedApplier applier;

  @BeforeEach
  void setUp() {
    state = processingState.getSecretReferenceState();
    applier = new SecretReferenceResolutionCompletedApplier(state);
  }

  @Test
  void shouldRemovePendingSecretReference() {
    // given
    state.addPendingSecretReference("store-1", "secret-a");
    final var record =
        new SecretReferenceRecord().setStoreId("store-1").setSecretReference("secret-a");

    // when
    applier.applyState(100L, record);

    // then
    assertThat(state.isPending("store-1", "secret-a")).isFalse();
  }

  @Test
  void shouldLeaveWaitingJobIndexUntouched() {
    // given
    state.addPendingSecretReference("store-1", "secret-a");
    state.addWaitingJob("store-1", "secret-a", 1L);
    final var record =
        new SecretReferenceRecord().setStoreId("store-1").setSecretReference("secret-a");

    // when
    applier.applyState(100L, record);

    // then — the waiting-job index is untouched; removing waiting jobs is the
    // BATCH_JOBS_REACTIVATED applier's responsibility, not this one's
    final List<Long> waitingJobs = new ArrayList<>();
    state.visitJobsBySecretReference(
        "store-1",
        "secret-a",
        jobKey -> {
          waitingJobs.add(jobKey);
          return true;
        });
    assertThat(waitingJobs).containsExactly(1L);
  }
}
