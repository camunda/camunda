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
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.snapshots.SnapshotTransferUtil;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SnapshotTransferTest {

  @AutoClose
  static final ActorScheduler scheduler =
      new ActorScheduler.ActorSchedulerBuilder().setIoBoundActorThreadCount(2).build();

  @TempDir Path temporaryFolder;
  @AutoClose ConstructableSnapshotStore senderSnapshotStore;
  @AutoClose ReceivableSnapshotStore receiverSnapshotStore;
  private SnapshotTransferService transferService;
  private SnapshotTransfer snapshotTransfer;

  @BeforeAll
  public static void beforeAll() {
    scheduler.start();
  }

  @BeforeEach
  public void beforeEach() throws Exception {
    final int partitionId = 1;
    final var senderDirectory = temporaryFolder.resolve("sender");
    final var receiverDirectory = temporaryFolder.resolve("receiver");

    senderSnapshotStore =
        new FileBasedSnapshotStore(
            0, partitionId, senderDirectory, snapshotPath -> Map.of(), new SimpleMeterRegistry());
    scheduler.submitActor((Actor) senderSnapshotStore).join();

    receiverSnapshotStore =
        new FileBasedSnapshotStore(
            0, partitionId, receiverDirectory, snapshotPath -> Map.of(), new SimpleMeterRegistry());

    scheduler.submitActor((Actor) receiverSnapshotStore).join();

    transferService = new SnapshotTransferServiceImpl(senderSnapshotStore, 1);
    snapshotTransfer =
        new SnapshotTransfer(transferService, receiverSnapshotStore, (Actor) senderSnapshotStore);
  }

  @Test
  public void shouldTransferSnapshot() {
    // given
    final int partitionId = 1;
    SnapshotTransferUtil.takePersistedSnapshot(
        senderSnapshotStore, SnapshotTransferUtil.SNAPSHOT_FILE_CONTENTS);
    // when
    final var persistedSnapshot = snapshotTransfer.getLatestSnapshot(partitionId).join();
    // then
    assertThat(persistedSnapshot.getId())
        .isEqualTo(senderSnapshotStore.getLatestSnapshot().get().getId());
  }
}
