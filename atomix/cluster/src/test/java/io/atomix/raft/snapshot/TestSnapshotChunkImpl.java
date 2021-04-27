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
package io.atomix.raft.snapshot;

import io.zeebe.snapshots.SnapshotChunk;

class TestSnapshotChunkImpl implements SnapshotChunk {

  final int totalCount;
  final String chunkName;
  private final byte[] content;
  private final String snapshotId;

  TestSnapshotChunkImpl(
      final String snapshotId, final String chunkName, final byte[] content, final int totalCount) {
    this.content = content;
    this.snapshotId = snapshotId;
    this.totalCount = totalCount;
    this.chunkName = chunkName;
  }

  @Override
  public String getSnapshotId() {
    return snapshotId;
  }

  @Override
  public int getTotalCount() {
    return totalCount;
  }

  @Override
  public String getChunkName() {
    return chunkName;
  }

  @Override
  public long getChecksum() {
    return 0;
  }

  @Override
  public byte[] getContent() {
    return content;
  }

  @Override
  public long getSnapshotChecksum() {
    return 0;
  }
}
