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
    final var reserved = store.reserveLatestSnapshot().join();

    // then
    assertThat(reserved).isEmpty();
  }

  @Test
  void shouldReserveLatestSnapshot() {
    // given
    persistSnapshot(1);
    final var latest = persistSnapshot(2);

    // when
    final var reserved = store.reserveLatestSnapshot().join();

    // then
    assertThat(reserved).hasValue(latest.getId());
    assertThat(store.getReservedSnapshot(latest.getId()).join()).hasValue(latest);
  }

  @Test
  void shouldKeepReservedLatestSnapshotWhenNewerSnapshotIsPersisted() {
    // given
    final var reservedSnapshot = persistSnapshot(1);
    store.reserveLatestSnapshot().join();

    // when
    persistSnapshot(2);

    // then
    assertThat(reservedSnapshot.getPath()).exists();
    assertThat(store.getReservedSnapshot(reservedSnapshot.getId()).join())
        .hasValue(reservedSnapshot);
  }

  @Test
  void shouldReserveSnapshotById() {
    // given
    final var snapshot = persistSnapshot(1);

    // when
    store.reserveSnapshot(snapshot.getId()).join();
    persistSnapshot(2);

    // then
    assertThat(snapshot.getPath()).exists();
    assertThat(store.getReservedSnapshot(snapshot.getId()).join()).hasValue(snapshot);
  }

  @Test
  void shouldNotReserveUnknownSnapshot() {
    // given
    final var deleted = persistSnapshot(1);
    persistSnapshot(2);

    // when
    final var reserved = store.reserveSnapshot(deleted.getId());

    // then
    assertThat(reserved)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(SnapshotNotFoundException.class)
        .withMessageContaining(deleted.getId());
    assertThat(store.getReservedSnapshot(deleted.getId()).join()).isEmpty();
  }

  @Test
  void shouldNotReturnSnapshotThatIsNotReserved() {
    // given
    final var snapshot = persistSnapshot(1);

    // when
    final var reserved = store.getReservedSnapshot(snapshot.getId()).join();

    // then
    assertThat(reserved).isEmpty();
  }

  @Test
  void shouldDeleteSnapshotOnceReleasedAndNewerSnapshotIsPersisted() {
    // given
    final var snapshot = persistSnapshot(1);
    store.reserveLatestSnapshot().join();

    // when
    store.releaseReservation(snapshot.getId()).join();
    persistSnapshot(2);

    // then
    assertThat(snapshot.getPath()).doesNotExist();
    assertThat(store.getReservedSnapshot(snapshot.getId()).join()).isEmpty();
  }

  @Test
  void shouldKeepSnapshotUntilEveryReservationIsReleased() {
    // given
    final var snapshot = persistSnapshot(1);
    store.reserveLatestSnapshot().join();
    store.reserveSnapshot(snapshot.getId()).join();

    // when
    store.releaseReservation(snapshot.getId()).join();
    persistSnapshot(2);

    // then
    assertThat(snapshot.getPath()).exists();
    assertThat(store.getReservedSnapshot(snapshot.getId()).join()).hasValue(snapshot);
  }

  @Test
  void shouldIgnoreReleaseOfSnapshotWithoutReservation() {
    // given
    final var snapshot = persistSnapshot(1);

    // when
    store.releaseReservation(snapshot.getId()).join();
    store.releaseReservation("unknown").join();

    // then
    assertThat(store.getReservedSnapshot(snapshot.getId()).join()).isEmpty();
  }

  @Test
  void shouldReleaseAllReservations() {
    // given
    final var first = persistSnapshot(1);
    store.reserveLatestSnapshot().join();
    store.reserveLatestSnapshot().join();
    final var second = persistSnapshot(2);
    store.reserveLatestSnapshot().join();

    // when
    store.releaseAllReservations().join();
    persistSnapshot(3);

    // then
    assertThat(first.getPath()).doesNotExist();
    assertThat(second.getPath()).doesNotExist();
    assertThat(store.getReservedSnapshot(first.getId()).join()).isEmpty();
    assertThat(store.getReservedSnapshot(second.getId()).join()).isEmpty();
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
