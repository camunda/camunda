/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.camunda.zeebe.broker.system.partitions.impl.StateControllerImpl;
import io.camunda.zeebe.broker.system.partitions.impl.ThreadSafeSnapshotDirector;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorMode;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import io.camunda.zeebe.util.sched.ActorCompatability;
import io.camunda.zeebe.util.sched.ActorScheduler;
import io.camunda.zeebe.util.sched.TestConcurrencyControl;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ThreadSafeSnapshottingTest {

  @Test
  void shouldPersistSnapshotWhenCommitted(@TempDir final Path snapshotDirectory) {
    // given
    final var testComponents = buildSnapshotDirector(snapshotDirectory);
    final var snapshotDirector = testComponents.director;
    final var snapshotStore = testComponents.store;
    final var snapshot = snapshotDirector.forceSnapshot();

    // when

    commit(snapshotDirector, 100);

    // then
    assertThat(snapshot)
        .succeedsWithin(
            Duration.ofSeconds(10), InstanceOfAssertFactories.type(PersistedSnapshot.class))
        .isNotNull()
        .isSameAs(snapshotStore.getLatestSnapshot().get());
  }

  @Test
  void shouldTakeSnapshotsOneByOne(@TempDir final Path snapshotDirectory)
      throws ExecutionException, InterruptedException {
    // given
    final var testComponents = buildSnapshotDirector(snapshotDirectory);
    final var snapshotDirector = testComponents.director;
    final var snapshotStore = testComponents.store;
    final var firstSnapshot = snapshotDirector.forceSnapshot();
    commit(snapshotDirector, 99L);
    assertThat(firstSnapshot).succeedsWithin(Duration.ofMinutes(1));

    // when
    final var secondSnapshot = snapshotDirector.forceSnapshot();
    commit(snapshotDirector, 100L);

    // then
    assertThat(secondSnapshot)
        .succeedsWithin(Duration.ofMinutes(1))
        .isNotNull()
        .extracting(PersistedSnapshot::getIndex, as(InstanceOfAssertFactories.LONG))
        .isGreaterThan(firstSnapshot.get().getIndex());
    assertThat(snapshotStore.getLatestSnapshot()).hasValue(secondSnapshot.get());
  }

  @Test
  void shouldSucceedAfterRetrievingPositionFails(@TempDir final Path snapshotDirectory)
      throws ExecutionException, InterruptedException {
    // given
    final var testComponents = buildSnapshotDirector(snapshotDirectory);
    final var snapshotDirector = testComponents.director;
    final var snapshotStore = testComponents.store;
    final var streamProcessor = testComponents.streamProcessor;
    final var initialFailure = new RuntimeException("getting position failed");

    final long lastProcessedPosition = 25L;
    final long lastWrittenPosition = 26L;
    final long commitPosition = 100L;

    when(streamProcessor.getLastProcessedPositionAsync())
        .thenReturn(CompletableActorFuture.completedExceptionally(initialFailure));
    when(streamProcessor.getLastWrittenPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastProcessedPosition));
    commit(snapshotDirector, commitPosition);
    assertThatThrownBy(() -> snapshotDirector.forceSnapshot().get()).hasRootCause(initialFailure);

    // when
    when(streamProcessor.getLastWrittenPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastWrittenPosition));

    assertThat(snapshotDirector.forceSnapshot().get()).isNotNull();
    assertThat(snapshotStore.getLatestSnapshot()).isPresent();
    verify(streamProcessor, timeout(10_000).times(2)).getLastWrittenPositionAsync();
  }

  private void commit(final SnapshotDirector snapshotDirector, final long position) {
    snapshotDirector.onCommit(
        new TestIndexedRaftLogEntry(
            position, 0, new ApplicationEntry(0L, position, new UnsafeBuffer())));
  }

  private TestComponents buildSnapshotDirector(final Path snapshotDirectory) {
    final var actorCompatability = new ActorCompatability();
    final var actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();
    actorScheduler.submitActor(actorCompatability);

    final var snapshotStoreFactory = new FileBasedSnapshotStoreFactory(actorScheduler, 0);
    snapshotStoreFactory.createReceivableSnapshotStore(snapshotDirectory, 1);
    final var snapshotStore = snapshotStoreFactory.getConstructableSnapshotStore(1);
    final var stateController =
        new StateControllerImpl(
            DefaultZeebeDbFactory.defaultFactory(),
            snapshotStore,
            snapshotDirectory.resolve("runtime"),
            l ->
                Optional.of(
                    new TestIndexedRaftLogEntry(
                        l + 100, 1, new ApplicationEntry(1, 10, new UnsafeBuffer()))),
            db -> Long.MAX_VALUE,
            new TestConcurrencyControl());
    final var result = actorCompatability.await(stateController::recover).join();

    final var streamProcessor = mock(StreamProcessor.class);
    when(streamProcessor.getLastProcessedPositionAsync())
        .thenReturn(CompletableActorFuture.completed(0L))
        .thenReturn(CompletableActorFuture.completed(25L))
        .thenReturn(CompletableActorFuture.completed(32L));
    when(streamProcessor.getLastWrittenPositionAsync())
        .thenReturn(CompletableActorFuture.completed(99L))
        .thenReturn(CompletableActorFuture.completed(100L));

    final var snapshotDirector =
        new ThreadSafeSnapshotDirector(
            actorCompatability,
            stateController,
            streamProcessor,
            StreamProcessorMode.PROCESSING,
            Duration.ofMinutes(1));
    return new TestComponents(snapshotDirector, snapshotStore, streamProcessor);
  }

  record TestComponents(
      SnapshotDirector director,
      ConstructableSnapshotStore store,
      StreamProcessor streamProcessor) {}
}
