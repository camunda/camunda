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

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class FileBasedSnapshotTest {
  private static final Map<String, String> SNAPSHOT_FILE_CONTENTS =
      Map.of(
          "file1", "file1 contents",
          "file2", "file2 contents");

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public ActorSchedulerRule scheduler = new ActorSchedulerRule();

  private Path snapshotDir;
  private TestActor actor;

  @Before
  public void beforeEach() throws Exception {
    snapshotDir = temporaryFolder.newFolder("store", "snapshots").toPath();
    actor = new TestActor();
    scheduler.submitActor(actor);
  }

  @Test
  public void shouldDeleteSnapshot() throws IOException {
    // given
    final var snapshotPath = snapshotDir.resolve("snapshot");
    final Path checksumPath = snapshotDir.resolve("checksum");
    final var snapshot = createSnapshot(snapshotPath, checksumPath);

    // when
    snapshot.delete();

    // then
    assertThat(snapshotPath).doesNotExist();
    assertThat(checksumPath).doesNotExist();
  }

  @Test
  public void shouldReserveSnapshot() throws IOException {
    // given
    final var snapshotPath = snapshotDir.resolve("snapshot");
    final Path checksumPath = snapshotDir.resolve("checksum");
    final var snapshot = createSnapshot(snapshotPath, checksumPath);

    // when
    snapshot.reserve().join();

    // then
    assertThat(snapshot.isReserved()).isTrue();
  }

  @Test
  public void shouldReleaseReservedSnapshot() throws IOException {
    // given
    final var snapshotPath = snapshotDir.resolve("snapshot");
    final Path checksumPath = snapshotDir.resolve("checksum");
    final var snapshot = createSnapshot(snapshotPath, checksumPath);
    final var reservation = snapshot.reserve().join();

    // when
    reservation.release().join();

    // then
    assertThat(snapshot.isReserved()).isFalse();
  }

  @Test
  public void shouldReleaseSnapshotOnlyAfterAllReservationsAreReleased() throws IOException {
    // given
    final var snapshotPath = snapshotDir.resolve("snapshot");
    final Path checksumPath = snapshotDir.resolve("checksum");
    final var snapshot = createSnapshot(snapshotPath, checksumPath);
    final var firstReservation = snapshot.reserve().join();
    final var secondReservation = snapshot.reserve().join();

    // when
    firstReservation.release().join();

    // then
    assertThat(snapshot.isReserved()).isTrue();

    // when
    secondReservation.release().join();

    // then
    assertThat(snapshot.isReserved()).isFalse();
  }

  @Test
  public void shouldNotMarkAsReservedBootstrappedSnapshots() throws IOException {
    // given
    final var metadata = new FileBasedSnapshotMetadata(1, 1L, 1L, 1L, 0, true);
    final var snapshotPath = snapshotDir.resolve("snapshot");
    final Path checksumPath = snapshotDir.resolve("checksum");

    // when
    final var snapshot = createSnapshot(snapshotPath, checksumPath, metadata);

    // then
    assertThat(snapshot.isReserved()).isFalse();
    assertThat(snapshot.isBootstrap()).isTrue();
  }

  private FileBasedSnapshot createSnapshot(final Path snapshotPath, final Path checksumPath)
      throws IOException {
    return createSnapshot(snapshotPath, checksumPath, null);
  }

  private FileBasedSnapshot createSnapshot(
      final Path snapshotPath, final Path checksumPath, final FileBasedSnapshotMetadata metadata)
      throws IOException {
    final var snapshotId = new FileBasedSnapshotId(1L, 1L, 1L, 1L, 0);

    FileUtil.ensureDirectoryExists(snapshotPath);
    for (final var entry : SNAPSHOT_FILE_CONTENTS.entrySet()) {
      final var fileName = snapshotPath.resolve(entry.getKey());
      final var fileContent = entry.getValue().getBytes(StandardCharsets.UTF_8);
      Files.write(fileName, fileContent, CREATE_NEW, StandardOpenOption.WRITE);
    }
    SnapshotChecksum.persist(checksumPath, SnapshotChecksum.calculate(snapshotPath));

    return new FileBasedSnapshot(
        snapshotPath,
        checksumPath,
        new SfvChecksumImpl(),
        snapshotId,
        metadata,
        s -> {},
        actor.getActorControl());
  }

  static class TestActor extends Actor {
    io.camunda.zeebe.scheduler.ActorControl getActorControl() {
      return actor;
    }
  }
}
