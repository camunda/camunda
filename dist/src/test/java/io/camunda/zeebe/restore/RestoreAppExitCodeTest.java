/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.Profile;
import io.camunda.zeebe.backup.api.BackupRange;
import io.camunda.zeebe.backup.api.Checkpoint;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupConfig;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupStore;
import io.camunda.zeebe.backup.management.BackupMetadataSyncer;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ExitCodeEvent;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationListener;

/**
 * Runs the restore app as a Spring Boot application, as its main method does, and checks two parts
 * of the exit code contract: a failed restore exits with the code of its reason, and a classified
 * failure leaves the data directory untouched, so the caller can run the restore again on the same
 * volume.
 *
 * <p>The exit itself happens on the main thread only, so the test observes the {@link
 * ExitCodeEvent} Spring Boot publishes for it.
 */
final class RestoreAppExitCodeTest {

  private static final Instant BACKUP_TIMESTAMP = Instant.parse("2026-01-01T10:00:00Z");

  @TempDir private Path backupDirectory;
  @TempDir private Path dataDirectory;

  private final List<Integer> exitCodes = new ArrayList<>();

  @Test
  void shouldExitWithTheInvalidRequestCodeWhenBothABackupIdAndATimeRangeAreGiven()
      throws IOException {
    // when
    assertThatThrownBy(() -> runRestore("--backupId=1", "--from=2026-01-01T10:00:00Z"))
        .hasMessageContaining("Cannot specify both --backupId and --from/--to");

    // then
    assertThat(exitCodes).containsExactly(10);
    assertDataDirectoryIsUntouched();
  }

  @Test
  void shouldExitWithTheRestorePointUnavailableCodeWhenTheBackupsDoNotCoverTheRequestedPoint()
      throws IOException {
    // given - the only backup was taken a day after the requested point
    storeBackupMetadata(1, BACKUP_TIMESTAMP);

    // when
    assertThatThrownBy(() -> runRestore("--from=2025-12-31T10:00:00Z"))
        .hasMessageContaining("No usable range found for partition 1");

    // then
    assertThat(exitCodes).containsExactly(11);
    assertDataDirectoryIsUntouched();
  }

  @Test
  void shouldExitWithTheBackupsInconsistentCodeWhenTheBackupDoesNotExist() throws IOException {
    // when
    assertThatThrownBy(() -> runRestore("--backupId=42"))
        .hasRootCauseMessage("Could not find a completed backup with id 42.");

    // then
    assertThat(exitCodes).containsExactly(13);
    assertDataDirectoryIsUntouched();
  }

  private void runRestore(final String... args) {
    new SpringApplicationBuilder(RestoreApp.class)
        .web(WebApplicationType.NONE)
        .profiles(Profile.RESTORE.getId())
        .listeners((ApplicationListener<ExitCodeEvent>) e -> exitCodes.add(e.getExitCode()))
        .properties(
            "camunda.data.primary-storage.directory=" + dataDirectory,
            "camunda.data.primary-storage.backup.store=filesystem",
            "camunda.data.primary-storage.backup.filesystem.basepath=" + backupDirectory,
            "camunda.data.primary-storage.backup.continuous=true",
            "camunda.data.secondary-storage.type=none",
            "camunda.cluster.node-id=0")
        .run(args);
  }

  /** Stores backup metadata of partition 1 holding a single backup, as a backup would. */
  private void storeBackupMetadata(final long backupId, final Instant timestamp) {
    final var store =
        FilesystemBackupStore.of(
            new FilesystemBackupConfig.Builder().withBasePath(backupDirectory.toString()).build());
    try {
      new BackupMetadataSyncer(store, new SimpleMeterRegistry())
          .store(
              1,
              List.of(
                  new Checkpoint(
                      CheckpointType.SCHEDULED_BACKUP,
                      backupId,
                      timestamp.toEpochMilli(),
                      100L,
                      1L)),
              List.of(new BackupRange(backupId, backupId)))
          .join();
    } finally {
      store.closeAsync().join();
    }
  }

  /** The restore may create the data directory itself, but must not write anything into it. */
  private void assertDataDirectoryIsUntouched() throws IOException {
    try (final Stream<Path> files = Files.walk(dataDirectory)) {
      assertThat(files.filter(Files::isRegularFile)).isEmpty();
    }
  }
}
