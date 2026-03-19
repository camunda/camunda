/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.broker.offset.OffsetStore;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotException;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link SnapshotManager}.
 *
 * <p>Uses a real {@link ActorScheduler} with one CPU thread to exercise the actor dispatch path.
 * {@link ConstructableSnapshotStore}, {@link TransientSnapshot}, and {@link PersistedSnapshot} are
 * mocked with Mockito to avoid filesystem overhead for most test cases.
 */
class SnapshotManagerTest {

  private static final int PARTITION_ID = 0;
  private static final int INTERVAL = 3; // snapshot every 3 batches

  private ActorScheduler scheduler;
  private ConstructableSnapshotStore snapshotStore;
  private OffsetStore offsetStore;
  private SnapshotManager manager;

  @BeforeEach
  void setUp() {
    scheduler =
        ActorScheduler.newActorScheduler()
            .setSchedulerName("test-scheduler")
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(1)
            .build();
    scheduler.start();

    snapshotStore = mock(ConstructableSnapshotStore.class);
    offsetStore = new OffsetStore();
    manager = new SnapshotManager(PARTITION_ID, INTERVAL, snapshotStore, offsetStore);
    scheduler.submitActor(manager).join();
  }

  @AfterEach
  void tearDown() throws Exception {
    manager.closeAsync().join();
    scheduler.close();
  }

  // -------------------------------------------------------------------------
  // notifyBatchWritten — counter and threshold behaviour

  @Nested
  class NotifyBatchWritten {

    @Test
    void shouldNotTriggerSnapshotBeforeThreshold() {
      // given — INTERVAL = 3; notify two batches (below threshold)

      // when
      manager.notifyBatchWritten(100L).join();
      manager.notifyBatchWritten(200L).join();

      // then — snapshotStore never called
      verify(snapshotStore, never())
          .newTransientSnapshot(anyLong(), anyLong(), anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void shouldTriggerSnapshotWhenThresholdIsReached(@TempDir final Path tmpDir) {
      // given — set up a successful snapshot flow
      stubSuccessfulSnapshot(tmpDir);

      // when — INTERVAL = 3; push exactly 3 batches
      manager.notifyBatchWritten(100L).join();
      manager.notifyBatchWritten(200L).join();
      manager.notifyBatchWritten(300L).join();

      // give the actor a moment to process the async callbacks
      manager.notifyBatchWritten(999L).join();

      // then — one snapshot initiated
      verify(snapshotStore, times(1))
          .newTransientSnapshot(anyLong(), anyLong(), anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void shouldPassLastLogPositionAsSnapshotIndex(@TempDir final Path tmpDir) {
      // given
      stubSuccessfulSnapshot(tmpDir);

      // when
      manager.notifyBatchWritten(100L).join();
      manager.notifyBatchWritten(200L).join();
      manager.notifyBatchWritten(300L).join();

      // give the actor a moment to process callbacks
      manager.notifyBatchWritten(999L).join();

      // then — snapshot index matches the last reported log position (300)
      verify(snapshotStore)
          .newTransientSnapshot(eq(300L), anyLong(), anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void shouldResetCounterAfterSuccessfulSnapshot(@TempDir final Path tmpDir) {
      // given
      stubSuccessfulSnapshot(tmpDir);

      // when — first threshold (batches 1–3) and second threshold (batches 4–6)
      for (int i = 1; i <= 6; i++) {
        manager.notifyBatchWritten((long) i * 100).join();
      }

      // give the actor time to finish both snapshots
      manager.notifyBatchWritten(999L).join();

      // then — two distinct snapshots taken
      verify(snapshotStore, times(2))
          .newTransientSnapshot(anyLong(), anyLong(), anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void shouldNotInitiateNewSnapshotWhileOneIsInProgress() {
      // given — a snapshot is in progress; the take() future is deliberately not completed
      final var takeFuture = new CompletableActorFuture<Void>();
      final var transientSnapshot = mock(TransientSnapshot.class);
      when(transientSnapshot.take(any())).thenReturn(takeFuture);

      when(snapshotStore.newTransientSnapshot(
              anyLong(), anyLong(), anyLong(), anyLong(), anyBoolean()))
          .thenReturn(Either.right(transientSnapshot));

      // trigger the first snapshot
      manager.notifyBatchWritten(100L).join();
      manager.notifyBatchWritten(200L).join();
      manager.notifyBatchWritten(300L).join();

      // push 3 more batches; because the first snapshot is still in-progress, no second trigger
      manager.notifyBatchWritten(400L).join();
      manager.notifyBatchWritten(500L).join();
      manager.notifyBatchWritten(600L).join();

      // then — newTransientSnapshot called exactly once
      verify(snapshotStore, times(1))
          .newTransientSnapshot(anyLong(), anyLong(), anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void shouldHandleSnapshotAlreadyExistsGracefully(@TempDir final Path tmpDir) {
      // given — first call returns SnapshotAlreadyExistsException; second succeeds
      final var alreadyExists =
          new SnapshotException.SnapshotAlreadyExistsException("already exists");
      when(snapshotStore.newTransientSnapshot(
              anyLong(), anyLong(), anyLong(), anyLong(), anyBoolean()))
          .thenReturn(Either.left(alreadyExists))
          .thenAnswer(inv -> Either.right(buildSuccessTransientSnapshot(tmpDir)));

      // when — first threshold
      manager.notifyBatchWritten(100L).join();
      manager.notifyBatchWritten(200L).join();
      manager.notifyBatchWritten(300L).join();

      // trigger second threshold (counter reset after SnapshotAlreadyExists)
      manager.notifyBatchWritten(400L).join();
      manager.notifyBatchWritten(500L).join();
      manager.notifyBatchWritten(600L).join();

      // give the actor time to finish
      manager.notifyBatchWritten(999L).join();

      // then — two attempts total
      verify(snapshotStore, times(2))
          .newTransientSnapshot(anyLong(), anyLong(), anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void shouldAbortTransientSnapshotWhenTakeFails() {
      // given — take() completes exceptionally
      final var abortFuture = CompletableActorFuture.<Void>completed(null);
      final var transientSnapshot = mock(TransientSnapshot.class);
      when(transientSnapshot.take(any()))
          .thenReturn(
              CompletableActorFuture.completedExceptionally(new RuntimeException("disk full")));
      when(transientSnapshot.abort()).thenReturn(abortFuture);

      when(snapshotStore.newTransientSnapshot(
              anyLong(), anyLong(), anyLong(), anyLong(), anyBoolean()))
          .thenReturn(Either.right(transientSnapshot));

      // when — reach threshold
      manager.notifyBatchWritten(100L).join();
      manager.notifyBatchWritten(200L).join();
      manager.notifyBatchWritten(300L).join();

      // give the actor a moment to process the async callbacks
      manager.notifyBatchWritten(999L).join();

      // then — abort was called
      verify(transientSnapshot, atLeastOnce()).abort();
      // persist must NOT be called when take failed
      verify(transientSnapshot, never()).persist();
    }

    @Test
    void shouldWriteOffsetStateToSnapshotDirectory(@TempDir final Path tmpDir) throws IOException {
      // given — offset committed before snapshot
      offsetStore.commit("g1", "c1", PARTITION_ID, 42L);
      stubSuccessfulSnapshot(tmpDir);

      // when
      manager.notifyBatchWritten(100L).join();
      manager.notifyBatchWritten(200L).join();
      manager.notifyBatchWritten(300L).join();

      // give the actor time to complete persist
      manager.notifyBatchWritten(999L).join();

      // then — offsets.sbe file created in the snapshot directory
      final Path offsetFile = tmpDir.resolve("offsets.sbe");
      assertThat(offsetFile).exists();
      assertThat(Files.size(offsetFile)).isGreaterThan(0L);

      // and — the file deserializes back to the committed offset
      final var restored = new OffsetStore();
      restored.loadFromDirectory(PARTITION_ID, tmpDir);
      assertThat(restored.getCommittedOffset("g1", "c1", PARTITION_ID)).isEqualTo(42L);
    }
  }

  // -------------------------------------------------------------------------
  // setCurrentTerm

  @Nested
  class SetCurrentTerm {

    @Test
    void shouldUseUpdatedTermInSnapshotId(@TempDir final Path tmpDir) {
      // given
      stubSuccessfulSnapshot(tmpDir);

      // when — set term and wait for it to land before pushing batches
      manager.setCurrentTerm(7L).join();
      manager.notifyBatchWritten(100L).join();
      manager.notifyBatchWritten(200L).join();
      manager.notifyBatchWritten(300L).join();

      // give actor time to process the snapshot callbacks
      manager.notifyBatchWritten(999L).join();

      // then — snapshot was created with term 7 (term update completed before threshold was hit)
      verify(snapshotStore, times(1))
          .newTransientSnapshot(anyLong(), eq(7L), anyLong(), anyLong(), anyBoolean());
    }
  }

  // -------------------------------------------------------------------------
  // loadLatestSnapshot — recovery path

  @Nested
  class LoadLatestSnapshot {

    @Test
    void shouldNoOpWhenNoSnapshotExists() {
      // given
      when(snapshotStore.getLatestSnapshot()).thenReturn(Optional.empty());

      // when — must not throw
      manager.loadLatestSnapshot();

      // then — store is still empty
      assertThat(offsetStore.getAllEntries()).isEmpty();
    }

    @Test
    void shouldRestoreOffsetStateFromLatestSnapshot(@TempDir final Path tmpDir) throws IOException {
      // given — write an offset snapshot file to tmpDir
      final var src = new OffsetStore();
      src.commit("g1", "c1", PARTITION_ID, 77L);
      src.saveToDirectory(PARTITION_ID, tmpDir);

      final var persistedSnapshot = mock(PersistedSnapshot.class);
      when(persistedSnapshot.getPath()).thenReturn(tmpDir);
      when(persistedSnapshot.getIndex()).thenReturn(42L);
      when(snapshotStore.getLatestSnapshot()).thenReturn(Optional.of(persistedSnapshot));

      // when
      manager.loadLatestSnapshot();

      // then
      assertThat(offsetStore.getCommittedOffset("g1", "c1", PARTITION_ID)).isEqualTo(77L);
    }

    @Test
    void shouldRestoreMultipleOffsetsFromLatestSnapshot(@TempDir final Path tmpDir)
        throws IOException {
      // given
      final var src = new OffsetStore();
      src.commit("g1", "c1", PARTITION_ID, 100L);
      src.commit("g1", "c2", PARTITION_ID, 200L);
      src.commit("g2", "c1", PARTITION_ID, 300L);
      src.saveToDirectory(PARTITION_ID, tmpDir);

      final var persistedSnapshot = mock(PersistedSnapshot.class);
      when(persistedSnapshot.getPath()).thenReturn(tmpDir);
      when(persistedSnapshot.getIndex()).thenReturn(10L);
      when(snapshotStore.getLatestSnapshot()).thenReturn(Optional.of(persistedSnapshot));

      // when
      manager.loadLatestSnapshot();

      // then
      assertThat(offsetStore.getCommittedOffset("g1", "c1", PARTITION_ID)).isEqualTo(100L);
      assertThat(offsetStore.getCommittedOffset("g1", "c2", PARTITION_ID)).isEqualTo(200L);
      assertThat(offsetStore.getCommittedOffset("g2", "c1", PARTITION_ID)).isEqualTo(300L);
    }

    @Test
    void shouldHandleMissingOffsetFileGracefully(@TempDir final Path tmpDir) {
      // given — tmpDir exists but contains no offsets.sbe file
      final var persistedSnapshot = mock(PersistedSnapshot.class);
      when(persistedSnapshot.getPath()).thenReturn(tmpDir);
      when(persistedSnapshot.getIndex()).thenReturn(5L);
      when(snapshotStore.getLatestSnapshot()).thenReturn(Optional.of(persistedSnapshot));

      // when — must not throw
      manager.loadLatestSnapshot();

      // then — store remains empty
      assertThat(offsetStore.getAllEntries()).isEmpty();
    }

    @Test
    void shouldRestoreLastLogPositionFromLatestSnapshotIndex(@TempDir final Path tmpDir)
        throws IOException {
      // given — snapshot at index 99
      final var src = new OffsetStore();
      src.commit("g1", "c1", PARTITION_ID, 1L);
      src.saveToDirectory(PARTITION_ID, tmpDir);

      final var persistedSnapshot = mock(PersistedSnapshot.class);
      when(persistedSnapshot.getPath()).thenReturn(tmpDir);
      when(persistedSnapshot.getIndex()).thenReturn(99L);
      when(snapshotStore.getLatestSnapshot()).thenReturn(Optional.of(persistedSnapshot));

      manager.loadLatestSnapshot();

      // when — trigger INTERVAL batches from the restored position
      stubSuccessfulSnapshot(tmpDir);
      manager.notifyBatchWritten(100L).join();
      manager.notifyBatchWritten(200L).join();
      manager.notifyBatchWritten(300L).join();

      // give the actor time to finish
      manager.notifyBatchWritten(999L).join();

      // then — snapshot was triggered (confirms the actor is alive and counter advanced correctly)
      verify(snapshotStore, atLeastOnce())
          .newTransientSnapshot(anyLong(), anyLong(), anyLong(), anyLong(), anyBoolean());
    }
  }

  // -------------------------------------------------------------------------
  // Helpers

  /**
   * Stubs the snapshot store to return a successful {@link TransientSnapshot} that writes to {@code
   * tmpDir} and persists successfully.
   */
  private void stubSuccessfulSnapshot(final Path tmpDir) {
    when(snapshotStore.newTransientSnapshot(
            anyLong(), anyLong(), anyLong(), anyLong(), anyBoolean()))
        .thenAnswer(inv -> Either.right(buildSuccessTransientSnapshot(tmpDir)));
  }

  /**
   * Builds a {@link TransientSnapshot} mock that executes the {@code take()} callback (writing
   * offset state to {@code tmpDir}) and returns a completed persist future.
   */
  private TransientSnapshot buildSuccessTransientSnapshot(final Path tmpDir) {
    final var persistedSnapshot = mock(PersistedSnapshot.class);
    when(persistedSnapshot.getId()).thenReturn("snap-" + System.nanoTime());

    final var transientSnapshot = mock(TransientSnapshot.class);

    // Execute the Consumer<Path> callback synchronously inside take()
    when(transientSnapshot.take(any()))
        .thenAnswer(
            inv -> {
              final Consumer<Path> consumer = inv.getArgument(0);
              consumer.accept(tmpDir);
              return CompletableActorFuture.<Void>completed(null);
            });

    when(transientSnapshot.persist())
        .thenReturn(CompletableActorFuture.completed(persistedSnapshot));

    return transientSnapshot;
  }
}
