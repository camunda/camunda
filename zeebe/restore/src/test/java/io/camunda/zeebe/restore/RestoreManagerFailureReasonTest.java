/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParseException;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.restore.RestoreFailure.Reason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The restore app exits with a code per {@link Reason}, so each failure to resolve the backups to
 * restore from must name the reason a caller can act on.
 */
final class RestoreManagerFailureReasonTest {

  private static final Instant FROM = Instant.parse("2026-01-01T10:00:00Z");

  @TempDir Path dataDirectory;
  private final BackupStore backupStore = mock(BackupStore.class);
  private RestoreManager restoreManager;

  @BeforeEach
  void setUp() {
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dataDirectory.toString());
    when(backupStore.closeAsync()).thenReturn(CompletableFuture.completedFuture(null));
    restoreManager = new RestoreManager(configuration, backupStore, new SimpleMeterRegistry());
  }

  @AfterEach
  void tearDown() {
    restoreManager.close();
  }

  @Test
  void shouldReportATimeRangeWithoutFromAsAnInvalidRequest() {
    // when
    final var failure = catchThrowable(() -> restoreManager.resolveBackups(null, FROM));

    // then
    assertThat(failure).isInstanceOf(IllegalArgumentException.class);
    assertThat(reasonOf(failure)).isEqualTo(Reason.INVALID_REQUEST);
  }

  @Test
  void shouldReportAMissingBackupAsInconsistentBackups() {
    // given
    when(backupStore.list(any(BackupIdentifierWildcard.class)))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when
    final var failure = catchThrowable(() -> restoreManager.resolveBackups(new long[] {42}));

    // then
    assertThat(failure).hasRootCauseInstanceOf(BackupNotFoundException.class);
    assertThat(reasonOf(failure)).isEqualTo(Reason.BACKUPS_INCONSISTENT);
  }

  @Test
  void shouldReportAFailingBackupStoreAsUnavailable() {
    // given
    final var storeFailure = new UncheckedIOException(new IOException("Connection refused"));
    when(backupStore.list(any(BackupIdentifierWildcard.class)))
        .thenReturn(CompletableFuture.failedFuture(storeFailure));

    // when
    final var failure = catchThrowable(() -> restoreManager.resolveBackups(new long[] {42}));

    // then - retrying may help
    assertThat(failure).hasCause(storeFailure);
    assertThat(reasonOf(failure)).isEqualTo(Reason.BACKUP_STORE_UNAVAILABLE);
  }

  @Test
  void shouldReportAnUnparsableBackupManifestAsInconsistentBackups() {
    // given - the stores wrap the failure to parse a manifest in an exception of their own
    final var storeFailure =
        new UncheckedIOException(
            "Unable to read manifest", new JsonParseException(null, "Unexpected end-of-input"));
    when(backupStore.list(any(BackupIdentifierWildcard.class)))
        .thenReturn(CompletableFuture.failedFuture(storeFailure));

    // when
    final var failure = catchThrowable(() -> restoreManager.resolveBackups(new long[] {42}));

    // then - retrying does not help
    assertThat(failure).hasCause(storeFailure);
    assertThat(reasonOf(failure)).isEqualTo(Reason.BACKUPS_INCONSISTENT);
  }

  @Test
  void shouldReportMissingBackupMetadataAsInconsistentBackups() {
    // given
    when(backupStore.loadBackupMetadata(anyInt()))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    // when
    final var failure = catchThrowable(() -> restoreManager.resolveBackups(FROM, null));

    // then
    assertThat(failure).hasMessageContaining("No backup metadata found for partition 1");
    assertThat(reasonOf(failure)).isEqualTo(Reason.BACKUPS_INCONSISTENT);
  }

  /** The reason the restore app's exit code is derived from, found as the app finds it. */
  private static @Nullable Reason reasonOf(final Throwable failure) {
    final Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable current = failure;
        current != null && seen.add(current);
        current = current.getCause()) {
      if (current instanceof final RestoreFailure restoreFailure) {
        return restoreFailure.reason();
      }
    }
    return null;
  }
}
