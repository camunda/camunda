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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StateSnapshotControllerTest {
  @Rule public final TemporaryFolder tempFolderRule = new TemporaryFolder();
  @Rule public final AutoCloseableRule autoCloseableRule = new AutoCloseableRule();

  private StateSnapshotController snapshotController;
  private SnapshotStorage storage;

  @Before
  public void setup() throws IOException {
    final var rootDirectory = tempFolderRule.newFolder("state").toPath();
    storage = new TestSnapshotStorage(rootDirectory);

    snapshotController =
        new StateSnapshotController(
            ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class), storage);

    autoCloseableRule.manage(snapshotController);
    autoCloseableRule.manage(storage);
  }

  @Test
  public void shouldThrowExceptionOnTakeSnapshotIfClosed() {
    // given

    // then
    assertThat(snapshotController.isDbOpened()).isFalse();
    assertThatThrownBy(() -> snapshotController.takeSnapshot(1))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldTakeSnapshot() throws Exception {
    // given
    final String key = "test";
    final int value = 3;
    final RocksDBWrapper wrapper = new RocksDBWrapper();

    // when
    wrapper.wrap(snapshotController.openDb());
    wrapper.putInt(key, value);
    snapshotController.takeSnapshot(1);
    snapshotController.close();
    wrapper.wrap(snapshotController.openDb());

    // then
    assertThat(wrapper.getInt(key)).isEqualTo(value);
  }

  @Test
  public void shouldOpenNewDatabaseIfNoSnapshotsToRecoverFrom() throws Exception {
    // given

    // when
    final long lowerBoundSnapshotPosition = snapshotController.recover();

    // then
    assertThat(lowerBoundSnapshotPosition).isEqualTo(-1);
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
    final long lowerBound = snapshotController.recover();
    wrapper.wrap(snapshotController.openDb());

    // then
    assertThat(lowerBound).isEqualTo(-1);
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
    final long lowerBound = snapshotController.recover();
    wrapper.wrap(snapshotController.openDb());

    // then
    assertThat(lowerBound).isEqualTo(3);
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
    final long lowerBound = snapshotController.recover();
    wrapper.wrap(snapshotController.openDb());

    // then
    assertThat(lowerBound).isEqualTo(1);
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

    assertThat(snapshotController.getLastValidSnapshotPosition()).isEqualTo(-1L);

    snapshotController.takeSnapshot(1L);
    snapshotController.takeSnapshot(3L);
    snapshotController.takeSnapshot(5L);
    snapshotController.takeTempSnapshot(6L);

    // when/then
    assertThat(snapshotController.getLastValidSnapshotPosition()).isEqualTo(5L);
  }

  private void corruptLatestSnapshot() throws IOException {
    final var snapshot = storage.getLatestSnapshot();
    assertThat(snapshot).isPresent();

    final var optionalFile =
        Files.list(snapshot.get().getPath())
            .filter(p -> p.toString().endsWith(".sst"))
            .sorted(Comparator.reverseOrder())
            .findFirst();
    assertThat(optionalFile).isPresent();
    Files.write(
        optionalFile.get(), "<--corrupted-->".getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
  }
}
