/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.snapshots.broker.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.snapshots.raft.ReceivableSnapshotStore;
import io.zeebe.util.sched.ActorScheduler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PersistedSnapshotStoreTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private ReceivableSnapshotStore persistedSnapshotStore;

  @Before
  public void before() {
    final FileBasedSnapshotStoreFactory factory =
        new FileBasedSnapshotStoreFactory(createActorScheduler());

    final var partitionName = "1";
    final var root = temporaryFolder.getRoot();

    persistedSnapshotStore = factory.createReceivableSnapshotStore(root.toPath(), partitionName);
  }

  private ActorScheduler createActorScheduler() {
    final var actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();
    return actorScheduler;
  }

  @Test
  public void shouldReturnZeroWhenNoSnapshotWasTaken() {
    // given

    // when
    final var currentSnapshotIndex = persistedSnapshotStore.getCurrentSnapshotIndex();

    // then
    assertThat(currentSnapshotIndex).isEqualTo(0);
  }

  @Test
  public void shouldReturnEmptyWhenNoSnapshotWasTaken() {
    // given

    // when
    final var optionalLatestSnapshot = persistedSnapshotStore.getLatestSnapshot();

    // then
    assertThat(optionalLatestSnapshot).isEmpty();
  }

  @Test
  public void shouldReturnFalseOnNonExistingSnapshot() {
    // given

    // when
    final var exists = persistedSnapshotStore.hasSnapshotId("notexisting");

    // then
    assertThat(exists).isFalse();
  }

  @Test
  public void shouldCreateSubFoldersOnCreatingDirBasedStore() {
    // given

    // when + then
    assertThat(
            temporaryFolder
                .getRoot()
                .toPath()
                .resolve(FileBasedSnapshotStoreFactory.SNAPSHOTS_DIRECTORY))
        .exists();
    assertThat(
            temporaryFolder
                .getRoot()
                .toPath()
                .resolve(FileBasedSnapshotStoreFactory.PENDING_DIRECTORY))
        .exists();
  }

  @Test
  public void shouldTakeReceivedSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);

    // when
    final var transientSnapshot = persistedSnapshotStore.newReceivedSnapshot("1-0-123");

    // then
    assertThat(transientSnapshot.index()).isEqualTo(index);
  }
}
