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
import io.camunda.zeebe.snapshots.SnapshotFilesInfo;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

/**
 * After a failed sync, the kernel marks the pages clean and reports the error only once, so syncing
 * the same file again may succeed without writing it. A snapshot which fails to be written is
 * therefore never committed, and a later snapshot writes new files instead of syncing the same ones
 * again.
 */
final class FileBasedSnapshotStoreWriteFailureTest {

  @RegisterExtension final ActorSchedulerExtension actorScheduler = new ActorSchedulerExtension();

  @TempDir Path root;
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private FileBasedSnapshotStore store;

  @BeforeEach
  void setUp() {
    store = createStore();
  }

  @Test
  void shouldNotCommitSnapshotWhoseMetadataFailedToBeWritten() {
    // given - writing the metadata file fails, standing in for a failed sync of its DSYNC write
    final var failingSnapshot = takeSnapshot(1, true);

    // when
    final var persisted = failingSnapshot.persist();

    // then
    assertThat(persisted).failsWithin(Duration.ofSeconds(10));
    assertThat(store.getLatestSnapshot()).isEmpty();
  }

  @Test
  void shouldCommitLaterSnapshotWithNewFiles() {
    // given
    final var failingSnapshot = takeSnapshot(1, true);
    assertThat(failingSnapshot.persist()).failsWithin(Duration.ofSeconds(10));

    // when
    final var persisted = takeSnapshot(2, false).persist().join();
    store.close();
    store = createStore();

    // then
    assertThat(store.getLatestSnapshot()).hasValue(persisted);
  }

  private TransientSnapshot takeSnapshot(final long index, final boolean failToWriteMetadata) {
    final var transientSnapshot = store.newTransientSnapshot(index, 1, index, 0, false).get();
    transientSnapshot
        .take(
            path -> {
              try {
                Files.createDirectories(path);
                Files.writeString(path.resolve("file"), "content");
              } catch (final IOException e) {
                throw new UncheckedIOException(e);
              }
            })
        .join();

    if (failToWriteMetadata) {
      // the metadata file cannot be opened for writing if it is a directory
      try {
        Files.createDirectory(
            transientSnapshot.getPath().resolve(FileBasedSnapshotStoreImpl.METADATA_FILE_NAME));
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return transientSnapshot;
  }

  private FileBasedSnapshotStore createStore() {
    final var snapshotStore =
        new FileBasedSnapshotStore(0, 1, root, path -> SnapshotFilesInfo.none(), meterRegistry);
    actorScheduler.submitActor(snapshotStore).join();
    return snapshotStore;
  }
}
