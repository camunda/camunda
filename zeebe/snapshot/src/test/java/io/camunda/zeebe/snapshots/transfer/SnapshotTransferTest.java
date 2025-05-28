/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.snapshots.SnapshotTransferUtil;
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
  ConstructableSnapshotStore senderSnapshotStore;
  ReceivableSnapshotStore receiverSnapshotStore;
  private SnapshotTransfer snapshotTransfer;

  @BeforeEach
  public void beforeEach() throws Exception {
    final int partitionId = 1;
    final var senderDirectory = temporaryFolder.resolve("sender");
    final var receiverDirectory = temporaryFolder.resolve("receiver");

    senderSnapshotStore =
        new FileBasedSnapshotStore(
            0, partitionId, senderDirectory, snapshotPath -> Map.of(), new SimpleMeterRegistry());
    actorScheduler.submitActor((Actor) senderSnapshotStore);
    actorScheduler.workUntilDone();

    receiverSnapshotStore =
        new FileBasedSnapshotStore(
            0, partitionId, receiverDirectory, snapshotPath -> Map.of(), new SimpleMeterRegistry());

    actorScheduler.submitActor((Actor) receiverSnapshotStore);
    actorScheduler.workUntilDone();
    final SnapshotTransferService transferService =
        new SnapshotTransferServiceImpl(senderSnapshotStore, 1);
    snapshotTransfer =
        new SnapshotTransfer(transferService, receiverSnapshotStore, (Actor) senderSnapshotStore);

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
            (Actor) receiverSnapshotStore);
    actorScheduler.workUntilDone();
    assertThat(takeSnapshotFuture).succeedsWithin(Duration.ofSeconds(3));
    // when
    final var persistedSnapshotFuture = snapshotTransfer.getLatestSnapshot(partitionId);

    actorScheduler.workUntilDone();

    // then
    assertThat(persistedSnapshotFuture)
        .succeedsWithin(Duration.ofSeconds(30))
        .satisfies(
            snapshot ->
                assertThat(snapshot.getId())
                    .isEqualTo(senderSnapshotStore.getLatestSnapshot().get().getId()));
  }
}
