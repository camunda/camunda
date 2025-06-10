/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.snapshots.SnapshotCopyUtil;
import io.camunda.zeebe.snapshots.SnapshotTransferUtil;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotMetadata;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

public class SnapshotTransferTest {

  @RegisterExtension
  public final ControlledActorSchedulerExtension actorScheduler =
      new ControlledActorSchedulerExtension();

  @TempDir Path temporaryFolder;
  FileBasedSnapshotStore senderSnapshotStore;
  FileBasedSnapshotStore receiverSnapshotStore;
  private SnapshotTransferImpl snapshotTransfer;

  @BeforeEach
  public void beforeEach() throws Exception {
    final int partitionId = 1;
    final var senderDirectory = temporaryFolder.resolve("sender");
    final var receiverDirectory = temporaryFolder.resolve("receiver");

    senderSnapshotStore =
        new FileBasedSnapshotStore(
            0, partitionId, senderDirectory, snapshotPath -> Map.of(), new SimpleMeterRegistry());
    actorScheduler.submitActor(senderSnapshotStore);
    actorScheduler.workUntilDone();

    receiverSnapshotStore =
        new FileBasedSnapshotStore(
            0, partitionId, receiverDirectory, snapshotPath -> Map.of(), new SimpleMeterRegistry());

    actorScheduler.submitActor(receiverSnapshotStore);
    actorScheduler.workUntilDone();
    snapshotTransfer =
        new SnapshotTransferImpl(
            control ->
                spy(
                    new SnapshotTransferServiceImpl(
                        senderSnapshotStore, 1, SnapshotCopyUtil.copyAllFiles(), control)),
            receiverSnapshotStore);
    actorScheduler.submitActor(snapshotTransfer);

    actorScheduler.workUntilDone();
  }

  @Test
  public void shouldTransferSnapshot() {
    // given
    final int partitionId = 1;
    final var takeSnapshotFuture =
        SnapshotTransferUtil.takePersistedSnapshot(
            senderSnapshotStore,
            SnapshotTransferUtil.SNAPSHOT_FILE_CONTENTS,
            receiverSnapshotStore);
    actorScheduler.workUntilDone();
    assertThat(takeSnapshotFuture).succeedsWithin(Duration.ofSeconds(3));

    // when
    final var persistedSnapshotFuture = snapshotTransfer.getLatestSnapshot(partitionId);

    actorScheduler.workUntilDone();

    // then
    assertThat(persistedSnapshotFuture)
        .succeedsWithin(Duration.ofSeconds(30))
        .satisfies(
            snapshot -> {
              assertThat(snapshot.getId()).isEqualTo("0-0-0-0-0");
              assertThat(snapshot.getMetadata())
                  .isEqualTo(FileBasedSnapshotMetadata.forBootstrap(1));
              assertThat(snapshot.isBootstrap()).isTrue();
              assertThat(snapshot.files()).isNotEmpty();
            });
    verify((SnapshotTransferServiceImpl) snapshotTransfer.snapshotTransferService())
        .withReservation(any(), any());
  }
}
