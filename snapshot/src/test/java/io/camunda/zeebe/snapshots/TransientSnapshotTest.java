/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.snapshots.SnapshotException.SnapshotAlreadyExistsException;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotNotFoundException;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TransientSnapshotTest {
  private static final Map<String, String> SNAPSHOT_FILE_CONTENTS =
      Map.of(
          "file1", "file1 contents",
          "file2", "file2 contents");

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public ActorSchedulerRule scheduler = new ActorSchedulerRule();

  private ConstructableSnapshotStore snapshotStore;

  @Before
  public void beforeEach() {
    final int partitionId = 1;
    final File root = temporaryFolder.getRoot();
    final FileBasedSnapshotStoreFactory factory =
        new FileBasedSnapshotStoreFactory(scheduler.get(), 1);

    factory.createReceivableSnapshotStore(root.toPath(), partitionId);
    snapshotStore = factory.getConstructableSnapshotStore(partitionId);
  }

  @Test
  public void shouldHaveCorrectSnapshotId() {
    // when
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 2L, 3L, 4L).get();
    final var snapshotId = transientSnapshot.snapshotId();

    // then
    assertThat(snapshotId.getIndex()).as("the ID has the right index").isEqualTo(1L);
    assertThat(snapshotId.getTerm()).as("the ID has the right term").isEqualTo(2L);
    assertThat(snapshotId.getProcessedPosition())
        .as("the ID has the right processed position")
        .isEqualTo(3L);
    assertThat(snapshotId.getExportedPosition())
        .as("the ID has the right exported position")
        .isEqualTo(4L);
  }

  @Test
  public void shouldAbortSuccessfullyEvenIfNothingWasWritten() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 0L, 1L, 0L).get();

    // when
    final ActorFuture<Void> didAbort = transientSnapshot.abort();

    // then
    assertThat(didAbort)
        .as("the transient snapshot was aborted successfully")
        .succeedsWithin(Duration.ofSeconds(5));
  }

  @Test
  public void shouldNotCommitUntilPersisted() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 2L, 3L, 4L).get();

    // when
    transientSnapshot.take(this::writeSnapshot).join();

    // then
    assertThat(snapshotStore.getLatestSnapshot())
        .as("there should be no persisted snapshot")
        .isEmpty();
  }

  @Test
  public void shouldTakeTransientSnapshot() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 2L, 3L, 4L).get();

    // when
    final var didWriteSnapshot = transientSnapshot.take(this::writeSnapshot);

    // then
    assertThatNoException()
        .as("the transient snapshot was successfully written")
        .isThrownBy(didWriteSnapshot::join);
  }

  @Test
  public void shouldCompleteExceptionallyOnExceptionInTake() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 2L, 3L, 4L).get();

    // when
    final var didTakeSnapshot =
        transientSnapshot.take(
            p -> {
              throw new RuntimeException("EXPECTED");
            });

    // then
    assertThatThrownBy(didTakeSnapshot::join)
        .as("did not take snapshot due to exception thrown in callback")
        .hasCauseInstanceOf(RuntimeException.class)
        .hasMessageContaining("EXPECTED");
  }

  @Test
  public void shouldBeAbleToAbortNotStartedSnapshot() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 2L, 3L, 4L).get();

    // when
    final ActorFuture<Void> didAbort = transientSnapshot.abort();

    // then
    assertThat(didAbort)
        .as("did abort even if nothing was written")
        .succeedsWithin(Duration.ofSeconds(5));
  }

  @Test
  public void shouldPersistSnapshotWithCorrectId() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 0L, 1L, 0L).get();
    transientSnapshot.take(this::writeSnapshot).join();

    // when
    final var persistedSnapshot = transientSnapshot.persist().join();

    // then
    assertThat(persistedSnapshot.getId())
        .as("the persisted snapshot as the same ID as the transient snapshot")
        .isEqualTo(transientSnapshot.snapshotId().getSnapshotIdAsString());
  }

  @Test
  public void shouldPersistSnapshot() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 0L, 1L, 0L).get();
    transientSnapshot.take(this::writeSnapshot);

    // when
    final var persistedSnapshot = transientSnapshot.persist().join();

    // then
    // TODO(npepinpe): compare checksums once the property is added to the PersistedSnapshot
    // interface
    assertThat(persistedSnapshot)
        .as("the persisted snapshot should be the latest snapshot")
        .isEqualTo(snapshotStore.getLatestSnapshot().orElseThrow());
  }

  @Test
  public void shouldReplacePreviousSnapshotOnPersist() {
    // given
    final var oldSnapshot = snapshotStore.newTransientSnapshot(1L, 0L, 1L, 0L).get();
    oldSnapshot.take(this::writeSnapshot);
    oldSnapshot.persist().join();

    // when
    final var newSnapshot = snapshotStore.newTransientSnapshot(2L, 0L, 1L, 0L).get();
    newSnapshot.take(this::writeSnapshot);
    final var persistedSnapshot = newSnapshot.persist().join();

    // then
    assertThat(snapshotStore.getLatestSnapshot())
        .as("the latest snapshot is the last persisted snapshot")
        .hasValue(persistedSnapshot);
  }

  @Test
  public void shouldNotPersistSnapshotIfIdIsLessThanTheLatestSnapshot() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(2L, 2L, 3L, 4L).get();
    transientSnapshot.take(this::writeSnapshot);
    final var previousSnapshot = transientSnapshot.persist().join();

    // when
    final var newTransientSnapshot = snapshotStore.newTransientSnapshot(1L, 2L, 4L, 5L).get();
    newTransientSnapshot.take(this::writeSnapshot);
    final var didPersist = newTransientSnapshot.persist();

    // then
    assertThat(didPersist)
        .as(
            "did not persist snapshot %s with ID less than %s and returns the previous snapshot",
            previousSnapshot.getId(), newTransientSnapshot.snapshotId().getSnapshotIdAsString())
        .succeedsWithin(Duration.ofSeconds(5))
        .isEqualTo(previousSnapshot);
  }

  @Test
  public void shouldRemoveTransientSnapshotOnPersist() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 0L, 1L, 0L).get();
    transientSnapshot.take(this::writeSnapshot);

    // when
    final var newSnapshot = snapshotStore.newTransientSnapshot(2L, 0L, 1L, 0L).get();
    newSnapshot.take(this::writeSnapshot);
    newSnapshot.persist().join();

    // then
    assertThat(newSnapshot.getPath()).as("the transient snapshot was removed").doesNotExist();
  }

  @Test
  public void shouldNotRemoveTransientSnapshotWithGreaterIdOnPersist() {
    final var newerTransientSnapshot = snapshotStore.newTransientSnapshot(2L, 0L, 1L, 0L).get();
    newerTransientSnapshot.take(this::writeSnapshot);

    // when
    final var newSnapshot = snapshotStore.newTransientSnapshot(1L, 0L, 1L, 0L).get();
    newSnapshot.take(this::writeSnapshot);
    newSnapshot.persist().join();
    final var persistedSnapshot = newerTransientSnapshot.persist().join();

    // then
    assertThat(persistedSnapshot)
        .as("the first transient snapshot with greater ID was persisted after the second one")
        .isEqualTo(snapshotStore.getLatestSnapshot().orElseThrow());
  }

  @Test
  public void shouldNotPersistOnTakeException() {
    // given
    final var snapshot = snapshotStore.newTransientSnapshot(1L, 0L, 1L, 0L).get();

    // when
    final var didTakeSnapshot =
        snapshot.take(
            path -> {
              throw new RuntimeException("expected");
            });

    // then
    assertThatThrownBy(didTakeSnapshot::join).hasCauseInstanceOf(RuntimeException.class);
    assertThat(snapshot.persist())
        .as("did not persist snapshot as it failed to be taken")
        .failsWithin(Duration.ofSeconds(5));
  }

  @Test
  public void shouldNotifyListenersOnNewSnapshot() {
    // given
    final AtomicReference<PersistedSnapshot> snapshotRef = new AtomicReference<>();
    final PersistedSnapshotListener listener = snapshotRef::set;
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 0L, 1L, 0L).get();
    snapshotStore.addSnapshotListener(listener);
    transientSnapshot.take(this::writeSnapshot).join();

    // when
    final var persistedSnapshot = transientSnapshot.persist().join();

    // then
    assertThat(snapshotRef).hasValue(persistedSnapshot);
  }

  @Test
  public void shouldNotNotifyListenersOnNewSnapshotWhenRemoved() {
    // given
    final AtomicReference<PersistedSnapshot> snapshotRef = new AtomicReference<>();
    final PersistedSnapshotListener listener = snapshotRef::set;
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 0L, 1L, 0L).get();
    snapshotStore.addSnapshotListener(listener);
    snapshotStore.removeSnapshotListener(listener);
    transientSnapshot.take(this::writeSnapshot).join();

    // when
    transientSnapshot.persist().join();

    // then
    assertThat(snapshotRef)
        .as("the listener was not called and did not record any new snapshot")
        .hasValue(null);
  }

  @Test
  public void shouldNotTakeSnapshotIfIdAlreadyExists() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 0L, 2L, 3L).get();
    transientSnapshot.take(this::writeSnapshot).join();

    // when
    transientSnapshot.persist().join();
    final var secondTransientSnapshot = snapshotStore.newTransientSnapshot(1L, 0L, 2L, 3L);

    // then
    assertThat(secondTransientSnapshot.getLeft())
        .as("should have no value since there already exists a transient snapshot with the same ID")
        .isInstanceOf(SnapshotAlreadyExistsException.class);
  }

  @Test
  public void shouldNotPersistDeletedTransientSnapshot() {
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 0L, 2L, 3L).get();
    transientSnapshot.take(this::writeSnapshot).join();

    // when
    snapshotStore.purgePendingSnapshots().join();
    final var persisted = transientSnapshot.persist();

    // then
    assertThatThrownBy(persisted::join)
        .as("did not persist a deleted transient snapshot")
        .hasCauseInstanceOf(SnapshotNotFoundException.class);
  }

  private void writeSnapshot(final Path path) {
    try {
      FileUtil.ensureDirectoryExists(path);

      for (final var entry : SNAPSHOT_FILE_CONTENTS.entrySet()) {
        final var fileName = path.resolve(entry.getKey());
        Files.writeString(fileName, entry.getValue(), CREATE_NEW, StandardOpenOption.WRITE);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
