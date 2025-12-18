/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.testkit;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.testkit.support.BackupAssert;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import java.nio.file.Path;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

public interface RestoringBackup {
  BackupStore getStore();

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
}
