/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.restore.RestoreFailure.BackupStoreFailure;
import io.camunda.zeebe.restore.RestoreFailure.InvalidArgument;
import io.camunda.zeebe.restore.RestoreFailure.Reason;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

/** The exit codes are a documented contract for callers that automate a restore. */
final class RestoreExitCodesTest {

  private final RestoreExitCodes exitCodes = new RestoreExitCodes();

  @Test
  void shouldExitWithTenForAnInvalidRequest() {
    // when
    final var exitCode = exitCodes.getExitCode(new InvalidArgument("No backups to restore"));

    // then
    assertThat(exitCode).isEqualTo(10);
  }

  @Test
  void shouldExitWithElevenForARestorePointTheBackupsCannotServe() {
    // when
    final var exitCode =
        exitCodes.getExitCode(new Failure(Reason.RESTORE_POINT_UNAVAILABLE, "No usable range"));

    // then
    assertThat(exitCode).isEqualTo(11);
  }

  @Test
  void shouldExitWithTwelveForAnUnavailableBackupStore() {
    // when
    final var exitCode =
        exitCodes.getExitCode(
            new BackupStoreFailure(
                Reason.BACKUP_STORE_UNAVAILABLE, new IOException("Connection refused")));

    // then
    assertThat(exitCode).isEqualTo(12);
  }

  @Test
  void shouldExitWithThirteenForAMissingBackup() {
    // when
    final var exitCode = exitCodes.getExitCode(new BackupNotFoundException(42));

    // then
    assertThat(exitCode).isEqualTo(13);
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
    final var failure = new ExecutionException(new BackupNotFoundException(42));

    // when
    final var exitCode = exitCodes.getExitCode(failure);

    // then
    assertThat(exitCode).isEqualTo(13);
  }

  private static final class Failure extends RuntimeException implements RestoreFailure {
    private final Reason reason;

    private Failure(final Reason reason, final String message) {
      super(message);
      this.reason = reason;
    }

    @Override
    public Reason reason() {
      return reason;
    }
  }
}
