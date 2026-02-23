/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.snapshots.SnapshotMetadata;
import io.camunda.zeebe.test.util.asserts.DirectoryAssert;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileBasedTransientSnapshotTest {
  private static final String SNAPSHOT_DIRECTORY = "snapshots";
  private static final Map<String, String> SNAPSHOT_FILE_CONTENTS =
      Map.of(
          "file1", "file1 contents",
          "file2", "file2 contents");

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public ActorSchedulerRule scheduler = new ActorSchedulerRule();

  private FileBasedSnapshotStore snapshotStore;
  private Path snapshotsDir;

  @Before
  public void beforeEach() throws IOException {
    final var root = temporaryFolder.getRoot().toPath();
    snapshotsDir = root.resolve(SNAPSHOT_DIRECTORY);
    snapshotStore = createStore(root);
  }

  @Test
  public void shouldNotCreateTransientDirectoryIfNothingWritten() {
    // when
    snapshotStore.newTransientSnapshot(1L, 0L, 1L, 0L, false);

    // then
    assertThat(snapshotsDir)
        .as("the snapshots directory is empty as nothing was written")
        .isEmptyDirectory();
  }

  @Test
  public void shouldNotCommitUntilPersisted() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 2L, 3L, 4L, false).get();

    // when
    transientSnapshot.take(this::writeSnapshot).join();

    // then
    assertThat(snapshotsDir)
        .isDirectoryNotContaining(
            p -> p.getFileName().toFile().getName().equals("1-2-3-4.checksum"));
  }

  @Test
  public void shouldTakeTransientSnapshot() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 2L, 3L, 4L, false).get();

    // when
    transientSnapshot.take(this::writeSnapshot).join();

    // then
    assertThat(transientSnapshot.getPath())
        .as("the transient snapshot directory was written in the pending directory")
        .hasParent(snapshotsDir)
        .isNotEmptyDirectory();
  }

  @Test
  public void shouldDeleteTransientDirectoryOnAbort() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 0L, 1L, 0L, false).get();
    transientSnapshot.take(this::writeSnapshot).join();

    // when
    transientSnapshot.abort().join();

    // then
    assertThat(transientSnapshot.getPath())
        .as("the transient directory should not exist after abort")
        .doesNotExist();
  }

  @Test
  public void shouldNotDeletePersistedSnapshotOnPurge() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 0L, 1L, 0L, false).get();
    transientSnapshot.take(this::writeSnapshot).join();
    final var persistedSnapshot = transientSnapshot.persist().join();

    // when
    snapshotStore.abortPendingSnapshots().join();

    // then
    assertThat(persistedSnapshot.getPath())
        .as("the persisted snapshot still exists in the committed snapshots directory")
        .isNotEmptyDirectory()
        .hasParent(snapshotsDir);
    assertThat(snapshotStore.getLatestSnapshot())
        .as("the latest snapshot was not changed after purge")
        .hasValue(persistedSnapshot);
  }

  @Test
  public void shouldPersistSnapshot() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 0L, 1L, 0L, false).get();
    transientSnapshot.take(this::writeSnapshot);

    // when
    final var persistedSnapshot = transientSnapshot.persist().join();

    // then
    assertThat(persistedSnapshot.getPath())
        .as("the snapshot was persisted under the snapshots directory")
        .hasParent(snapshotsDir);

    for (final var expectedChunk : SNAPSHOT_FILE_CONTENTS.entrySet()) {
      final var expectedFileName = expectedChunk.getKey();
      final var expectedFileContents = expectedChunk.getValue();
      assertThat(persistedSnapshot.getPath().resolve(expectedFileName))
          .as("chunk %s was persisted with the expected contents", expectedFileName)
          .isRegularFile()
          .hasBinaryContent(expectedFileContents.getBytes(StandardCharsets.UTF_8));
    }
  }

  @Test
  public void shouldReplacePreviousSnapshotOnPersist() {
    // given
    final var oldSnapshot = snapshotStore.newTransientSnapshot(1L, 0L, 1L, 0L, false).get();
    oldSnapshot.take(this::writeSnapshot);
    oldSnapshot.persist().join();

    // when
    final var newSnapshot = snapshotStore.newTransientSnapshot(2L, 0L, 1L, 0L, false).get();
    newSnapshot.take(this::writeSnapshot);
    final var persistedSnapshot = (FileBasedSnapshot) newSnapshot.persist().join();

    // then
    assertThat(snapshotsDir)
        .asInstanceOf(DirectoryAssert.factory())
        .as("the committed snapshots directory only contains the latest snapshot")
        .isDirectoryContainingExactly(
            persistedSnapshot.getPath(), persistedSnapshot.getChecksumPath());
  }

  @Test
  public void shouldNotRemoveTransientSnapshotWithGreaterIdOnPersist() {
    // given
    final var newerTransientSnapshot =
        snapshotStore.newTransientSnapshot(2L, 0L, 1L, 0L, false).get();
    newerTransientSnapshot.take(this::writeSnapshot);

    // when
    final var newSnapshot = snapshotStore.newTransientSnapshot(1L, 0L, 1L, 0L, false).get();
    newSnapshot.take(this::writeSnapshot);
    newSnapshot.persist().join();

    // then
    assertThat(newerTransientSnapshot.getPath())
        .as("the newer transient snapshot still exists since it has a greater ID")
        .isNotEmptyDirectory()
        .hasParent(snapshotsDir);
  }

  @Test
  public void shouldRemoveTransientDirectoryOnTakeException() {
    // given
    final var snapshot = snapshotStore.newTransientSnapshot(1L, 0L, 1L, 0L, false).get();

    // when
    final var didTakeSnapshot =
        snapshot.take(
            path -> {
              throw new RuntimeException("expected");
            });

    // then
    assertThatThrownBy(didTakeSnapshot::join).hasCauseInstanceOf(RuntimeException.class);
    assertThat(snapshotsDir).as("there is no committed snapshot").isEmptyDirectory();
  }

  @Test
  public void shouldNotPersistNonExistentTransientSnapshot() {
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 0L, 2L, 3L, false).get();
    transientSnapshot.take(p -> {});

    // when
    final var persisted = transientSnapshot.persist();

    // then
    assertThatThrownBy(persisted::join)
        .as("did not persist a non existent transient snapshot")
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldNotPersistEmptyTransientSnapshot() {
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 0L, 2L, 3L, false).get();
    transientSnapshot.take(p -> p.toFile().mkdir());

    // when
    final var persisted = transientSnapshot.persist();

    // then
    assertThatThrownBy(persisted::join)
        .as("did not persist an empty transient snapshot directory")
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldPersistIdempotently() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 2L, 3, 4, false).get();
    transientSnapshot.take(this::writeSnapshot).join();
    final var firstSnapshot = (FileBasedSnapshot) transientSnapshot.persist().join();

    // when
    final var secondSnapshot = transientSnapshot.persist().join();

    // then
    assertThat(secondSnapshot).as("did persist the same snapshot twice").isEqualTo(firstSnapshot);
    assertThat(secondSnapshot.getChecksums().sameChecksums(firstSnapshot.getChecksums()))
        .as("the content of the snapshot remains unchanged")
        .isTrue();
    assertThat(snapshotsDir)
        .asInstanceOf(DirectoryAssert.factory())
        .as("snapshots directory only contains snapshot %s", firstSnapshot.getId())
        .isDirectoryContainingExactly(firstSnapshot.getPath(), firstSnapshot.getChecksumPath());
  }

  @Test
  public void shouldAddMetadataToPersistedSnapshot() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 2L, 3, 4, false).get();
    transientSnapshot.take(this::writeSnapshot).join();
    // when
    final var persistedSnapshot =
        transientSnapshot.withLastFollowupEventPosition(100L).persist().join();

    // then
    final SnapshotMetadata metadata = persistedSnapshot.getMetadata();
    assertThat(metadata)
        .extracting(
            SnapshotMetadata::processedPosition,
            SnapshotMetadata::minExportedPosition,
            SnapshotMetadata::lastFollowupEventPosition,
            SnapshotMetadata::version)
        .containsExactly(3L, 4L, 100L, FileBasedSnapshotStoreImpl.VERSION);
  }

  @Test
  public void shouldAddMaxExportedPositionToMetadata() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 2L, 3, 4, false).get();
    transientSnapshot.take(this::writeSnapshot).join();

    // when
    final var persistedSnapshot = transientSnapshot.withMaxExportedPosition(250L).persist().join();

    // then
    final SnapshotMetadata metadata = persistedSnapshot.getMetadata();
    assertThat(metadata.maxExportedPosition()).isEqualTo(250L);
  }

  @Test
  public void shouldDefaultMaxExportedPositionToMaxLong() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 2L, 3, 4, false).get();
    transientSnapshot.take(this::writeSnapshot).join();

    // when - no withMaxExportedPosition call
    final var persistedSnapshot = transientSnapshot.persist().join();

    // then
    final SnapshotMetadata metadata = persistedSnapshot.getMetadata();
    assertThat(metadata.maxExportedPosition()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void shouldPersistMetadata() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 2L, 3, 4, false).get();
    transientSnapshot.take(this::writeSnapshot).join();

    // when
    final var persistedSnapshot =
        transientSnapshot.withLastFollowupEventPosition(100L).persist().join();

    // then
    assertThat(persistedSnapshot.getPath())
        .describedAs("Metadata file is persisted in snapshot path")
        .isDirectoryContaining(
            path ->
                path.getFileName()
                    .toString()
                    .equals(FileBasedSnapshotStoreImpl.METADATA_FILE_NAME));
  }

  private boolean writeSnapshot(final Path path) {
    try {
      FileUtil.ensureDirectoryExists(path);

      for (final var entry : SNAPSHOT_FILE_CONTENTS.entrySet()) {
        final var fileName = path.resolve(entry.getKey());
        final var fileContent = entry.getValue().getBytes(StandardCharsets.UTF_8);
        Files.write(fileName, fileContent, CREATE_NEW, StandardOpenOption.WRITE);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return true;
  }

  private FileBasedSnapshotStore createStore(final Path root) throws IOException {
    final var store =
        new FileBasedSnapshotStore(0, 1, root, snapshotPath -> Map.of(), new SimpleMeterRegistry());
    scheduler.submitActor(store);
    return store;
  }
}
