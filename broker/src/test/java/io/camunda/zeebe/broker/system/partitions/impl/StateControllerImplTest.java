/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.camunda.zeebe.broker.system.partitions.TestIndexedRaftLogEntry;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.snapshots.PersistableSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotMetadata;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.snapshots.impl.SnapshotMetrics;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Optional;
import org.agrona.collections.MutableLong;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class StateControllerImplTest {

  @Rule public final TemporaryFolder tempFolderRule = new TemporaryFolder();
  @Rule public final AutoCloseableRule autoCloseableRule = new AutoCloseableRule();
  @Rule public final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();

  private final MutableLong exporterPosition = new MutableLong(Long.MAX_VALUE);
  private StateControllerImpl snapshotController;
  private FileBasedSnapshotStore store;
  private Path runtimeDirectory;

  @Before
  public void setup() throws IOException {

    store =
        new FileBasedSnapshotStore(
            1,
            1,
            new SnapshotMetrics("partition-1"),
            tempFolderRule.newFolder("snapshots").toPath(),
            tempFolderRule.newFolder("pending").toPath());
    actorSchedulerRule.submitActor(store).join();

    runtimeDirectory = tempFolderRule.getRoot().toPath().resolve("runtime");
    snapshotController =
        new StateControllerImpl(
            ZeebeRocksDbFactory.newFactory(),
            store,
            runtimeDirectory,
            l ->
                Optional.of(
                    new TestIndexedRaftLogEntry(
                        l, 1, new ApplicationEntry(1, 10, new UnsafeBuffer()))),
            db -> exporterPosition.get(),
            store);

    autoCloseableRule.manage(snapshotController);
  }

  @Test
  public void shouldNotTakeSnapshotIfDbIsClosed() {
    // given

    // then
    assertThat(snapshotController.isDbOpened()).isFalse();
    assertThat(snapshotController.takeTransientSnapshot(1).join()).isEmpty();
  }

  @Test
  public void shouldTakeTempSnapshotWithExporterPosition() {
    // given
    final var snapshotPosition = 1;
    exporterPosition.set(snapshotPosition - 1);
    snapshotController.recover().join();

    // when
    final var tmpSnapshot = snapshotController.takeTransientSnapshot(snapshotPosition).join();
    final var snapshot =
        tmpSnapshot.map(TransientSnapshot::persist).map(ActorFuture::join).orElseThrow();

    // then
    assertThat(snapshot)
        .extracting(PersistedSnapshot::getCompactionBound)
        .isEqualTo(exporterPosition.get());
  }

  @Test
  public void shouldTakeTempSnapshot() throws Exception {
    // given
    final var key = "test";
    final var value = 3;
    final var wrapper = new RocksDBWrapper();
    final var snapshotPosition = 2L;
    exporterPosition.set(snapshotPosition + 1);

    // when
    wrapper.wrap(snapshotController.recover().join());
    wrapper.putInt(key, value);
    final var tmpSnapshot = snapshotController.takeTransientSnapshot(snapshotPosition).join();
    tmpSnapshot.orElseThrow().persist().join();
    snapshotController.close();
    wrapper.wrap(snapshotController.recover().join());

    // then
    assertThat(wrapper.getInt(key)).isEqualTo(value);
  }

  @Test
  public void shouldTakeSnapshotWithExporterPosition() {
    // given
    final var snapshotPosition = 1;
    exporterPosition.set(snapshotPosition - 1);
    snapshotController.recover();

    // when
    final var snapshot = takeSnapshot(snapshotPosition);

    // then
    assertThat(snapshot.getName()).contains(exporterPosition.toString());
  }

  @Test
  public void shouldTakeSnapshot() throws Exception {
    // given
    final var key = "test";
    final var value = 3;
    final var wrapper = new RocksDBWrapper();
    final var snapshotPosition = 2;
    exporterPosition.set(snapshotPosition + 1);

    // when
    wrapper.wrap(snapshotController.recover().join());
    wrapper.putInt(key, value);
    takeSnapshot(snapshotPosition);
    snapshotController.close();
    wrapper.wrap(snapshotController.recover().join());

    // then
    assertThat(wrapper.getInt(key)).isEqualTo(value);
  }

  @Test
  public void shouldTakeSnapshotWhenExporterPositionNotChanged() {
    // given
    final var snapshotPosition = 2;
    exporterPosition.set(snapshotPosition - 1);
    snapshotController.recover().join();
    final var firstSnapshot =
        snapshotController
            .takeTransientSnapshot(snapshotPosition)
            .join()
            .map(PersistableSnapshot::persist)
            .map(ActorFuture::join)
            .orElseThrow();

    // when
    final var tmpSnapshot = snapshotController.takeTransientSnapshot(snapshotPosition + 1).join();
    final var snapshot =
        tmpSnapshot.map(TransientSnapshot::persist).map(ActorFuture::join).orElseThrow();

    // then
    assertThat(snapshot)
        .extracting(PersistedSnapshot::getCompactionBound)
        .isEqualTo(firstSnapshot.getCompactionBound());
    assertThat(snapshot.getId()).isNotEqualTo(firstSnapshot.getId());
    final var newSnapshotId = FileBasedSnapshotMetadata.ofFileName(snapshot.getId()).orElseThrow();
    final var firstSnapshotId =
        FileBasedSnapshotMetadata.ofFileName(firstSnapshot.getId()).orElseThrow();
    assertThat(firstSnapshotId).isLessThan(newSnapshotId);
  }

  @Test
  public void shouldTakeSnapshotWhenProcessorPositionNotChanged() {
    // given
    final var snapshotPosition = 2;
    exporterPosition.set(snapshotPosition);
    snapshotController.recover().join();
    final var firstSnapshot =
        snapshotController
            .takeTransientSnapshot(snapshotPosition)
            .join()
            .map(PersistableSnapshot::persist)
            .map(ActorFuture::join)
            .orElseThrow();

    // when
    exporterPosition.set(snapshotPosition + 1);
    final var tmpSnapshot = snapshotController.takeTransientSnapshot(snapshotPosition).join();
    final var snapshot =
        tmpSnapshot.map(TransientSnapshot::persist).map(ActorFuture::join).orElseThrow();

    // then
    assertThat(snapshot)
        .extracting(PersistedSnapshot::getCompactionBound)
        .isEqualTo(firstSnapshot.getCompactionBound());
    assertThat(snapshot.getId()).isNotEqualTo(firstSnapshot.getId());
    final var newSnapshotId = FileBasedSnapshotMetadata.ofFileName(snapshot.getId()).orElseThrow();
    final var firstSnapshotId =
        FileBasedSnapshotMetadata.ofFileName(firstSnapshot.getId()).orElseThrow();
    assertThat(firstSnapshotId).isLessThan(newSnapshotId);
  }

  @Test
  public void shouldOpenEmptyDatabaseWhenNoSnapshotsToRecoverFrom() {
    // given

    // when
    snapshotController.recover().join();

    // then
    assertThat(snapshotController.isDbOpened()).isTrue();
  }

  @Test
  public void shouldRecoverFromLatestSnapshot() throws Exception {
    // given two snapshots
    final RocksDBWrapper wrapper = new RocksDBWrapper();
    wrapper.wrap(snapshotController.recover().join());

    wrapper.putInt("x", 1);
    takeSnapshot(1);

    wrapper.putInt("x", 2);
    takeSnapshot(2);

    wrapper.putInt("x", 3);
    takeSnapshot(3);

    snapshotController.close();

    // when
    wrapper.wrap(snapshotController.recover().join());

    // then
    assertThat(wrapper.getInt("x")).isEqualTo(3);
  }

  @Test
  public void shouldFailToRecoverIfSnapshotIsCorrupted() throws Exception {
    // given two snapshots
    final RocksDBWrapper wrapper = new RocksDBWrapper();

    wrapper.wrap(snapshotController.recover().join());
    wrapper.putInt("x", 1);

    takeSnapshot(1);
    snapshotController.close();
    corruptLatestSnapshot();

    // when/then
    assertThatThrownBy(() -> snapshotController.recover().join())
        .hasCauseInstanceOf(RuntimeException.class);
  }

  @Test
  public void shouldDeleteRuntimeFolderOnClose() {
    // given
    snapshotController.recover().join();

    // when
    snapshotController.closeDb().join();

    // then
    assertThat(runtimeDirectory).doesNotExist();
  }

  @Test
  public void shouldNotTakeSnapshotWhenDbIsClosed() {
    // given
    snapshotController.recover().join();

    // when
    final var closed = snapshotController.closeDb();
    final var snapshotTaken = snapshotController.takeTransientSnapshot(1);
    closed.join();

    // then
    assertThat(snapshotTaken.join()).isEmpty();
  }

  @Test
  public void shouldCloseDbOnlyAfterTakingSnapshot() {
    // given
    snapshotController.recover().join();

    // when
    final var snapshotTaken = snapshotController.takeTransientSnapshot(1);
    final var closed = snapshotController.closeDb();
    closed.join();

    // then
    assertThat(snapshotTaken.join()).isNotEmpty();
  }

  private File takeSnapshot(final long position) {
    final var snapshot = snapshotController.takeTransientSnapshot(position).join().orElseThrow();
    return snapshot.persist().join().getPath().toFile();
  }

  private void corruptLatestSnapshot() throws IOException {
    final var snapshot = store.getLatestSnapshot();
    final var path = snapshot.orElseThrow().getPath();

    try (final var files = Files.list(path)) {
      final var file =
          files
              .filter(p -> p.toString().endsWith(".sst"))
              .max(Comparator.naturalOrder())
              .orElseThrow();
      Files.write(file, "<--corrupted-->".getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
    }
  }
}
