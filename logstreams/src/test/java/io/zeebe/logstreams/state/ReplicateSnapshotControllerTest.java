/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.db.impl.DefaultColumnFamily;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.zeebe.logstreams.util.RocksDBWrapper;
import io.zeebe.logstreams.util.TestSnapshotStorage;
import io.zeebe.test.util.AutoCloseableRule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ReplicateSnapshotControllerTest {

  private static final int VALUE = 0xCAFE;
  private static final String KEY = "test";

  @Rule public final TemporaryFolder tempFolderRule = new TemporaryFolder();
  @Rule public final AutoCloseableRule autoCloseableRule = new AutoCloseableRule();

  private StateSnapshotController replicatorSnapshotController;
  private StateSnapshotController receiverSnapshotController;
  private Replicator replicator;
  private SnapshotStorage receiverStorage;

  @Before
  public void setup() throws IOException {
    final var senderRoot = tempFolderRule.newFolder("sender").toPath();
    final var storage = new TestSnapshotStorage(senderRoot);

    final var receiverRoot = tempFolderRule.newFolder("receiver").toPath();
    receiverStorage = new TestSnapshotStorage(receiverRoot);

    replicator = new Replicator();
    replicatorSnapshotController =
        new StateSnapshotController(
            ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class),
            storage,
            replicator,
            db -> Long.MAX_VALUE);

    receiverSnapshotController =
        new StateSnapshotController(
            ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class),
            receiverStorage,
            replicator,
            db -> Long.MAX_VALUE);

    autoCloseableRule.manage(replicatorSnapshotController);
    autoCloseableRule.manage(receiverSnapshotController);
    autoCloseableRule.manage(storage);
    autoCloseableRule.manage(receiverStorage);

    final RocksDBWrapper wrapper = new RocksDBWrapper();
    wrapper.wrap(replicatorSnapshotController.openDb());
    wrapper.putInt(KEY, VALUE);
  }

  @Test
  public void shouldReplicateSnapshotChunks() {
    // given
    replicatorSnapshotController.takeSnapshot(1);

    // when
    replicatorSnapshotController.replicateLatestSnapshot(Runnable::run);

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
        .flatExtracting(chunk -> List.of(chunk.split("_")))
        .contains("1");
  }

  @Test
  public void shouldContainChecksumPerChunk() {
    // given
    replicatorSnapshotController.takeSnapshot(1);

    // when
    replicatorSnapshotController.replicateLatestSnapshot(Runnable::run);

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
  public void shouldNotReplicateWithoutSnapshot() {
    // given

    // when
    replicatorSnapshotController.replicateLatestSnapshot(Runnable::run);

    // then
    final List<SnapshotChunk> replicatedChunks = replicator.replicatedChunks;
    final int totalCount = replicatedChunks.size();
    assertThat(totalCount).isEqualTo(0);
  }

  @Test
  public void shouldReceiveSnapshotChunks() throws Exception {
    // given
    receiverSnapshotController.consumeReplicatedSnapshots();
    replicatorSnapshotController.takeSnapshot(1);

    // when
    replicatorSnapshotController.replicateLatestSnapshot(Runnable::run);

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
    replicatorSnapshotController.takeSnapshot(1);
    replicatorSnapshotController.replicateLatestSnapshot(Runnable::run);

    // when
    replicatorSnapshotController.replicateLatestSnapshot(Runnable::run);

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
