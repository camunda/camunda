/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PersistedSnapshotStoreTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public ActorSchedulerRule scheduler = new ActorSchedulerRule();

  private ReceivableSnapshotStore persistedSnapshotStore;

  @Before
  public void before() {
    final FileBasedSnapshotStoreFactory factory =
        new FileBasedSnapshotStoreFactory(scheduler.get(), 1);

    final var partitionId = 1;
    final var root = temporaryFolder.getRoot();

    persistedSnapshotStore = factory.createReceivableSnapshotStore(root.toPath(), partitionId);
  }

  @Test
  public void shouldReturnZeroWhenNoSnapshotWasTaken() {
    // given

    // when
    final var currentSnapshotIndex = persistedSnapshotStore.getCurrentSnapshotIndex();

    // then
    assertThat(currentSnapshotIndex).isZero();
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
  public void shouldTakeReceivedSnapshot() {
    // given
    final var index = 1L;

    // when
    final var transientSnapshot = persistedSnapshotStore.newReceivedSnapshot("1-0-123-121");

    // then
    assertThat(transientSnapshot.index()).isEqualTo(index);
  }
}
