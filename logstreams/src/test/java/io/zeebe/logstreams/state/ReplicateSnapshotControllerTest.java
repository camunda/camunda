/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.zeebe.db.impl.DefaultColumnFamily;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.zeebe.logstreams.impl.delete.NoopDeletionService;
import io.zeebe.logstreams.util.RocksDBWrapper;
import io.zeebe.test.util.AutoCloseableRule;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReplicateSnapshotControllerTest {

  private static final int VALUE = 0xCAFE;
  private static final String KEY = "test";

  @Rule public TemporaryFolder tempFolderRule = new TemporaryFolder();
  @Rule public AutoCloseableRule autoCloseableRule = new AutoCloseableRule();

  private StateSnapshotController replicatorSnapshotController;
  private StateSnapshotController receiverSnapshotController;
  private Replicator replicator;
  private StateStorage receiverStorage;

  @Before
  public void setup() throws IOException {
    final File runtimeDirectory = tempFolderRule.newFolder("runtime");
    final File snapshotsDirectory = tempFolderRule.newFolder("snapshots");
    final StateStorage storage = new StateStorage(runtimeDirectory, snapshotsDirectory);

    final File receiverRuntimeDirectory = tempFolderRule.newFolder("runtime-receiver");
    final File receiverSnapshotsDirectory = tempFolderRule.newFolder("snapshots-receiver");
    receiverStorage = new StateStorage(receiverRuntimeDirectory, receiverSnapshotsDirectory);

    replicator = new Replicator();
    replicatorSnapshotController =
        new StateSnapshotController(
            ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class), storage, replicator, 2);

    receiverSnapshotController =
        new StateSnapshotController(
            ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class),
            receiverStorage,
            replicator,
            2);

    autoCloseableRule.manage(replicatorSnapshotController);
    autoCloseableRule.manage(receiverSnapshotController);

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

    assertThat(replicatedChunks)
        .extracting(SnapshotChunk::getSnapshotPosition, SnapshotChunk::getTotalCount)
        .containsOnly(tuple(1L, totalCount));
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
    final long recoveredSnapshot = receiverSnapshotController.recover();
    assertThat(recoveredSnapshot).isEqualTo(1);

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
    final long recoveredSnapshot = receiverSnapshotController.recover();
    assertThat(recoveredSnapshot).isEqualTo(1);

    wrapper.wrap(receiverSnapshotController.openDb());
    final int valueFromSnapshot = wrapper.getInt(KEY);
    assertThat(valueFromSnapshot).isEqualTo(VALUE);
  }

  @Test
  public void shouldEnsureMaxSnapshotCount() throws Exception {
    // given
    receiverSnapshotController.consumeReplicatedSnapshots();
    replicateXSnapshots(3);

    // when
    replicatorSnapshotController.replicateLatestSnapshot(Runnable::run);

    // then
    final RocksDBWrapper wrapper = new RocksDBWrapper();
    final long recoveredSnapshot = receiverSnapshotController.recover();
    assertThat(recoveredSnapshot).isEqualTo(3);

    wrapper.wrap(receiverSnapshotController.openDb());
    final int valueFromSnapshot = wrapper.getInt(KEY);
    assertThat(valueFromSnapshot).isEqualTo(VALUE);

    assertThat(receiverStorage.existSnapshot(1)).isFalse();
    assertThat(receiverStorage.existSnapshot(2)).isTrue();
    assertThat(receiverStorage.existSnapshot(3)).isTrue();
  }

  @Test
  public void shouldInvokeCallbackOnReplicatedSnapshot() {
    // given
    receiverSnapshotController.consumeReplicatedSnapshots();
    final NoopDeletionService mockDeletionService = spy(new NoopDeletionService());
    receiverSnapshotController.setDeletionService(mockDeletionService);
    replicateXSnapshots(3);

    // then
    verify(mockDeletionService, never()).delete(anyInt());

    // when
    replicatorSnapshotController.replicateLatestSnapshot(Runnable::run);

    // then
    verify(mockDeletionService).delete(2);
  }

  private void replicateXSnapshots(final int snapshotAmount) {
    for (int i = 1; i <= snapshotAmount; ++i) {
      replicatorSnapshotController.takeSnapshot(i);
      replicatorSnapshotController.replicateLatestSnapshot(Runnable::run);
    }
  }

  protected static final class Replicator implements SnapshotReplication {

    final List<SnapshotChunk> replicatedChunks = new ArrayList<>();
    private Consumer<SnapshotChunk> chunkConsumer;

    @Override
    public void replicate(SnapshotChunk snapshot) {
      replicatedChunks.add(snapshot);
      if (chunkConsumer != null) {
        chunkConsumer.accept(snapshot);
      }
    }

    @Override
    public void consume(Consumer<SnapshotChunk> consumer) {
      chunkConsumer = consumer;
    }

    @Override
    public void close() {}
  }
}
