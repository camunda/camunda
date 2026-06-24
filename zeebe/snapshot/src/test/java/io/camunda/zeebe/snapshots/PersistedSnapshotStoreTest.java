/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PersistedSnapshotStoreTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public ActorSchedulerRule scheduler = new ActorSchedulerRule();

  private FileBasedSnapshotStore persistedSnapshotStore;

  @Before
  public void before() {
    final var partitionId = 1;
    final var root = temporaryFolder.getRoot();

    final var snapshotStore =
        new FileBasedSnapshotStore(
            0, partitionId, root.toPath(), snapshotPath -> Map.of(), new SimpleMeterRegistry());
    scheduler.submitActor(snapshotStore).join();
    persistedSnapshotStore = snapshotStore;
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
    final var transientSnapshot =
        persistedSnapshotStore.newReceivedSnapshot("1-0-123-121-1").join();

    // then
    assertThat(transientSnapshot.index()).isEqualTo(index);
  }

  @Test
  public void shouldListAllFiles() {
    // given
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(1, 0, 123, 121, false).get();
    final var snapshotFileNames =
        List.of("zeebe.metadata", "table-0.sst", "table-1.sst", "table-2.sst");
    transientSnapshot
        .take(
            path -> {
              try {
                FileUtil.ensureDirectoryExists(path);
                for (final var filename : snapshotFileNames) {
                  final var file = new File(path.toString(), filename);
                  file.createNewFile();
                  try (final var fos = new FileOutputStream(file)) {
                    fos.write("test".getBytes());
                  }
                }
              } catch (final IOException e) {
                throw new RuntimeException(e);
              }
            })
        .join();
    final var persistedSnapshot = transientSnapshot.persist().join();

    // when
    final var files = persistedSnapshot.files();

    // then
    final var expectedFiles =
        new HashMap<>(
            snapshotFileNames.stream()
                .collect(Collectors.toMap(f -> f, f -> persistedSnapshot.getPath().resolve(f))));
    expectedFiles.put(
        persistedSnapshot.getChecksumPath().getFileName().toString(),
        persistedSnapshot.getChecksumPath());
    assertThat(files).containsExactlyInAnyOrderEntriesOf(expectedFiles);
  }
}
