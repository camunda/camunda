/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.testkit;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.testkit.support.BackupAssert;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.RandomUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

public interface RestoringBackup {
  BackupStore getStore();

  Class<? extends Exception> getFailToRestoreDuetoUnexistingFileExceptionClass();

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  default void canRestoreBackup(final Backup backup, @TempDir final Path targetDir) {
    // given
    getStore().save(backup).join();

    // when
    final var result = getStore().restore(backup.id(), targetDir);

    // then
    Assertions.assertThat(result).succeedsWithin(Duration.ofSeconds(10));
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  default void restoredBackupHasSameContents(
      final Backup originalBackup, @TempDir final Path targetDir) {
    // given
    getStore().save(originalBackup).join();

    // when
    final var restored = getStore().restore(originalBackup.id(), targetDir).join();

    // then
    BackupAssert.assertThatBackup(restored)
        .hasSameContentsAs(originalBackup)
        .residesInPath(targetDir);
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  default void savedContentDoesNotGetOverWritten(
      final Backup originalBackup, @TempDir final Path targetDir) {
    // given
    final Backup secondBackup =
        new BackupImpl(
            new BackupIdentifierImpl(
                11, originalBackup.id().partitionId(), originalBackup.id().checkpointId()),
            originalBackup.descriptor(),
            originalBackup.snapshot(),
            originalBackup.segments());

    getStore().save(originalBackup).join();
    getStore().save(secondBackup).join();

    // when
    final var restored = getStore().restore(originalBackup.id(), targetDir).join();

    // then
    BackupAssert.assertThatBackup(restored)
        .hasSameContentsAs(originalBackup)
        .residesInPath(targetDir);
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  default void cannotRestoreUploadingBackup(final Backup backup, @TempDir final Path targetDir)
      throws IOException {
    // given
    final var tempDir = Files.createTempDirectory("backup");
    Files.createDirectory(tempDir.resolve("segments/"));
    final var seg1 = Files.createFile(tempDir.resolve("segments/segment-file-1"));

    Files.write(seg1, RandomUtils.nextBytes(50 * 1024 * 1024));

    final Backup largeBackup =
        new BackupImpl(
            backup.id(),
            new BackupDescriptorImpl(Optional.empty(), 4, 5, "test"),
            new NamedFileSetImpl(Map.of()),
            new NamedFileSetImpl(Map.of("segment-file-1", seg1)));

    // when
    // Takes long to save 50MB
    getStore().save(largeBackup);

    // then
    Assertions.assertThat(getStore().restore(largeBackup.id(), targetDir))
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableOfType(Throwable.class)
        .withRootCauseInstanceOf(getFailToRestoreDuetoUnexistingFileExceptionClass());
  }
}
