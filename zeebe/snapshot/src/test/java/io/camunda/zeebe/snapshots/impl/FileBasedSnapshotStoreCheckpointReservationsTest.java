/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.scheduler.testing.ActorSchedulerExtension;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotNotFoundException;
import io.camunda.zeebe.snapshots.SnapshotFilesInfo;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

final class FileBasedSnapshotStoreCheckpointReservationsTest {

  private static final long CHECKPOINT_ID = 10L;
  private static final long OTHER_CHECKPOINT_ID = 11L;

  @RegisterExtension final ActorSchedulerExtension actorScheduler = new ActorSchedulerExtension();

  @TempDir Path root;
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private FileBasedSnapshotStore store;

  @BeforeEach
  void setUp() {
    store = new FileBasedSnapshotStore(0, 1, root, path -> SnapshotFilesInfo.none(), meterRegistry);
    actorScheduler.submitActor(store).join();
  }

  @Test
  void shouldReturnEmptyWhenNoSnapshotToReserve() {
    // when
    final var reserved = store.reserveLatestSnapshot(CHECKPOINT_ID).join();

    // then
    assertThat(reserved).isEmpty();
  }

  @Test
  void shouldReserveLatestSnapshot() {
    // given
    persistSnapshot(1);
    final var latest = persistSnapshot(2);

    // when
    final var reserved = store.reserveLatestSnapshot(CHECKPOINT_ID).join();

    // then
    assertThat(reserved).hasValue(latest.getId());
    assertThat(store.getReservedSnapshot(CHECKPOINT_ID, latest.getId()).join()).hasValue(latest);
  }

  @Test
  void shouldKeepReservedLatestSnapshotWhenNewerSnapshotIsPersisted() {
    // given
    final var reservedSnapshot = persistSnapshot(1);
    store.reserveLatestSnapshot(CHECKPOINT_ID).join();

    // when
    persistSnapshot(2);

    // then
    assertThat(reservedSnapshot.getPath()).exists();
    assertThat(store.getReservedSnapshot(CHECKPOINT_ID, reservedSnapshot.getId()).join())
        .hasValue(reservedSnapshot);
  }

  @Test
  void shouldReserveSnapshotById() {
    // given
    final var snapshot = persistSnapshot(1);

    // when
    store.reserveSnapshot(CHECKPOINT_ID, snapshot.getId()).join();
    persistSnapshot(2);

    // then
    assertThat(snapshot.getPath()).exists();
    assertThat(store.getReservedSnapshot(CHECKPOINT_ID, snapshot.getId()).join())
        .hasValue(snapshot);
  }

  @Test
  void shouldNotReserveUnknownSnapshot() {
    // given
    final var deleted = persistSnapshot(1);
    persistSnapshot(2);

    // when
    final var reserved = store.reserveSnapshot(CHECKPOINT_ID, deleted.getId());

    // then
    assertThat(reserved)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(SnapshotNotFoundException.class)
        .withMessageContaining(deleted.getId());
    assertThat(store.getReservedSnapshot(CHECKPOINT_ID, deleted.getId()).join()).isEmpty();
  }

  @Test
  void shouldNotReturnSnapshotThatIsNotReserved() {
    // given
    final var snapshot = persistSnapshot(1);

    // when
    final var reserved = store.getReservedSnapshot(CHECKPOINT_ID, snapshot.getId()).join();

    // then
    assertThat(reserved).isEmpty();
  }

  @Test
  void shouldDeleteSnapshotOnceReleasedAndNewerSnapshotIsPersisted() {
    // given
    final var snapshot = persistSnapshot(1);
    store.reserveLatestSnapshot(CHECKPOINT_ID).join();

    // when
    store.releaseReservation(CHECKPOINT_ID, snapshot.getId());
    persistSnapshot(2);

    // then
    assertThat(snapshot.getPath()).doesNotExist();
    assertThat(store.getReservedSnapshot(CHECKPOINT_ID, snapshot.getId()).join()).isEmpty();
  }

  @Test
  void shouldShareReservationOfSameCheckpointAndSnapshot() {
    // given
    final var snapshot = persistSnapshot(1);
    store.reserveLatestSnapshot(CHECKPOINT_ID).join();
    store.reserveSnapshot(CHECKPOINT_ID, snapshot.getId()).join();

    // when
    store.releaseReservation(CHECKPOINT_ID, snapshot.getId());
    persistSnapshot(2);

    // then
    assertThat(snapshot.getPath()).doesNotExist();
    assertThat(store.getReservedSnapshot(CHECKPOINT_ID, snapshot.getId()).join()).isEmpty();
  }

  @Test
  void shouldIgnoreReleaseOfSnapshotWithoutReservation() {
    // given
    final var snapshot = persistSnapshot(1);

    // when
    store.releaseReservation(CHECKPOINT_ID, snapshot.getId());
    store.releaseReservation(CHECKPOINT_ID, "unknown");

    // then
    assertThat(store.getReservedSnapshot(CHECKPOINT_ID, snapshot.getId()).join()).isEmpty();
  }

  @Test
  void shouldReleaseAllReservations() {
    // given
    final var first = persistSnapshot(1);
    store.reserveLatestSnapshot(CHECKPOINT_ID).join();
    store.reserveLatestSnapshot(CHECKPOINT_ID).join();
    final var second = persistSnapshot(2);
    store.reserveLatestSnapshot(CHECKPOINT_ID).join();

    // when
    store.releaseAllReservations();
    persistSnapshot(3);

    // then
    assertThat(first.getPath()).doesNotExist();
    assertThat(second.getPath()).doesNotExist();
    assertThat(store.getReservedSnapshot(CHECKPOINT_ID, first.getId()).join()).isEmpty();
    assertThat(store.getReservedSnapshot(CHECKPOINT_ID, second.getId()).join()).isEmpty();
  }

  @Test
  void shouldNotReturnSnapshotReservedForAnotherCheckpoint() {
    // given
    final var snapshot = persistSnapshot(1);
    store.reserveLatestSnapshot(OTHER_CHECKPOINT_ID).join();

    // when
    final var reserved = store.getReservedSnapshot(CHECKPOINT_ID, snapshot.getId()).join();

    // then
    assertThat(reserved).isEmpty();
  }

  @Test
  void shouldKeepReservationOfAnotherCheckpointWhenReleasingSameSnapshot() {
    // given - a checkpoint whose reservation was already dropped, e.g. on a role change, and a
    // newer
    // checkpoint that reserved the same snapshot
    final var snapshot = persistSnapshot(1);
    store.reserveLatestSnapshot(CHECKPOINT_ID).join();
    store.releaseAllReservations();
    store.reserveLatestSnapshot(OTHER_CHECKPOINT_ID).join();

    // when
    store.releaseReservation(CHECKPOINT_ID, snapshot.getId());
    persistSnapshot(2);

    // then
    assertThat(snapshot.getPath()).exists();
    assertThat(store.getReservedSnapshot(OTHER_CHECKPOINT_ID, snapshot.getId()).join())
        .hasValue(snapshot);
  }

  private PersistedSnapshot persistSnapshot(final long index) {
    final var transientSnapshot = store.newTransientSnapshot(index, 1, index, 0, false).get();
    transientSnapshot.take(
        path -> {
          try {
            Files.createDirectories(path);
            Files.writeString(path.resolve("file"), "content");
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        });
    return transientSnapshot.persist().join();
  }
}
