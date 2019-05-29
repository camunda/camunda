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
package io.zeebe.distributedlog.restore.snapshot;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.zeebe.distributedlog.restore.impl.ControllableRestoreClient;
import io.zeebe.distributedlog.restore.impl.ControllableSnapshotRestoreContext;
import io.zeebe.distributedlog.restore.snapshot.impl.InvalidSnapshotRestoreResponse;
import io.zeebe.distributedlog.restore.snapshot.impl.SuccessSnapshotRestoreResponse;
import io.zeebe.logstreams.state.SnapshotChunk;
import io.zeebe.util.collection.Tuple;
import java.util.concurrent.CompletableFuture;
import org.junit.Before;
import org.junit.Test;

public class RestoreSnapshotReplicatorTest {
  private ControllableRestoreClient client = new ControllableRestoreClient();
  private ControllableSnapshotRestoreContext restoreContext =
      new ControllableSnapshotRestoreContext();
  private RecordingSnapshotConsumer snapshotConsumer = new RecordingSnapshotConsumer();
  private RestoreSnapshotReplicator snapshotReplicator =
      new RestoreSnapshotReplicator(client, restoreContext, snapshotConsumer, 1, Runnable::run);
  private MemberId server = MemberId.anonymous();
  private final ControllableSnapshotChunk responseChunk = new ControllableSnapshotChunk();

  @Before
  public void setup() {
    client.reset();
    restoreContext.reset();
    snapshotConsumer.reset();
  }

  @Test
  public void shouldReplicateSnapshot() {

    // given
    final long snapshotId = 10;
    final int numChunks = 5;

    restoreContext.setExporterPositionSupplier(() -> 5L);
    restoreContext.setProcessorPositionSupplier(() -> 10L);

    for (int i = 0; i < numChunks; i++) {
      client.completeRequestSnapshotChunk(
          i, new SuccessSnapshotRestoreResponse(responseChunk.withChunk(snapshotId, i)));
    }

    // when
    final CompletableFuture<Tuple<Long, Long>> replicate =
        snapshotReplicator.restore(server, snapshotId, numChunks);

    final Tuple<Long, Long> longLongTuple = replicate.join();

    // then
    assertThat(snapshotConsumer.getConsumedChunks().size()).isEqualTo(numChunks);
    assertThat(snapshotConsumer.isSnapshotValid(snapshotId)).isTrue();
    assertThat(longLongTuple.getLeft()).isEqualTo(5L);
    assertThat(longLongTuple.getRight()).isEqualTo(10L);
  }

  @Test
  public void shouldCompleteExceptionallyWhenInvalidResponse() {
    // given
    final long snapshotId = 10;
    final int numChunks = 5;

    restoreContext.setExporterPositionSupplier(() -> 5L);
    restoreContext.setProcessorPositionSupplier(() -> 10L);

    client.completeRequestSnapshotChunk(0, new InvalidSnapshotRestoreResponse());

    // when
    final CompletableFuture<Tuple<Long, Long>> replicate =
        snapshotReplicator.restore(server, snapshotId, numChunks);

    // then
    assertThat(replicate).isCompletedExceptionally();
  }

  @Test
  public void shouldClearTmpSnapshotsIfReplicationFails() {
    // given
    final long snapshotId = 10;
    final int numChunks = 5;

    restoreContext.setExporterPositionSupplier(() -> 5L);
    restoreContext.setProcessorPositionSupplier(() -> 10L);

    final CompletableFuture<Tuple<Long, Long>> replicate =
        snapshotReplicator.restore(server, snapshotId, numChunks);

    client.completeRequestSnapshotChunk(
        0, new SuccessSnapshotRestoreResponse(responseChunk.withChunk(snapshotId, 0)));
    client.completeRequestSnapshotChunk(
        1, new SuccessSnapshotRestoreResponse(responseChunk.withChunk(snapshotId, 1)));

    assertThat(snapshotConsumer.getConsumedChunks().size()).isEqualTo(2);

    // when
    client.completeRequestSnapshotChunk(2, new RuntimeException());

    // then
    waitUntil(() -> replicate.isDone());
    assertThat(replicate).isCompletedExceptionally();
    assertThat(snapshotConsumer.getConsumedChunks().size()).isEqualTo(0);
  }

  private static final class ControllableSnapshotChunk implements SnapshotChunk {

    private long snapshotId;
    private final int totalCount = 1;
    private String name;
    private final long checksum = 1;
    private final byte[] content = new byte[0];

    public ControllableSnapshotChunk withChunk(long snapshotId, int chunkIdx) {
      this.snapshotId = snapshotId;
      this.name = String.valueOf(chunkIdx);
      return this;
    }

    @Override
    public long getSnapshotPosition() {
      return snapshotId;
    }

    @Override
    public int getTotalCount() {
      return totalCount;
    }

    @Override
    public String getChunkName() {
      return name;
    }

    @Override
    public long getChecksum() {
      return checksum;
    }

    @Override
    public byte[] getContent() {
      return content;
    }
  }
}
