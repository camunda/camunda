/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.restore.RestoreFailure.BackupNotFound;
import io.camunda.zeebe.restore.RestoreFailure.BackupStoreFailure;
import io.camunda.zeebe.restore.RestoreFailure.InvalidArgument;
import io.camunda.zeebe.restore.RestoreFailure.InvalidState;
import io.camunda.zeebe.restore.RestoreFailure.Reason;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

/** The exit codes are a documented contract for callers that automate a restore. */
final class RestoreExitCodesTest {

  private final RestoreExitCodes exitCodes = new RestoreExitCodes();

  @Test
  void shouldExitWithTwoForAnInvalidRequest() {
    // when
    final var exitCode = exitCodes.getExitCode(new InvalidArgument("from is after to"));

    // then
    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  void shouldExitWithTwoForAnInvalidConfiguration() {
    // when
    final var exitCode =
        exitCodes.getExitCode(new InvalidState(Reason.INVALID_REQUEST, "no backup store"));

    // then
    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  void shouldExitWithThreeForARestorePointTheBackupsCannotServe() {
    // when
    final var exitCode =
        exitCodes.getExitCode(
            new InvalidState(Reason.RESTORE_POINT_UNAVAILABLE, "No usable range found"));

    // then
    assertThat(exitCode).isEqualTo(3);
  }

  @Test
  void shouldExitWithFiveForAMissingBackup() {
    // when
    final var exitCode = exitCodes.getExitCode(new BackupNotFound("No completed backup found"));

    // then
    assertThat(exitCode).isEqualTo(5);
  }

  @Test
  void shouldExitWithFiveForInconsistentBackups() {
    // when
    final var exitCode =
        exitCodes.getExitCode(
            new InvalidState(Reason.BACKUPS_INCONSISTENT, "Unexpected data gaps between backups"));

    // then
    assertThat(exitCode).isEqualTo(5);
  }

  @Test
  void shouldExitWithFourForAnUnavailableBackupStore() {
    // when
    final var exitCode =
        exitCodes.getExitCode(
            new BackupStoreFailure(
                Reason.BACKUP_STORE_UNAVAILABLE, new IOException("Connection refused")));

    // then
    assertThat(exitCode).isEqualTo(4);
  }

  @Test
  void shouldExitWithOneForAnyOtherFailure() {
    // when
    final var exitCode = exitCodes.getExitCode(new IllegalStateException("Disk full"));

    // then
    assertThat(exitCode).isEqualTo(1);
  }

  @Test
  void shouldFindTheReasonBehindTheExceptionsWrappingIt() {
    // given - a failed partition restore task is wrapped by its future
    final var failure =
        new ExecutionException(
            new InvalidState(Reason.RESTORE_POINT_UNAVAILABLE, "No usable range found"));

    // when
    final var exitCode = exitCodes.getExitCode(failure);

    // then
    assertThat(exitCode).isEqualTo(3);
  }
}
