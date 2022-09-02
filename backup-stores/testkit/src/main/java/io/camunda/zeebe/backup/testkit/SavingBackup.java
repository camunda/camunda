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
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

public interface SavingBackup {
  BackupStore getStore();

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  default void savingBackupIsSuccessful(final Backup backup) {
    Assertions.assertThat(getStore().save(backup)).succeedsWithin(Duration.ofSeconds(10));
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  default void backupFailsIfBackupAlreadyExists(final Backup backup) {
    // when
    getStore().save(backup).join();

    // then
    Assertions.assertThat(getStore().save(backup)).failsWithin(Duration.ofSeconds(10));
    // TODO
    //  .withThrowableOfType(Throwable.class);
    //  .withRootCauseInstanceOf(BackupInInvalidStateException.class);
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  default void backupFailsIfFilesAreMissing(final Backup backup) throws IOException {
    // when
    final var deletedFile = backup.segments().files().stream().findFirst().orElseThrow();
    Files.delete(deletedFile);

    // then
    Assertions.assertThat(getStore().save(backup))
        .failsWithin(Duration.ofMinutes(1))
        .withThrowableOfType(Throwable.class)
        .withRootCauseInstanceOf(NoSuchFileException.class)
        .withMessageContaining(deletedFile.toString());
  }
}
