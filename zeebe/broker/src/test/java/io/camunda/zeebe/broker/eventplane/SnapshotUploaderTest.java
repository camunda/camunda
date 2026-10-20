/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.eventplane.segment.Identity;
import io.camunda.zeebe.eventplane.snapshot.SnapshotArchive;
import io.camunda.zeebe.eventplane.snapshot.SnapshotDescriptor;
import io.camunda.zeebe.eventplane.snapshot.SnapshotDescriptor.SnapshotFile;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotFilesInfo;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A leader's snapshots go into the store with a descriptor, once each. */
final class SnapshotUploaderTest {

  private static final Identity LOG = new Identity("c", "default", 1);
  private static final UUID HISTORY = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

  private static ActorScheduler actorScheduler;

  @TempDir private Path bucket;
  @TempDir private Path partitionDirectory;
  private FileSystemStore store;
  private FileBasedSnapshotStore snapshots;
  private SnapshotUploader uploader;

  @BeforeAll
  static void startScheduler() {
    actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();
  }

  @AfterAll
  static void stopScheduler() throws Exception {
    actorScheduler.stop().get();
  }

  @BeforeEach
  void setUp() {
    store = new FileSystemStore(bucket);
    snapshots =
        new FileBasedSnapshotStore(
            0,
            1,
            partitionDirectory,
            snapshotPath -> SnapshotFilesInfo.none(),
            new SimpleMeterRegistry());
    actorScheduler.submitActor(snapshots, SchedulingHints.IO_BOUND).join();
    uploader = new SnapshotUploader(LOG, store, snapshots, InstantSource.fixed(NOW));
  }

  @AfterEach
  void tearDown() {
    snapshots.close();
  }

  @Test
  void shouldUploadTheFilesAndTheChecksumFileThenTheDescriptor() throws IOException {
    // given
    final var snapshot = takeSnapshot(10, 40, 30, 42, 35);

    // when
    final var uploaded = uploader.uploadLatest(HISTORY);

    // then
    final var expectedFiles = new ArrayList<SnapshotFile>();
    for (final var file : new TreeMap<>(snapshot.files()).entrySet()) {
      expectedFiles.add(new SnapshotFile(file.getKey(), Files.size(file.getValue())));
    }
    final var expected =
        new SnapshotDescriptor(
            1, snapshot.getId(), 10, 1, 40, 30, 35, 42, HISTORY, expectedFiles, NOW);
    assertThat(uploaded).contains(expected);
    assertThat(SnapshotArchive.descriptor(store, "c", "default", 1, snapshot.getId()))
        .contains(expected);
    assertThat(expected.files())
        .extracting(SnapshotFile::name)
        .contains(snapshot.getId() + ".checksum", "zeebe.metadata", "state");
    for (final var file : snapshot.files().entrySet()) {
      assertThat(
              store.get(
                  ObjectNames.snapshotFile("c", "default", 1, snapshot.getId(), file.getKey())))
          .hasValueSatisfying(
              content -> assertThat(content).isEqualTo(readAllBytes(file.getValue())));
    }
    assertThat(snapshot.isReserved()).describedAs("released after the upload").isFalse();
  }

  @Test
  void shouldNotUploadASnapshotTwice() throws IOException {
    // given
    takeSnapshot(10, 40, 30, 42, 35);
    uploader.uploadLatest(HISTORY);

    // when
    final var second = uploader.uploadLatest(HISTORY);

    // then
    assertThat(second).isEmpty();
  }

  @Test
  void shouldUploadNothingWithoutASnapshot() throws IOException {
    // when
    final var uploaded = uploader.uploadLatest(HISTORY);

    // then
    assertThat(uploaded).isEmpty();
    assertThat(store.listPrefixes("")).isEmpty();
  }

  @Test
  void shouldUploadTheNewerSnapshotOnceThereIsOne() throws IOException {
    // given
    final var first = takeSnapshot(10, 40, 30, 42, 35);
    uploader.uploadLatest(HISTORY);
    final var second = takeSnapshot(20, 80, 70, 82, 75);

    // when
    final var uploaded = uploader.uploadLatest(HISTORY);

    // then
    assertThat(uploaded)
        .hasValueSatisfying(d -> assertThat(d.snapshotId()).isEqualTo(second.getId()));
    assertThat(SnapshotArchive.list(store, "c", "default", 1).complete())
        .extracting(SnapshotDescriptor::snapshotId)
        .containsExactlyInAnyOrder(first.getId(), second.getId());
  }

  private PersistedSnapshot takeSnapshot(
      final long index,
      final long processedPosition,
      final long exportedPosition,
      final long lastFollowupEventPosition,
      final long maxExportedPosition) {
    final var transientSnapshot =
        snapshots.newTransientSnapshot(index, 1, processedPosition, exportedPosition, false).get();
    transientSnapshot
        .take(
            path -> {
              try {
                Files.createDirectories(path);
                Files.write(
                    path.resolve("state"), ("state at " + index).getBytes(StandardCharsets.UTF_8));
              } catch (final IOException e) {
                throw new UncheckedIOException(e);
              }
            })
        .join();
    return transientSnapshot
        .withLastFollowupEventPosition(lastFollowupEventPosition)
        .withMaxExportedPosition(maxExportedPosition)
        .persist()
        .join();
  }

  private static byte[] readAllBytes(final Path file) {
    try {
      return Files.readAllBytes(file);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
