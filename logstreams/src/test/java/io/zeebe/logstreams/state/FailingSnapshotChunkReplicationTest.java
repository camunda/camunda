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

import io.zeebe.db.impl.DefaultColumnFamily;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.zeebe.logstreams.state.ReplicateSnapshotControllerTest.Replicator;
import io.zeebe.test.util.AutoCloseableRule;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FailingSnapshotChunkReplicationTest {

  @Rule public TemporaryFolder tempFolderRule = new TemporaryFolder();
  @Rule public AutoCloseableRule autoCloseableRule = new AutoCloseableRule();

  private StateSnapshotController replicatorSnapshotController;
  private StateSnapshotController receiverSnapshotController;
  private StateStorage receiverStorage;
  private StateStorage replicatorStorage;

  public void setup(SnapshotReplication replicator) throws IOException {
    final File runtimeDirectory = tempFolderRule.newFolder("runtime");
    final File snapshotsDirectory = tempFolderRule.newFolder("snapshots");
    replicatorStorage = new StateStorage(runtimeDirectory, snapshotsDirectory);

    final File receiverRuntimeDirectory = tempFolderRule.newFolder("runtime-receiver");
    final File receiverSnapshotsDirectory = tempFolderRule.newFolder("snapshots-receiver");
    receiverStorage = new StateStorage(receiverRuntimeDirectory, receiverSnapshotsDirectory);

    setupReplication(replicator);
  }

  private void setupReplication(SnapshotReplication replicator) {
    replicatorSnapshotController =
        new StateSnapshotController(
            ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class),
            replicatorStorage,
            replicator,
            1);
    receiverSnapshotController =
        new StateSnapshotController(
            ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class),
            receiverStorage,
            replicator,
            1);

    autoCloseableRule.manage(replicatorSnapshotController);
    autoCloseableRule.manage(receiverSnapshotController);
    replicatorSnapshotController.openDb();
  }

  @Test
  public void shouldNotWriteChunksAfterReceivingInvalidChunk() throws Exception {
    // given
    final EvilReplicator replicator = new EvilReplicator();
    setup(replicator);

    receiverSnapshotController.consumeReplicatedSnapshots(pos -> {});
    replicatorSnapshotController.takeSnapshot(1);

    // when
    replicatorSnapshotController.replicateLatestSnapshot(Runnable::run);

    // then
    final List<SnapshotChunk> replicatedChunks = replicator.replicatedChunks;
    assertThat(replicatedChunks.size()).isGreaterThan(0);

    final File snapshotDirectory = receiverStorage.getTmpSnapshotDirectoryFor("1");
    assertThat(snapshotDirectory).exists();

    final File[] files = snapshotDirectory.listFiles();
    assertThat(files).hasSize(1);
    assertThat(files[0].getName()).isEqualTo(replicatedChunks.get(0).getChunkName());

    assertThat(receiverStorage.existSnapshot(1)).isFalse();
  }

  @Test
  public void shouldNotMarkSnapshotAsValidIfNotReceivedAllChunks() throws Exception {
    // given
    final FlakyReplicator replicator = new FlakyReplicator();
    setup(replicator);

    receiverSnapshotController.consumeReplicatedSnapshots(pos -> {});
    replicatorSnapshotController.takeSnapshot(1);

    // when
    replicatorSnapshotController.replicateLatestSnapshot(Runnable::run);

    // then
    final List<SnapshotChunk> replicatedChunks = replicator.replicatedChunks;
    assertThat(replicatedChunks.size()).isGreaterThan(0);

    final File snapshotDirectory = receiverStorage.getTmpSnapshotDirectoryFor("1");
    assertThat(snapshotDirectory).exists();
    final File[] files = snapshotDirectory.listFiles();
    assertThat(files)
        .extracting(File::getName)
        .containsExactlyInAnyOrder(
            replicatedChunks.subList(0, 2).stream()
                .map(SnapshotChunk::getChunkName)
                .toArray(String[]::new));
    assertThat(receiverStorage.existSnapshot(1)).isFalse();
  }

  @Test
  public void shouldDeleteOrphanedSnapshots() throws Exception {
    // given
    final FlakyReplicator flakyReplicator = new FlakyReplicator();
    setup(flakyReplicator);
    receiverSnapshotController.consumeReplicatedSnapshots(pos -> {});
    replicatorSnapshotController.takeSnapshot(1);
    replicatorSnapshotController.replicateLatestSnapshot(Runnable::run);
    replicatorSnapshotController.close();

    final Replicator workingReplicator = new Replicator();
    setupReplication(workingReplicator);
    receiverSnapshotController.consumeReplicatedSnapshots(pos -> {});
    replicatorSnapshotController.takeSnapshot(2);
    replicatorSnapshotController.replicateLatestSnapshot(Runnable::run);

    // when
    replicatorSnapshotController.takeSnapshot(3);
    replicatorSnapshotController.replicateLatestSnapshot(Runnable::run);

    // then
    final List<SnapshotChunk> replicatedChunks = workingReplicator.replicatedChunks;
    assertThat(replicatedChunks.size()).isGreaterThan(0);

    final File snapshotDirectory = receiverStorage.getTmpSnapshotDirectoryFor("1");
    assertThat(snapshotDirectory).doesNotExist();
    assertThat(receiverStorage.existSnapshot(1)).isFalse();
    assertThat(receiverStorage.existSnapshot(2)).isFalse();
    assertThat(receiverStorage.existSnapshot(3)).isTrue();
  }

  private final class FlakyReplicator implements SnapshotReplication {

    final List<SnapshotChunk> replicatedChunks = new ArrayList<>();
    private Consumer<SnapshotChunk> chunkConsumer;

    @Override
    public void replicate(SnapshotChunk snapshot) {
      replicatedChunks.add(snapshot);
      if (chunkConsumer != null) {
        if (replicatedChunks.size() < 3) {
          chunkConsumer.accept(snapshot);
        }
      }
    }

    @Override
    public void consume(Consumer<SnapshotChunk> consumer) {
      chunkConsumer = consumer;
    }

    @Override
    public void close() {}
  }

  private final class EvilReplicator implements SnapshotReplication {

    final List<SnapshotChunk> replicatedChunks = new ArrayList<>();
    private Consumer<SnapshotChunk> chunkConsumer;

    @Override
    public void replicate(SnapshotChunk snapshot) {
      replicatedChunks.add(snapshot);
      if (chunkConsumer != null) {
        chunkConsumer.accept(
            replicatedChunks.size() > 1 ? new DisruptedSnapshotChunk(snapshot) : snapshot);
      }
    }

    @Override
    public void consume(Consumer<SnapshotChunk> consumer) {
      chunkConsumer = consumer;
    }

    @Override
    public void close() {}
  }

  private final class DisruptedSnapshotChunk implements SnapshotChunk {
    private final SnapshotChunk snapshotChunk;

    DisruptedSnapshotChunk(SnapshotChunk snapshotChunk) {
      this.snapshotChunk = snapshotChunk;
    }

    @Override
    public long getSnapshotPosition() {
      return snapshotChunk.getSnapshotPosition();
    }

    @Override
    public int getTotalCount() {
      return snapshotChunk.getTotalCount();
    }

    @Override
    public String getChunkName() {
      return snapshotChunk.getChunkName();
    }

    @Override
    public long getChecksum() {
      return 0;
    }

    @Override
    public byte[] getContent() {
      return snapshotChunk.getContent();
    }
  }
}
