/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.db.impl.DefaultColumnFamily;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.zeebe.logstreams.util.RocksDBWrapper;
import io.zeebe.logstreams.util.TestSnapshotStorage;
import io.zeebe.test.util.AutoCloseableRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import org.agrona.collections.MutableLong;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

@SuppressWarnings("unchecked")
public final class StateSnapshotControllerTest {
  @Rule public final TemporaryFolder tempFolderRule = new TemporaryFolder();
  @Rule public final AutoCloseableRule autoCloseableRule = new AutoCloseableRule();

  private final MutableLong exporterPosition = new MutableLong(Long.MAX_VALUE);

  private StateSnapshotController snapshotController;
  private SnapshotStorage storage;

  @Before
  public void setup() throws IOException {
    final var rootDirectory = tempFolderRule.newFolder("state").toPath();
    storage = new TestSnapshotStorage(rootDirectory);

    snapshotController =
        new StateSnapshotController(
            ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class),
            storage,
            new NoneSnapshotReplication(),
            db -> exporterPosition.get());

    autoCloseableRule.manage(snapshotController);
    autoCloseableRule.manage(storage);
  }

  @Test
  public void shouldNotTakeSnapshotIfDbIsClosed() {
    // given

    // then
    assertThat(snapshotController.isDbOpened()).isFalse();
    assertThat(snapshotController.takeSnapshot(1)).isEmpty();
  }

  @Test
  public void shouldTakeTempSnapshotWithExporterPosition() {
    // given
    final var snapshotPosition = 1;
    exporterPosition.set(snapshotPosition - 1);
    snapshotController.openDb();

    // when
    final var tmpSnapshot = snapshotController.takeTempSnapshot(snapshotPosition);

    // then
    assertThat(tmpSnapshot).map(Snapshot::getCompactionBound).hasValue(exporterPosition.get());
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
    wrapper.wrap(snapshotController.openDb());
    wrapper.putInt(key, value);
    final var tmpSnapshot = snapshotController.takeTempSnapshot(snapshotPosition);
    snapshotController.commitSnapshot(tmpSnapshot.orElseThrow());
    snapshotController.close();
    wrapper.wrap(snapshotController.openDb());

    // then
    assertThat(wrapper.getInt(key)).isEqualTo(value);
  }

  @Test
  public void shouldTakeSnapshotWithExporterPosition() {
    // given
    final var snapshotPosition = 1;
    exporterPosition.set(snapshotPosition - 1);
    snapshotController.openDb();

    // when
    final var snapshot = snapshotController.takeSnapshot(snapshotPosition);

    // then
    assertThat(snapshot).map(Snapshot::getCompactionBound).hasValue(exporterPosition.get());
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
    wrapper.wrap(snapshotController.openDb());
    wrapper.putInt(key, value);
    snapshotController.takeSnapshot(snapshotPosition);
    snapshotController.close();
    wrapper.wrap(snapshotController.openDb());

    // then
    assertThat(wrapper.getInt(key)).isEqualTo(value);
  }

  @Test
  public void shouldDoNothingIfNoSnapshotsToRecoverFrom() throws Exception {
    // given

    // when
    snapshotController.recover();

    // then
    assertThat(snapshotController.isDbOpened()).isFalse();
  }

  @Test
  public void shouldRemovePreExistingDatabaseOnRecover() throws Exception {
    // given
    final String key = "test";
    final int value = 1;
    final RocksDBWrapper wrapper = new RocksDBWrapper();

    // when
    wrapper.wrap(snapshotController.openDb());
    wrapper.putInt(key, value);
    snapshotController.close();
    snapshotController.recover();
    assertThat(snapshotController.isDbOpened()).isFalse();
    wrapper.wrap(snapshotController.openDb());

    // then
    assertThat(wrapper.mayExist(key)).isFalse();
  }

  @Test
  public void shouldRecoverFromLatestSnapshot() throws Exception {
    // given two snapshots
    final RocksDBWrapper wrapper = new RocksDBWrapper();
    wrapper.wrap(snapshotController.openDb());

    wrapper.putInt("x", 1);
    snapshotController.takeSnapshot(1);

    wrapper.putInt("x", 2);
    snapshotController.takeSnapshot(2);

    wrapper.putInt("x", 3);
    snapshotController.takeSnapshot(3);

    snapshotController.close();

    // when
    snapshotController.recover();
    wrapper.wrap(snapshotController.openDb());

    // then
    assertThat(wrapper.getInt("x")).isEqualTo(3);
  }

  @Test
  public void shouldRecoverFromLatestNotCorruptedSnapshot() throws Exception {
    // given two snapshots
    final RocksDBWrapper wrapper = new RocksDBWrapper();
    wrapper.wrap(snapshotController.openDb());

    wrapper.putInt("x", 1);
    snapshotController.takeSnapshot(1);

    wrapper.putInt("x", 2);
    snapshotController.takeSnapshot(2);

    snapshotController.close();
    corruptLatestSnapshot();

    // when
    snapshotController.recover();
    wrapper.wrap(snapshotController.openDb());

    // then
    assertThat(wrapper.getInt("x")).isEqualTo(1);
  }

  @Test
  public void shouldFailToRecoverIfAllSnapshotsAreCorrupted() throws Exception {
    // given two snapshots
    final RocksDBWrapper wrapper = new RocksDBWrapper();

    wrapper.wrap(snapshotController.openDb());
    wrapper.putInt("x", 1);

    snapshotController.takeSnapshot(1);
    snapshotController.close();
    corruptLatestSnapshot();

    // when/then
    assertThatThrownBy(() -> snapshotController.recover())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to recover from snapshots");
  }

  @Test
  public void shouldGetValidSnapshotCount() {
    // given
    snapshotController.openDb();

    assertThat(snapshotController.getValidSnapshotsCount()).isEqualTo(0);

    snapshotController.takeSnapshot(1L);
    snapshotController.takeSnapshot(3L);
    snapshotController.takeSnapshot(5L);
    snapshotController.takeTempSnapshot(6L);

    // when/then
    assertThat(snapshotController.getValidSnapshotsCount()).isEqualTo(3);
  }

  @Test
  public void shouldGetLastValidSnapshot() {
    // given
    snapshotController.openDb();

    assertThat(snapshotController.getLastValidSnapshotDirectory()).isNull();

    snapshotController.takeSnapshot(1L);
    snapshotController.takeSnapshot(3L);
    final var lastValidSnapshot = snapshotController.takeSnapshot(5L).orElseThrow();
    final var lastTempSnapshot = snapshotController.takeTempSnapshot(6L).orElseThrow();

    // when/then
    assertThat(snapshotController.getLastValidSnapshotDirectory().toPath())
        .isEqualTo(lastValidSnapshot.getPath())
        .isNotEqualTo(lastTempSnapshot.getPath());
  }

  private void corruptLatestSnapshot() throws IOException {
    final var snapshot = storage.getLatestSnapshot();
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
