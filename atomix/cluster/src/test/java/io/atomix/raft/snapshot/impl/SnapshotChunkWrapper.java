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

import io.atomix.raft.snapshot.SnapshotChunk;

public final class SnapshotChunkWrapper implements SnapshotChunk {

  private final SnapshotChunk wrappedChunk;
  private final String snapshotId;
  private final Integer totalCount;
  private final Long checksum;
  private final Long snapshotChecksum;

  private SnapshotChunkWrapper(
      final SnapshotChunk wrappedChunk,
      final String snapshotId,
      final Integer totalCount,
      final Long checksum,
      final Long snapshotChecksum) {
    this.wrappedChunk = wrappedChunk;
    this.snapshotId = snapshotId;
    this.totalCount = totalCount;
    this.checksum = checksum;
    this.snapshotChecksum = snapshotChecksum;
  }

  public static SnapshotChunk withDifferentSnapshotId(
      final SnapshotChunk wrappedChunk, final String snapshotId) {
    return new SnapshotChunkWrapper(wrappedChunk, snapshotId, null, null, null);
  }

  public static SnapshotChunk withDifferentTotalCount(
      final SnapshotChunk wrappedChunk, final Integer totalCount) {
    return new SnapshotChunkWrapper(wrappedChunk, null, totalCount, null, null);
  }

  public static SnapshotChunk withDifferentChecksum(
      final SnapshotChunk wrappedChunk, final Long checksum) {
    return new SnapshotChunkWrapper(wrappedChunk, null, null, checksum, null);
  }

  public static SnapshotChunk withDifferentSnapshotChecksum(
      final SnapshotChunk wrappedChunk, final Long snapshotChecksum) {
    return new SnapshotChunkWrapper(wrappedChunk, null, null, null, snapshotChecksum);
  }

  @Override
  public String getSnapshotId() {
    if (snapshotId == null) {
      return wrappedChunk.getSnapshotId();
    }
    return snapshotId;
  }

  @Override
  public int getTotalCount() {
    if (totalCount == null) {
      return wrappedChunk.getTotalCount();
    }
    return totalCount;
  }

  @Override
  public String getChunkName() {
    return wrappedChunk.getChunkName();
  }

  @Override
  public long getChecksum() {
    if (checksum == null) {
      return wrappedChunk.getChecksum();
    }
    return checksum;
  }

  @Override
  public byte[] getContent() {
    return wrappedChunk.getContent();
  }

  @Override
  public long getSnapshotChecksum() {
    if (snapshotChecksum == null) {
      return wrappedChunk.getSnapshotChecksum();
    }
    return snapshotChecksum;
  }
}
