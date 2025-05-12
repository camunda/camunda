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
  private Path reservationsDir;
  private TestActor actor;

  @Before
  public void beforeEach() throws Exception {
    snapshotDir = temporaryFolder.newFolder("store", "snapshots").toPath();
    reservationsDir = temporaryFolder.newFolder("store", "reservations").toPath();
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
  public void shouldReserveSnapshotPersistently() throws IOException {
    // given
    final var snapshotPath = snapshotDir.resolve("snapshot");
    final Path checksumPath = snapshotDir.resolve("checksum");
    var snapshot = createSnapshot(snapshotPath, checksumPath);
    final var reservationId = snapshot.reserveWithPersistence().join().reservationId();
    assertThat(snapshot.isReserved()).isTrue();

    // when
    snapshot = openSnapshot(snapshotPath, checksumPath);

    // then
    assertThat(snapshot.isReserved()).isTrue();

    // when
    final var reservation = snapshot.getPersistedSnapshotReservation(reservationId).join();
    reservation.release().join();

    assertThat(snapshot.isReserved()).isFalse();
  }

  @Test
  public void shouldReserveSnapshotPersistentlyAndInMemory() throws IOException {
    // given
    final var snapshotPath = snapshotDir.resolve("snapshot");
    final Path checksumPath = snapshotDir.resolve("checksum");
    final var snapshot = createSnapshot(snapshotPath, checksumPath);
    final var persistedReservation = snapshot.reserveWithPersistence().join();
    assertThat(snapshot.isReserved()).isTrue();
    final var inMemoryReservation = snapshot.reserve().join();
    assertThat(snapshot.isReserved()).isTrue();

    // when
    persistedReservation.release().join();
    // then
    assertThat(snapshot.isReserved()).isTrue();
    // when
    inMemoryReservation.release().join();
    // then
    assertThat(snapshot.isReserved()).isFalse();
  }

  @Test
  public void shouldAssignANewReservationIdAfterARestart() throws IOException {
    // given
    final var snapshotPath = snapshotDir.resolve("snapshot");
    final Path checksumPath = snapshotDir.resolve("checksum");
    var snapshot = createSnapshot(snapshotPath, checksumPath);
    final var reservationId = snapshot.reserveWithPersistence().join().reservationId();
    assertThat(snapshot.isReserved()).isTrue();

    // when
    snapshot = openSnapshot(snapshotPath, checksumPath);

    // then
    assertThat(snapshot.isReserved()).isTrue();

    // when
    final var newReservation = snapshot.reserveWithPersistence().join();
    // then
    assertThat(newReservation.reservationId()).isNotEqualTo(reservationId);

    // when
    newReservation.release().join();
    // the first reservation is still active
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

  private FileBasedSnapshot createSnapshot(final Path snapshotPath, final Path checksumPath)
      throws IOException {
    final var metadata = new FileBasedSnapshotId(1L, 1L, 1L, 1L, 0);

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
        reservationsDir,
        new SfvChecksumImpl(),
        metadata,
        null,
        s -> {},
        actor.getActorControl());
  }

  // Opens an already persisted snapshot to simulate restarts
  private FileBasedSnapshot openSnapshot(final Path snapshotPath, final Path checksumPath)
      throws IOException {
    final var metadata = new FileBasedSnapshotId(1L, 1L, 1L, 1L, 0);
    return new FileBasedSnapshot(
        snapshotPath,
        checksumPath,
        reservationsDir,
        new SfvChecksumImpl(),
        metadata,
        null,
        s -> {},
        actor.getActorControl());
  }

  static class TestActor extends Actor {
    io.camunda.zeebe.scheduler.ActorControl getActorControl() {
      return actor;
    }
  }
}
