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

import static java.nio.file.StandardOpenOption.CREATE_NEW;

import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.spi.ValidSnapshotListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.zip.CRC32;
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
  }

  private static long createChecksum(byte[] content) {
    final CRC32 crc32 = new CRC32();
    crc32.update(content);
    return crc32.getValue();
  }

  public void replicate(long snapshotPosition, int totalCount, File snapshotChunkFile) {
    try {
      final byte[] content = Files.readAllBytes(snapshotChunkFile.toPath());
      final long checksum = createChecksum(content);

      replication.replicate(
          new SnapshotChunkImpl(
              snapshotPosition, totalCount, snapshotChunkFile.getName(), checksum, content));
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

    if (storage.existSnapshot(snapshotPosition)) {
      LOG.debug("Ignore snapshot chunk {}, snapshot {} already exist.", chunkName, snapshotName);
      return;
    }

    final long snapshotCounter =
        receivedSnapshots.computeIfAbsent(snapshotPosition, k -> START_VALUE);
    if (snapshotCounter == INVALID_SNAPSHOT) {
      LOG.debug(
          "Ignore snapshot chunk {}, because snapshot {} is marked as invalid.",
          chunkName,
          snapshotName);
      return;
    }

    final long expectedChecksum = snapshotChunk.getChecksum();
    final long actualChecksum = createChecksum(snapshotChunk.getContent());

    if (expectedChecksum != actualChecksum) {
      markSnapshotAsInvalid(snapshotChunk);
      LOG.warn(
          "Expected to have checksum {} for snapshot chunk file {} ({}), but calculated {}",
          expectedChecksum,
          chunkName,
          snapshotName,
          actualChecksum);
      return;
    }

    final File tmpSnapshotDirectory = storage.getTmpSnapshotDirectoryFor(snapshotName);
    if (!tmpSnapshotDirectory.exists()) {
      tmpSnapshotDirectory.mkdirs();
    }

    final File snapshotFile = new File(tmpSnapshotDirectory, chunkName);
    if (snapshotFile.exists()) {
      LOG.debug("Received a snapshot file which already exist '{}'.", snapshotFile);
      return;
    }

    LOG.debug("Consume snapshot chunk {}", chunkName);
    writeReceivedSnapshotChunk(snapshotChunk, tmpSnapshotDirectory, snapshotFile);
  }

  private void writeReceivedSnapshotChunk(
      SnapshotChunk snapshotChunk, File tmpSnapshotDirectory, File snapshotFile) {
    try {
      Files.write(
          snapshotFile.toPath(), snapshotChunk.getContent(), CREATE_NEW, StandardOpenOption.WRITE);
      LOG.debug("Wrote replicated snapshot chunk to file {}", snapshotFile.toPath());

      validateWhenReceivedAllChunks(snapshotChunk, tmpSnapshotDirectory);
    } catch (IOException ioe) {
      markSnapshotAsInvalid(snapshotChunk);
      LOG.error(
          "Unexpected error occurred on writing an snapshot chunk to '{}'.", snapshotFile, ioe);
    }
  }

  private void markSnapshotAsInvalid(SnapshotChunk chunk) {
    final long snapshotPosition = chunk.getSnapshotPosition();
    receivedSnapshots.put(snapshotPosition, INVALID_SNAPSHOT);
  }

  private void validateWhenReceivedAllChunks(
      SnapshotChunk snapshotChunk, File tmpSnapshotDirectory) {
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

      final boolean valid =
          tryToMarkSnapshotAsValid(snapshotChunk, tmpSnapshotDirectory, validSnapshotDirectory);

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

  private boolean tryToMarkSnapshotAsValid(
      SnapshotChunk snapshotChunk, File tmpSnapshotDirectory, File validSnapshotDirectory) {
    try {
      Files.move(tmpSnapshotDirectory.toPath(), validSnapshotDirectory.toPath());
      receivedSnapshots.remove(snapshotChunk.getSnapshotPosition());

      return true;
    } catch (Exception e) {
      markSnapshotAsInvalid(snapshotChunk);
      LOG.error(
          "Unexpected error occurred on moving replicated snapshot from '{}'.",
          tmpSnapshotDirectory.toPath(),
          e);
      return false;
    }
  }

  private final class SnapshotChunkImpl implements SnapshotChunk {
    private final long snapshotPosition;
    private final int totalCount;
    private final String chunkName;
    private final byte[] content;
    private final long checksum;

    SnapshotChunkImpl(
        long snapshotPosition, int totalCount, String chunkName, long checksum, byte[] content) {
      this.snapshotPosition = snapshotPosition;
      this.totalCount = totalCount;
      this.chunkName = chunkName;
      this.checksum = checksum;
      this.content = content;
    }

    public long getSnapshotPosition() {
      return snapshotPosition;
    }

    @Override
    public String getChunkName() {
      return chunkName;
    }

    @Override
    public int getTotalCount() {
      return totalCount;
    }

    public long getChecksum() {
      return checksum;
    }

    public byte[] getContent() {
      return content;
    }
  }
}
