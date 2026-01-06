/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.testkit;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import java.time.Duration;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

public interface QueryingBackupStatus {
  BackupStore getStore();

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  default void canGetStatus(final Backup backup) {
    // given
    getStore().save(backup).join();

    // when
    final var status = getStore().getStatus(backup.id());

    // then
    Assertions.assertThat(status)
        .succeedsWithin(Duration.ofSeconds(10))
        .returns(BackupStatusCode.COMPLETED, Assertions.from(BackupStatus::statusCode))
        .returns(Optional.empty(), Assertions.from(BackupStatus::failureReason))
        .returns(backup.id(), Assertions.from(BackupStatus::id))
        .returns(Optional.of(backup.descriptor()), Assertions.from(BackupStatus::descriptor));
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  default void statusIsFailedAfterMarkingAsFailed(final Backup backup) {
    // given
    getStore().save(backup).join();

    // when
    getStore().markFailed(backup.id(), "error").join();
    final var status = getStore().getStatus(backup.id());

    // then
    Assertions.assertThat(status)
        .succeedsWithin(Duration.ofSeconds(10))
        .returns(BackupStatusCode.FAILED, Assertions.from(BackupStatus::statusCode))
        .returns("error", Assertions.from(s -> s.failureReason().orElseThrow()))
        .returns(backup.id(), Assertions.from(BackupStatus::id))
        .returns(Optional.of(backup.descriptor()), Assertions.from(BackupStatus::descriptor));
  }

  @Test
  default void statusIsDoesNotExistForNonExistingBackup() {
    // when
    final var result = getStore().getStatus(new BackupIdentifierImpl(1, 1, 15));
    // then
    Assertions.assertThat(result)
        .succeedsWithin(Duration.ofSeconds(10))
        .returns(BackupStatusCode.DOES_NOT_EXIST, Assertions.from(BackupStatus::statusCode));
  }
}
