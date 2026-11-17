/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreParameters;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.TenantRestoreArguments;
import io.camunda.zeebe.restore.RestoreFailure;
import io.camunda.zeebe.restore.RestoreFailure.Reason;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Every failure {@link RestoreValidator#validate} reports names a {@link Reason}, which the
 * standalone restore app exits with, while keeping the exception type the cluster restore API maps
 * its response from.
 */
final class RestoreValidatorFailureReasonTest {

  private final BackupStore backupStore = mock(BackupStore.class);

  @Test
  void shouldReportAnInvalidRequestAsAnIllegalArgument() {
    // given
    final var validator = new RestoreValidator(1, backupStore, partition -> 1L);

    // when
    final var failure =
        validator
            .validate(request(List.of(1L), "2026-01-01T10:00:00Z", null, "rdbms", true))
            .getLeft();

    // then
    assertThat(failure).isInstanceOf(IllegalArgumentException.class);
    assertReason(failure, Reason.INVALID_REQUEST);
  }

  @Test
  void shouldReportAMissingBackupAsNotFound() {
    // given
    when(backupStore.list(any(BackupIdentifierWildcard.class)))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    final var validator = new RestoreValidator(1, backupStore, null);

    // when
    final var failure =
        validator.validate(request(List.of(42L), null, null, "rdbms", false)).getLeft();

    // then
    assertThat(failure).isInstanceOf(NoSuchElementException.class);
    assertReason(failure, Reason.BACKUPS_INCONSISTENT);
  }

  @Test
  void shouldReportMissingBackupMetadataAsAnIllegalState() {
    // given
    when(backupStore.loadBackupMetadata(1))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
    final var validator = new RestoreValidator(1, backupStore, partition -> 1L);

    // when
    final var failure = validator.validate(request(List.of(), null, null, "rdbms", true)).getLeft();

    // then
    assertThat(failure).isInstanceOf(IllegalStateException.class);
    assertReason(failure, Reason.BACKUPS_INCONSISTENT);
  }

  @Test
  void shouldReportAFailingBackupStoreAsUnavailableWithItsOwnDescription() {
    // given
    final var storeFailure = new RuntimeException("Connection refused");
    when(backupStore.list(any(BackupIdentifierWildcard.class)))
        .thenReturn(CompletableFuture.failedFuture(storeFailure));
    final var validator = new RestoreValidator(1, backupStore, null);

    // when
    final var failure =
        validator.validate(request(List.of(42L), null, null, "rdbms", false)).getLeft();

    // then - none of the types the cluster restore API maps specifically, described as the store
    // failure itself, so the API answers with the same internal error as before
    assertThat(failure)
        .isNotInstanceOfAny(
            IllegalArgumentException.class,
            IllegalStateException.class,
            NoSuchElementException.class)
        .hasRootCause(storeFailure)
        .hasToString(storeFailure.toString());
    assertReason(failure, Reason.BACKUP_STORE_UNAVAILABLE);
  }

  @Test
  void shouldReportACheckedBackupStoreFailureAsAnIllegalState() {
    // given
    final var storeFailure = new IOException("Disk unavailable");
    when(backupStore.list(any(BackupIdentifierWildcard.class)))
        .thenReturn(CompletableFuture.failedFuture(storeFailure));
    final var validator = new RestoreValidator(1, backupStore, null);

    // when
    final var failure =
        validator.validate(request(List.of(42L), null, null, "rdbms", false)).getLeft();

    // then
    assertThat(failure)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Failed to resolve backups")
        .hasCause(storeFailure);
    assertReason(failure, Reason.BACKUP_STORE_UNAVAILABLE);
  }

  private static void assertReason(final Exception failure, final Reason reason) {
    assertThat(failure)
        .isInstanceOfSatisfying(
            RestoreFailure.class, f -> assertThat(f.reason()).isEqualTo(reason));
  }

  private static RestoreRequest request(
      final List<Long> backupIds,
      final @Nullable String from,
      final @Nullable String to,
      final String databaseType,
      final boolean continuousBackups) {
    return new RestoreRequest(
        "default",
        new TenantRestoreArguments(
            new RestoreParameters(backupIds, from, to), databaseType, continuousBackups),
        false);
  }
}
