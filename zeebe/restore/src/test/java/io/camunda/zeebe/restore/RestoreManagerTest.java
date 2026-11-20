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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.restore.RestoreFailure.Reason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RestoreManagerTest {

  @Test
  void shouldFailWhenDirectoryIsNotEmpty(@TempDir final Path dir) throws IOException {
    // given
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    final var restoreManager =
        new RestoreManager(
            configuration, new TestRestorableBackupStore(), new SimpleMeterRegistry());

    // when
    Files.createDirectory(dir.resolve("other-data"));

    // then
    assertThatThrownBy(() -> restoreManager.restore(1L, false, List.of()))
        .isInstanceOf(DirectoryNotEmptyException.class);
  }

  @Test
  void shouldIgnoreConfigurableFilesInTarget(@TempDir final Path dir) throws IOException {
    // given
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    final var restoreManager =
        new RestoreManager(
            configuration, new TestRestorableBackupStore(), new SimpleMeterRegistry());

    // when - create ignored files
    Files.createDirectory(dir.resolve("lost+found"));
    Files.createFile(dir.resolve(".DS_Store"));
    Files.createFile(dir.resolve("Thumbs.db"));

    // then - should not fail because all files are ignored
    assertThatThrownBy(
            () ->
                restoreManager.restore(1L, false, List.of("lost+found", ".DS_Store", "Thumbs.db")))
        .isInstanceOf(BackupNotFoundException.class);
  }

  @Test
  void shouldFailWhenNonIgnoredFileExists(@TempDir final Path dir) throws IOException {
    // given
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    final var restoreManager =
        new RestoreManager(
            configuration, new TestRestorableBackupStore(), new SimpleMeterRegistry());

    // when - create ignored and non-ignored files
    Files.createDirectory(dir.resolve("lost+found"));
    Files.createFile(dir.resolve("some-data-file"));

    // then - should fail because some-data-file is not ignored
    assertThatThrownBy(() -> restoreManager.restore(1L, false, List.of("lost+found")))
        .isInstanceOf(DirectoryNotEmptyException.class);
  }

  @Test
  void shouldFindEveryBackupBeforeWritingWhenALaterBackupIsMissing(@TempDir final Path dir)
      throws IOException {
    // given - backup 1 exists, backup 2 does not
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    final var backupStore = mock(BackupStore.class);
    when(backupStore.list(any(BackupIdentifierWildcard.class)))
        .thenAnswer(
            invocation -> {
              final BackupIdentifierWildcard wildcard = invocation.getArgument(0);
              return CompletableFuture.completedFuture(
                  wildcard.checkpointPattern().matches(1L)
                      ? List.of(completedBackup(1L))
                      : List.of());
            });
    final var restoreManager =
        new RestoreManager(configuration, backupStore, new SimpleMeterRegistry());
    Files.createDirectory(dir.resolve("lost+found"));

    // when
    final var failure =
        catchThrowable(
            () -> restoreManager.restore(new long[] {1L, 2L}, false, List.of("lost+found")));

    // then - the restore app reports the reason as having left the data directory untouched
    assertThat(failure).isInstanceOf(BackupNotFoundException.class);
    assertThat(((RestoreFailure) failure).reason()).isEqualTo(Reason.BACKUPS_INCONSISTENT);
    verify(backupStore, never()).restore(any(), any());
    try (final var entries = Files.list(dir)) {
      assertThat(entries).containsExactly(dir.resolve("lost+found"));
    }
  }

  private static BackupStatus completedBackup(final long checkpointId) {
    return new BackupStatusImpl(
        new BackupIdentifierImpl(0, 1, checkpointId),
        Optional.empty(),
        BackupStatusCode.COMPLETED,
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }
}
