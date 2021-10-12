/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class PersistedSnapshotTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public ActorSchedulerRule scheduler = new ActorSchedulerRule();

  private ConstructableSnapshotStore snapshotStore;

  @Before
  public void beforeEach() {
    final int partitionId = 1;
    final File root = temporaryFolder.getRoot();
    final FileBasedSnapshotStoreFactory factory =
        new FileBasedSnapshotStoreFactory(scheduler.get(), 1);

    factory.createReceivableSnapshotStore(root.toPath(), partitionId);
    snapshotStore = factory.getConstructableSnapshotStore(partitionId);
  }

  @Test
  public void shouldDeleteSnapshot() {
    // given
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1L, 2L, 3L, 4L).get();
    transientSnapshot.take(this::writeSnapshot);
    final var persistedSnapshot = transientSnapshot.persist().join();

    // when
    persistedSnapshot.delete();

    // then
    assertThat(persistedSnapshot.getPath()).doesNotExist();
  }

  private boolean writeSnapshot(final Path path) {
    try {
      FileUtil.ensureDirectoryExists(path);
      Files.writeString(path.resolve("file"), "contents");
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return true;
  }
}
