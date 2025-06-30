/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.camunda.zeebe.broker.system.partitions.AtomixRecordEntrySupplier;
import io.camunda.zeebe.broker.system.partitions.NoEntryAtSnapshotPosition;
import io.camunda.zeebe.broker.system.partitions.TestIndexedRaftLogEntry;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotException.StateClosedException;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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

  private final AtomixRecordEntrySupplier indexedRaftLogEntry =
      l ->
          Optional.of(
              new TestIndexedRaftLogEntry(
                  l, 1, new SerializedApplicationEntry(1, 10, new UnsafeBuffer())));
  private final AtomixRecordEntrySupplier emptyEntrySupplier = l -> Optional.empty();
  private final AtomicReference<AtomixRecordEntrySupplier> atomixRecordEntrySupplier =
      new AtomicReference<>(indexedRaftLogEntry);

  @Before
  public void setup() throws IOException {
    final var meterRegistry = new SimpleMeterRegistry();
    store =
        new FileBasedSnapshotStore(
            0,
            1,
            tempFolderRule.newFolder("data").toPath(),
            snapshotPath -> Map.of(),
            meterRegistry);
    actorSchedulerRule.submitActor(store).join();

    runtimeDirectory = tempFolderRule.getRoot().toPath().resolve("runtime");
    snapshotController =
        new StateControllerImpl(
            DefaultZeebeDbFactory.defaultFactory(),
            store,
            runtimeDirectory,
            l -> atomixRecordEntrySupplier.get().getPreviousIndexedEntry(l),
            db -> exporterPosition.get(),
            store);

    autoCloseableRule.manage(snapshotController);
  }

  @Test
  public void shouldNotTakeSnapshotIfDbIsClosed() {
    // given

    // then
    assertThat(snapshotController.isDbOpened()).isFalse();
    assertThatThrownBy(() -> snapshotController.takeTransientSnapshot(1).join())
        .hasCauseInstanceOf(StateClosedException.class);
  }

  @Test
  public void shouldNotTakeSnapshotIfNoIndexedEntry() {
    // given
    atomixRecordEntrySupplier.set(emptyEntrySupplier);
    snapshotController.recover().join();

    // then
    assertThatThrownBy(() -> snapshotController.takeTransientSnapshot(1).join())
        .hasCauseInstanceOf(NoEntryAtSnapshotPosition.class);
  }

  @Test
  public void shouldTakeTempSnapshotWithExporterPosition() {
    // given
    final var snapshotPosition = 1;
    exporterPosition.set(snapshotPosition - 1);
    snapshotController.recover().join();

    // when
    final var tmpSnapshot = snapshotController.takeTransientSnapshot(snapshotPosition).join();
    final var snapshot = tmpSnapshot.persist().join();

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
    tmpSnapshot.persist().join();
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
        snapshotController.takeTransientSnapshot(snapshotPosition).join().persist().join();

    // when
    final var tmpSnapshot = snapshotController.takeTransientSnapshot(snapshotPosition + 1).join();
    final var snapshot = tmpSnapshot.persist().join();

    // then
    assertThat(snapshot)
        .extracting(PersistedSnapshot::getCompactionBound)
        .isEqualTo(firstSnapshot.getCompactionBound());
    assertThat(snapshot.getId()).isNotEqualTo(firstSnapshot.getId());
    final var newSnapshotId = FileBasedSnapshotId.ofFileName(snapshot.getId()).orElseThrow();
    final var firstSnapshotId = FileBasedSnapshotId.ofFileName(firstSnapshot.getId()).orElseThrow();
    assertThat(firstSnapshotId).isLessThan(newSnapshotId);
  }

  @Test
  public void shouldTakeSnapshotWithoutIndexedEntryWhenProcessedPositionChanged() {
    // given
    final var snapshotPosition = 2;
    exporterPosition.set(snapshotPosition - 1);
    snapshotController.recover().join();
    final var firstSnapshot =
        snapshotController.takeTransientSnapshot(snapshotPosition).join().persist().join();
    atomixRecordEntrySupplier.set(emptyEntrySupplier);

    // when
    final var snapshot =
        snapshotController.takeTransientSnapshot(snapshotPosition + 1).join().persist().join();

    // then
    assertThat(snapshot)
        .extracting(PersistedSnapshot::getCompactionBound)
        .isEqualTo(firstSnapshot.getCompactionBound());
    assertThat(snapshot.getId()).isNotEqualTo(firstSnapshot.getId());
    final var newSnapshotId = FileBasedSnapshotId.ofFileName(snapshot.getId()).orElseThrow();
    final var firstSnapshotId = FileBasedSnapshotId.ofFileName(firstSnapshot.getId()).orElseThrow();
    assertThat(newSnapshotId.getExportedPosition())
        .isEqualTo(firstSnapshotId.getExportedPosition());
    assertThat(newSnapshotId.getProcessedPosition())
        .isGreaterThan(firstSnapshotId.getProcessedPosition());
  }

  @Test
  public void shouldTakeSnapshotWhenProcessorPositionNotChanged() {
    // given
    final var snapshotPosition = 2;
    exporterPosition.set(snapshotPosition);
    snapshotController.recover().join();
    final var firstSnapshot =
        snapshotController.takeTransientSnapshot(snapshotPosition).join().persist().join();

    // when
    exporterPosition.set(snapshotPosition + 1);
    final var tmpSnapshot = snapshotController.takeTransientSnapshot(snapshotPosition).join();
    final var snapshot = tmpSnapshot.persist().join();

    // then
    assertThat(snapshot)
        .extracting(PersistedSnapshot::getCompactionBound)
        .isEqualTo(firstSnapshot.getCompactionBound());
    assertThat(snapshot.getId()).isNotEqualTo(firstSnapshot.getId());
    final var newSnapshotId = FileBasedSnapshotId.ofFileName(snapshot.getId()).orElseThrow();
    final var firstSnapshotId = FileBasedSnapshotId.ofFileName(firstSnapshot.getId()).orElseThrow();
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
    assertThatThrownBy(snapshotTaken::join).hasCauseInstanceOf(StateClosedException.class);
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
    assertThatNoException().isThrownBy(snapshotTaken::join);
  }

  @Test
  public void shouldSetExporterPositionToZero() {
    // given
    snapshotController.recover().join();

    exporterPosition.set(-1L);
    final long snapshotPosition = 5;

    // when
    final var transientSnapshot = snapshotController.takeTransientSnapshot(snapshotPosition).join();

    // then
    final var snapshot = transientSnapshot.snapshotId();
    assertThat(snapshot.getIndex()).isEqualTo(0);
    assertThat(snapshot.getTerm()).isEqualTo(0);
    assertThat(snapshot.getProcessedPosition()).isEqualTo(snapshotPosition);
    assertThat(snapshot.getExportedPosition()).isEqualTo(0);
  }

  @Test
  public void shouldKeepIndexAndTerm() {
    // given
    snapshotController.recover().join();

    final long snapshotPosition = 5;
    exporterPosition.set(4L);
    takeSnapshot(snapshotPosition);

    final var latestSnapshot = store.getLatestSnapshot().get();

    exporterPosition.set(-1L);

    // when
    final var transientSnapshot = snapshotController.takeTransientSnapshot(snapshotPosition).join();

    // then
    final var snapshot = transientSnapshot.snapshotId();
    assertThat(snapshot.getIndex()).isEqualTo(latestSnapshot.getIndex());
    assertThat(snapshot.getTerm()).isEqualTo(latestSnapshot.getTerm());
    assertThat(snapshot.getProcessedPosition()).isEqualTo(snapshotPosition);
    assertThat(snapshot.getExportedPosition()).isEqualTo(0);
  }

  private File takeSnapshot(final long position) {
    final var snapshot = snapshotController.takeTransientSnapshot(position).join();
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
