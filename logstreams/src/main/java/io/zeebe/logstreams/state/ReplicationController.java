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

import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.spi.ValidSnapshotListener;
import java.io.File;
import java.io.IOException;
import org.agrona.collections.Long2LongHashMap;
import org.slf4j.Logger;

public final class ReplicationController {

  private static final Logger LOG = Loggers.SNAPSHOT_LOGGER;

  private static final long START_VALUE = 0L;
  private static final long INVALID_SNAPSHOT = -1;
  private static final long MISSING_SNAPSHOT = Long.MIN_VALUE;
  private static final ValidSnapshotListener NOOP_VALID_SNAPSHOT_LISTENER = () -> {};

  private final SnapshotReplication replication;
  private final Long2LongHashMap receivedSnapshots = new Long2LongHashMap(MISSING_SNAPSHOT);
  private final StateStorage storage;

  private final SnapshotConsumer snapshotConsumer;

  private final ValidSnapshotListener validSnapshotListener;

  public ReplicationController(SnapshotReplication replication, StateStorage storage) {
    this(replication, storage, NOOP_VALID_SNAPSHOT_LISTENER);
  }

  public ReplicationController(
      SnapshotReplication replication,
      StateStorage storage,
      ValidSnapshotListener validSnapshotListener) {
    this.replication = replication;
    this.storage = storage;
    this.validSnapshotListener = validSnapshotListener;
    this.snapshotConsumer = new FileSnapshotConsumer(storage, LOG);
  }

  public void replicate(long snapshotPosition, int totalCount, File snapshotChunkFile) {
    try {
      final SnapshotChunk chunkToReplicate =
          SnapshotChunkUtil.createSnapshotChunkFromFile(
              snapshotChunkFile, snapshotPosition, totalCount);
      replication.replicate(chunkToReplicate);
    } catch (IOException ioe) {
      LOG.error(
          "Unexpected error on reading snapshot chunk from file '{}'.", snapshotChunkFile, ioe);
    }
  }

  /** Registering for consuming snapshot chunks. */
  public void consumeReplicatedSnapshots() {
    replication.consume(this::consumeSnapshotChunk);
  }

  /**
   * This is called by the snapshot replication implementation on each snapshot chunk
   *
   * @param snapshotChunk the chunk to consume
   */
  private void consumeSnapshotChunk(SnapshotChunk snapshotChunk) {
    final long snapshotPosition = snapshotChunk.getSnapshotPosition();
    final String snapshotName = Long.toString(snapshotPosition);
    final String chunkName = snapshotChunk.getChunkName();

    final long snapshotCounter =
        receivedSnapshots.computeIfAbsent(snapshotPosition, k -> START_VALUE);
    if (snapshotCounter == INVALID_SNAPSHOT) {
      LOG.debug(
          "Ignore snapshot chunk {}, because snapshot {} is marked as invalid.",
          chunkName,
          snapshotName);
      return;
    }

    if (snapshotConsumer.consumeSnapshotChunk(snapshotChunk)) {
      validateWhenReceivedAllChunks(snapshotChunk);
    } else {
      markSnapshotAsInvalid(snapshotChunk);
    }
  }

  private void markSnapshotAsInvalid(SnapshotChunk chunk) {
    final long snapshotPosition = chunk.getSnapshotPosition();
    receivedSnapshots.put(snapshotPosition, INVALID_SNAPSHOT);
  }

  private void validateWhenReceivedAllChunks(SnapshotChunk snapshotChunk) {
    final int totalChunkCount = snapshotChunk.getTotalCount();
    final long currentChunks = incrementAndGetChunkCount(snapshotChunk);

    if (currentChunks == totalChunkCount) {
      final File validSnapshotDirectory =
          storage.getSnapshotDirectoryFor(snapshotChunk.getSnapshotPosition());
      LOG.debug(
          "Received all snapshot chunks ({}/{}), snapshot is valid. Move to {}",
          currentChunks,
          totalChunkCount,
          validSnapshotDirectory.toPath());

      final boolean valid = tryToMarkSnapshotAsValid(snapshotChunk);

      if (valid) {
        validSnapshotListener.onNewValidSnapshot();
      }
    } else {
      LOG.debug(
          "Waiting for more snapshot chunks, currently have {}/{}.",
          currentChunks,
          totalChunkCount);
    }
  }

  private long incrementAndGetChunkCount(SnapshotChunk snapshotChunk) {
    final long snapshotPosition = snapshotChunk.getSnapshotPosition();
    final long oldCount = receivedSnapshots.get(snapshotPosition);
    final long newCount = oldCount + 1;
    receivedSnapshots.put(snapshotPosition, newCount);
    return newCount;
  }

  private boolean tryToMarkSnapshotAsValid(SnapshotChunk snapshotChunk) {
    if (snapshotConsumer.completeSnapshot(snapshotChunk.getSnapshotPosition())) {
      receivedSnapshots.remove(snapshotChunk.getSnapshotPosition());
      return true;

    } else {
      markSnapshotAsInvalid(snapshotChunk);
      return false;
    }
  }
}
