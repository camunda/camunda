/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.management;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

final class BackupServiceTest {

  @RegisterExtension
  final ControlledActorSchedulerExtension actorScheduler = new ControlledActorSchedulerExtension();

  @TempDir Path segmentsDirectory;

  private final PersistedSnapshotStore snapshotStore = mock(PersistedSnapshotStore.class);
  private final BackupStore backupStore = mock(BackupStore.class);
  private BackupService backupService;

  @BeforeEach
  void setUp() {
    backupService =
        new BackupService(
            1,
            1,
            backupStore,
            snapshotStore,
            segmentsDirectory,
            index -> CompletableFuture.completedFuture(null),
            new SimpleMeterRegistry(),
            null,
            null,
            null);
    actorScheduler.submitActor(backupService);
    actorScheduler.workUntilDone();
  }

  @Test
  void shouldReleaseCheckpointSnapshotReservationsWhenClosed() {
    // when
    backupService.closeAsync();
    actorScheduler.workUntilDone();

    // then
    verify(snapshotStore).releaseAllReservations();
  }

  @Test
  void shouldReleaseSnapshotReservedForCheckpointWhenCreatingFailedBackup() {
    // given
    when(backupStore.markFailed(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(BackupStatusCode.FAILED));
    final var descriptor =
        new BackupDescriptorImpl(
            "reserved-snapshot", 10, 1, "8.9.0", Instant.now(), CheckpointType.MANUAL_BACKUP);

    // when
    backupService.createFailedBackup(1L, descriptor, "scaling in progress");
    actorScheduler.workUntilDone();

    // then
    verify(snapshotStore).releaseReservation(1L, "reserved-snapshot");
  }
}
