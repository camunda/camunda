/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft.snapshot.impl;

import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.snapshot.SnapshotChunk;
import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Arrays;
import java.util.Objects;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * An implementation of {@link SnapshotChunk} which is compatible with the snapshot replication
 * protocol of brokers pre 0.24.x. Should be removed once no versions below 0.24.x are supported.
 */
public final class LegacySnapshotChunk implements SnapshotChunk {
  private final FileBasedSnapshotMetadata metadata;
  private final String chunkId;
  private final byte[] content;
  private final long checksum;

  private LegacySnapshotChunk(
      final FileBasedSnapshotMetadata metadata,
      final String chunkId,
      final byte[] content,
      final long checksum) {
    this.metadata = metadata;
    this.chunkId = chunkId;
    this.content = content;
    this.checksum = checksum;
  }

  public static LegacySnapshotChunk ofInstallRequest(final InstallRequest request) {
    final var metadata =
        new FileBasedSnapshotMetadata(
            request.index(), request.term(), WallClockTimestamp.from(request.timestamp()));
    final var chunkId = BufferUtil.bufferAsString(new UnsafeBuffer(request.chunkId()));
    final var content = BufferUtil.bufferAsArray(new UnsafeBuffer(request.data()));
    final var checksum = SnapshotChunkUtil.createChecksum(content);

    return new LegacySnapshotChunk(metadata, chunkId, content, checksum);
  }

  @Override
  public String getSnapshotId() {
    return metadata.getSnapshotIdAsString();
  }

  @Override
  public int getTotalCount() {
    return FileBasedReceivedSnapshot.TOTAL_COUNT_NULL_VALUE;
  }

  @Override
  public String getChunkName() {
    return chunkId;
  }

  @Override
  public long getChecksum() {
    return checksum;
  }

  @Override
  public byte[] getContent() {
    return content;
  }

  @Override
  public long getSnapshotChecksum() {
    return FileBasedReceivedSnapshot.SNAPSHOT_CHECKSUM_NULL_VALUE;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final LegacySnapshotChunk that = (LegacySnapshotChunk) o;
    return getChecksum() == that.getChecksum()
        && Objects.equals(metadata, that.metadata)
        && Objects.equals(chunkId, that.chunkId)
        && Arrays.equals(getContent(), that.getContent());
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(metadata, chunkId, getChecksum());
    result = 31 * result + Arrays.hashCode(getContent());
    return result;
  }

  @Override
  public String toString() {
    return "LegacySnapshotChunk{"
        + "metadata="
        + metadata
        + ", chunkId='"
        + chunkId
        + '\''
        + ", checksum="
        + checksum
        + '}';
  }
}
