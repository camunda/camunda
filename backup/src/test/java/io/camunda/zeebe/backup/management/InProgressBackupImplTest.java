/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotNotFoundException;
import io.camunda.zeebe.snapshots.SnapshotMetadata;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InProgressBackupImplTest {

  @Mock PersistedSnapshotStore snapshotStore;

  InProgressBackupImpl inProgressBackup;
  private final TestConcurrencyControl concurrencyControl = new TestConcurrencyControl();

  @BeforeEach
  void setup() {
    inProgressBackup =
        new InProgressBackupImpl(
            snapshotStore, new BackupIdentifierImpl(1, 1, 1), 10, 1, concurrencyControl);
  }

  @Test
  void shouldCompleteFutureNoSnapshotExists() {
    // given
    when(snapshotStore.getAvailableSnapshots())
        .thenReturn(TestActorFuture.completedFuture(Set.of()));

    // when
    final var future = inProgressBackup.findValidSnapshot();

    // then
    assertThat(future).succeedsWithin(Duration.ofMillis(100));
  }

  @Test
  void shouldCompleteFutureWhenValidSnapshotFound() {
    // given
    final var validSnapshot = snapshotWith(1L, 5L);
    final var invalidSnapshot = snapshotWith(8L, 20L);
    final Set<PersistedSnapshot> snapshots = Set.of(validSnapshot, invalidSnapshot);
    when(snapshotStore.getAvailableSnapshots())
        .thenReturn(TestActorFuture.completedFuture(snapshots));

    // when
    final var future = inProgressBackup.findValidSnapshot();

    // then
    assertThat(future).succeedsWithin(Duration.ofMillis(100));
  }

  @Test
  void shouldFailFutureWhenSnapshotIsPastCheckpointPosition() {
    // given - checkpointPosition < processedPosition <  followupPosition
    final var invalidSnapshot = snapshotWith(11L, 20L);
    final Set<PersistedSnapshot> snapshots = Set.of(invalidSnapshot);
    when(snapshotStore.getAvailableSnapshots())
        .thenReturn(TestActorFuture.completedFuture(snapshots));

    // when - then
    // when
    final var future = inProgressBackup.findValidSnapshot();

    // then
    assertThat(future)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(SnapshotNotFoundException.class);
  }

  @Test
  void shouldFailFutureWhenSnapshotOverlapsWithCheckpoint() {
    // given - processedPosition < checkpointPosition < followupPosition
    final var invalidSnapshot = snapshotWith(8L, 20L);
    final Set<PersistedSnapshot> snapshots = Set.of(invalidSnapshot);
    when(snapshotStore.getAvailableSnapshots())
        .thenReturn(TestActorFuture.completedFuture(snapshots));

    // when
    final var future = inProgressBackup.findValidSnapshot();

    // then
    assertThat(future)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(SnapshotNotFoundException.class);
  }

  private PersistedSnapshot snapshotWith(
      final long processedPosition, final long followUpPosition) {
    final PersistedSnapshot snapshot = mock(PersistedSnapshot.class);
    final SnapshotMetadata snapshotMetadata = mock(SnapshotMetadata.class);
    when(snapshotMetadata.processedPosition()).thenReturn(processedPosition);
    lenient().when(snapshotMetadata.lastFollowupEventPosition()).thenReturn(followUpPosition);

    when(snapshot.getMetadata()).thenReturn(snapshotMetadata);
    return snapshot;
  }
}
