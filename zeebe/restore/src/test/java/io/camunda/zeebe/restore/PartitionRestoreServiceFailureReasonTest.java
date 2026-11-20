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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParseException;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.restore.PartitionRestoreService.BackupValidator;
import io.camunda.zeebe.restore.PartitionRestoreService.BackupValidator.BackupNotValidException;
import io.camunda.zeebe.restore.RestoreFailure.Reason;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The restore app exits with a code per {@link Reason}, so each failure of the checks on the
 * requested backups must name the reason a caller can act on.
 */
final class PartitionRestoreServiceFailureReasonTest {

  @TempDir Path dataDirectory;
  @AutoClose MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final BackupStore backupStore = mock(BackupStore.class);
  private PartitionRestoreService restoreService;

  @BeforeEach
  void setUp() {
    final var partitionMetadata =
        new PartitionMetadata(
            PartitionId.from("raft", 1), Set.of(), Map.of(), 1, new MemberId("1"));
    final var raftPartition =
        new RaftPartition(partitionMetadata, null, dataDirectory.toFile(), meterRegistry);
    restoreService =
        new PartitionRestoreService(
            backupStore, raftPartition, 1, snapshotPath -> Map.of(), meterRegistry);
  }

  @Test
  void shouldReportANegativeBackupIdAsAnInvalidRequest() {
    // when
    final var failure = catchThrowable(() -> restoreService.restore(-1, BackupValidator.none()));

    // then
    assertThat(failure).isInstanceOf(IllegalArgumentException.class);
    assertReason(failure, Reason.INVALID_REQUEST);
  }

  @Test
  void shouldReportAMissingBackupAsInconsistentBackups() {
    // given
    when(backupStore.list(any(BackupIdentifierWildcard.class)))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when
    final var failure = catchThrowable(() -> restoreService.restore(42, BackupValidator.none()));

    // then
    assertThat(failure).isInstanceOf(BackupNotFoundException.class);
    assertReason(failure, Reason.BACKUPS_INCONSISTENT);
  }

  @Test
  void shouldReportAnInvalidBackupAsInconsistentBackups() {
    // given
    final var status = mock(BackupStatus.class);
    when(status.statusCode()).thenReturn(BackupStatusCode.COMPLETED);
    when(backupStore.list(any(BackupIdentifierWildcard.class)))
        .thenReturn(CompletableFuture.completedFuture(List.of(status)));
    final BackupValidator validator =
        s -> {
          throw new BackupNotValidException(s, "Expected backup to have 2 partitions");
        };

    // when
    final var failure = catchThrowable(() -> restoreService.restore(42, validator));

    // then
    assertThat(failure).isInstanceOf(BackupNotValidException.class);
    assertReason(failure, Reason.BACKUPS_INCONSISTENT);
  }

  @Test
  void shouldReportAFailingBackupStoreAsUnavailable() {
    // given
    final var storeFailure = new UncheckedIOException(new IOException("Connection refused"));
    when(backupStore.list(any(BackupIdentifierWildcard.class)))
        .thenReturn(CompletableFuture.failedFuture(storeFailure));

    // when
    final var failure = catchThrowable(() -> restoreService.restore(42, BackupValidator.none()));

    // then - retrying may help
    assertThat(failure).hasCause(storeFailure).hasToString(storeFailure.toString());
    assertReason(failure, Reason.BACKUP_STORE_UNAVAILABLE);
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
    final var failure = catchThrowable(() -> restoreService.restore(42, BackupValidator.none()));

    // then - retrying does not help
    assertThat(failure).hasCause(storeFailure);
    assertReason(failure, Reason.BACKUPS_INCONSISTENT);
  }

  private static void assertReason(final Throwable failure, final Reason reason) {
    assertThat(failure)
        .isInstanceOfSatisfying(
            RestoreFailure.class, f -> assertThat(f.reason()).isEqualTo(reason));
  }
}
