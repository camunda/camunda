/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.camunda.zeebe.broker.system.partitions.TestIndexedRaftLogEntry;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerExtension;
import io.camunda.zeebe.snapshots.SnapshotFilesInfo;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.stream.impl.state.DbLastProcessedPositionState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.collections.MutableLong;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

final class FollowerSnapshotDirectorTest {

  @RegisterExtension
  private final ActorSchedulerExtension scheduler = new ActorSchedulerExtension();

  @TempDir private Path tempDir;

  private final ZeebeDbFactory<ZbColumnFamilies> dbFactory = DefaultZeebeDbFactory.defaultFactory();
  private final MutableLong committedPosition = new MutableLong(Long.MAX_VALUE);
  private final MutableLong lowestExportedPosition = new MutableLong(Long.MAX_VALUE);
  private final List<Long> requestedEntryPositions = new CopyOnWriteArrayList<>();
  private final AtomicBoolean hasState = new AtomicBoolean(true);
  private final AtomicBoolean stateUnreachable = new AtomicBoolean(false);

  private Path stateDirectory;
  private FileBasedSnapshotStore snapshotStore;
  private FollowerSnapshotDirector director;

  @BeforeEach
  void setUp() {
    stateDirectory = tempDir.resolve("state");
    snapshotStore =
        new FileBasedSnapshotStore(
            0,
            1,
            tempDir.resolve("snapshots"),
            snapshotPath -> SnapshotFilesInfo.none(),
            new SimpleMeterRegistry());
    scheduler.submitActor(snapshotStore).join();

    director =
        new FollowerSnapshotDirector(
            new PartitionId("raft-partition", 1),
            this::openSharedState,
            committedPosition::get,
            position -> {
              requestedEntryPositions.add(position);
              return Optional.of(
                  new TestIndexedRaftLogEntry(
                      position, 1, new SerializedApplicationEntry(1, 10, new UnsafeBuffer())));
            },
            snapshotStore,
            () -> CompletableFuture.completedFuture(null),
            db -> new FixedPositions(),
            Duration.ofHours(1));
    scheduler.submitActor(director).join();
  }

  @Test
  void shouldSkipWithoutState() {
    // given
    hasState.set(false);

    // when
    final var snapshot = director.takeSnapshot().join();

    // then
    assertThat(snapshot).isNull();
    assertThat(snapshotStore.getLatestSnapshot()).isEmpty();
  }

  @Test
  void shouldSkipWhenNothingWasProcessed() {
    // given - a state that has processed nothing yet

    // when
    final var snapshot = director.takeSnapshot().join();

    // then
    assertThat(snapshot).isNull();
    assertThat(snapshotStore.getLatestSnapshot()).isEmpty();
  }

  @Test
  void shouldTakeSnapshotAtProcessedPosition() {
    // given
    markAsProcessed(10);

    // when
    final var snapshot = director.takeSnapshot().join();

    // then
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.getMetadata().processedPosition()).isEqualTo(10);
    assertThat(requestedEntryPositions).containsExactly(10L);
    assertThat(snapshotStore.getLatestSnapshot()).contains(snapshot);
  }

  @Test
  void shouldNotSnapshotPastOwnCommittedLog() {
    // given
    markAsProcessed(10);
    committedPosition.set(5);

    // when
    final var snapshot = director.takeSnapshot().join();

    // then
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.getMetadata().processedPosition()).isEqualTo(5);
    assertThat(requestedEntryPositions).containsExactly(5L);
  }

  @Test
  void shouldKeepRecordsNotExportedYet() {
    // given
    markAsProcessed(10);
    lowestExportedPosition.set(3);

    // when
    final var snapshot = director.takeSnapshot().join();

    // then
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.getMetadata().processedPosition()).isEqualTo(10);
    assertThat(snapshot.getMetadata().minExportedPosition()).isEqualTo(3);
    assertThat(requestedEntryPositions).containsExactly(3L);
  }

  @Test
  void shouldCloseSharedStateAfterEachSnapshot() {
    // given
    markAsProcessed(10);
    director.takeSnapshot().join();

    // when - another instance can only open the state if the director closed it
    markAsProcessed(20);
    final var snapshot = director.takeSnapshot().join();

    // then
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.getMetadata().processedPosition()).isEqualTo(20);
    assertThat(snapshotStore.getLatestSnapshot()).contains(snapshot);
  }

  @Test
  void shouldSkipWhenSnapshotAlreadyExists() {
    // given
    markAsProcessed(10);
    final var existing = director.takeSnapshot().join();

    // when
    final var snapshot = director.takeSnapshot().join();

    // then
    assertThat(snapshot).isNull();
    assertThat(snapshotStore.getLatestSnapshot()).contains(existing);
    assertThat(director.getHealthReport().isHealthy()).isTrue();
  }

  @Test
  void shouldBecomeUnhealthyWhenSnapshotFails() {
    // given
    markAsProcessed(10);
    stateUnreachable.set(true);

    // when
    assertThatThrownBy(() -> director.takeSnapshot().join())
        .hasRootCauseMessage("database unreachable");

    // then
    assertThat(director.getHealthReport().isHealthy()).isFalse();
    assertThat(snapshotStore.getLatestSnapshot()).isEmpty();
  }

  @Test
  void shouldBecomeHealthyAgainOnNextSnapshot() {
    // given
    markAsProcessed(10);
    stateUnreachable.set(true);
    assertThatThrownBy(() -> director.takeSnapshot().join())
        .hasRootCauseMessage("database unreachable");

    // when
    stateUnreachable.set(false);
    final var snapshot = director.takeSnapshot().join();

    // then
    assertThat(snapshot).isNotNull();
    assertThat(director.getHealthReport().isHealthy()).isTrue();
  }

  private Optional<ZeebeDb<ZbColumnFamilies>> openSharedState() {
    if (stateUnreachable.get()) {
      throw new IllegalStateException("database unreachable");
    }
    if (!hasState.get()) {
      return Optional.empty();
    }
    return Optional.of(dbFactory.createDb(stateDirectory.toFile()));
  }

  private void markAsProcessed(final long position) {
    // flush on close, so the director sees the position when it opens the state
    try (final var db = dbFactory.createDb(stateDirectory.toFile(), false)) {
      final var context = db.createContext();
      context.runInTransaction(
          () -> new DbLastProcessedPositionState(db, context).markAsProcessed(position));
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private final class FixedPositions implements StatePositionSupplier {
    @Override
    public long getLowestExportedPosition() {
      return lowestExportedPosition.get();
    }

    @Override
    public long getHighestExportedPosition() {
      return lowestExportedPosition.get();
    }

    @Override
    public long getHighestBackupPosition() {
      return Long.MAX_VALUE;
    }
  }
}
