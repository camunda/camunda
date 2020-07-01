/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.partitions.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.snapshot.PersistedSnapshotStore;
import io.atomix.raft.snapshot.SnapshotChunk;
import io.atomix.raft.snapshot.impl.FileBasedSnapshotStoreFactory;
import io.atomix.raft.zeebe.ZeebeEntry;
import io.atomix.storage.journal.Indexed;
import io.zeebe.broker.system.partitions.SnapshotReplication;
import io.zeebe.db.impl.DefaultColumnFamily;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.zeebe.logstreams.util.RocksDBWrapper;
import io.zeebe.test.util.AutoCloseableRule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ReplicateStateControllerTest {

  private static final int VALUE = 0xCAFE;
  private static final String KEY = "test";

  @Rule public final TemporaryFolder tempFolderRule = new TemporaryFolder();
  @Rule public final AutoCloseableRule autoCloseableRule = new AutoCloseableRule();

  private StateControllerImpl replicatorSnapshotController;
  private StateControllerImpl receiverSnapshotController;
  private Replicator replicator;
  private PersistedSnapshotStore senderStore;
  private PersistedSnapshotStore receiverStore;

  @Before
  public void setup() throws IOException {
    final var senderRoot = tempFolderRule.newFolder("sender").toPath();
    senderStore = new FileBasedSnapshotStoreFactory().createSnapshotStore(senderRoot, "1");

    final var receiverRoot = tempFolderRule.newFolder("receiver").toPath();
    receiverStore = new FileBasedSnapshotStoreFactory().createSnapshotStore(receiverRoot, "1");

    replicator = new Replicator();
    replicatorSnapshotController =
        new StateControllerImpl(
            1,
            ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class),
            senderStore,
            senderRoot.resolve("runtime"),
            replicator,
            l ->
                Optional.ofNullable(
                    new Indexed(l, new ZeebeEntry(1, System.currentTimeMillis(), 1, 10, null), 0)),
            db -> Long.MAX_VALUE);
    senderStore.addSnapshotListener(replicatorSnapshotController);

    receiverSnapshotController =
        new StateControllerImpl(
            1,
            ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class),
            receiverStore,
            receiverRoot.resolve("runtime"),
            replicator,
            l ->
                Optional.ofNullable(
                    new Indexed(l, new ZeebeEntry(1, System.currentTimeMillis(), 1, 10, null), 0)),
            db -> Long.MAX_VALUE);
    receiverStore.addSnapshotListener(receiverSnapshotController);

    autoCloseableRule.manage(replicatorSnapshotController);
    autoCloseableRule.manage(receiverSnapshotController);
    autoCloseableRule.manage(senderStore);
    autoCloseableRule.manage(receiverStore);

    final RocksDBWrapper wrapper = new RocksDBWrapper();
    wrapper.wrap(replicatorSnapshotController.openDb());
    wrapper.putInt(KEY, VALUE);
  }

  @Test
  public void shouldReplicateSnapshotChunks() {
    // given
    final var snapshot = replicatorSnapshotController.takeTransientSnapshot(1).orElseThrow();

    // when
    snapshot.persist();

    // then
    final List<SnapshotChunk> replicatedChunks = replicator.replicatedChunks;
    final int totalCount = replicatedChunks.size();
    assertThat(totalCount).isGreaterThan(0);

    final SnapshotChunk firstChunk = replicatedChunks.get(0);
    final int chunkTotalCount = firstChunk.getTotalCount();
    assertThat(totalCount).isEqualTo(chunkTotalCount);

    assertThat(replicatedChunks).extracting(SnapshotChunk::getTotalCount).containsOnly(totalCount);

    assertThat(replicatedChunks)
        .extracting(SnapshotChunk::getSnapshotId)
        .flatExtracting(chunk -> List.of(chunk.split("-")))
        .contains("1");
  }

  @Test
  public void shouldContainChecksumPerChunk() {
    // given
    final var snapshot = replicatorSnapshotController.takeTransientSnapshot(1).orElseThrow();

    // when
    snapshot.persist();

    // then
    final List<SnapshotChunk> replicatedChunks = replicator.replicatedChunks;
    assertThat(replicatedChunks.size()).isGreaterThan(0);

    replicatedChunks.forEach(
        chunk -> {
          final CRC32 crc32 = new CRC32();
          crc32.update(chunk.getContent());
          assertThat(chunk.getChecksum()).isEqualTo(crc32.getValue());
        });
  }

  @Test
  public void shouldReceiveSnapshotChunks() throws Exception {
    // given
    receiverSnapshotController.consumeReplicatedSnapshots();
    final var snapshot = replicatorSnapshotController.takeTransientSnapshot(1).orElseThrow();

    // when
    snapshot.persist();

    // then
    final RocksDBWrapper wrapper = new RocksDBWrapper();
    receiverSnapshotController.recover();
    assertThat(receiverSnapshotController.isDbOpened()).isTrue();

    wrapper.wrap(receiverSnapshotController.openDb());
    final int valueFromSnapshot = wrapper.getInt(KEY);
    assertThat(valueFromSnapshot).isEqualTo(VALUE);
  }

  @Test
  public void shouldNotFailOnReplicatingAndReceivingTwice() throws Exception {
    // given
    receiverSnapshotController.consumeReplicatedSnapshots();
    final var transientSnapshot =
        replicatorSnapshotController.takeTransientSnapshot(1).orElseThrow();

    // when
    final var snapshot = transientSnapshot.persist();
    receiverSnapshotController.onNewSnapshot(snapshot);

    // then
    final RocksDBWrapper wrapper = new RocksDBWrapper();
    receiverSnapshotController.recover();
    assertThat(receiverSnapshotController.isDbOpened()).isTrue();

    wrapper.wrap(receiverSnapshotController.openDb());
    final int valueFromSnapshot = wrapper.getInt(KEY);
    assertThat(valueFromSnapshot).isEqualTo(VALUE);
  }

  protected static final class Replicator implements SnapshotReplication {

    final List<SnapshotChunk> replicatedChunks = new ArrayList<>();
    private Consumer<SnapshotChunk> chunkConsumer;

    @Override
    public void replicate(final SnapshotChunk snapshot) {
      replicatedChunks.add(snapshot);
      if (chunkConsumer != null) {
        chunkConsumer.accept(snapshot);
      }
    }

    @Override
    public void consume(final Consumer<SnapshotChunk> consumer) {
      chunkConsumer = consumer;
    }

    @Override
    public void close() {}
  }
}
