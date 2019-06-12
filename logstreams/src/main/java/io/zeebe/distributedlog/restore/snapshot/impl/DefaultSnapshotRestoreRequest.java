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
package io.zeebe.distributedlog.restore.snapshot.impl;

import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreRequest;

public class DefaultSnapshotRestoreRequest implements SnapshotRestoreRequest {

  private long snapshotId;
  private int chunkIdx;

  public DefaultSnapshotRestoreRequest() {}

  public DefaultSnapshotRestoreRequest(long snapshotId, int chunkIdx) {
    this.snapshotId = snapshotId;
    this.chunkIdx = chunkIdx;
  }

  @Override
  public long getSnapshotId() {
    return snapshotId;
  }

  @Override
  public int getChunkIdx() {
    return chunkIdx;
  }

  public void setSnapshotId(long snapshotId) {
    this.snapshotId = snapshotId;
  }

  public void setChunkIdx(int chunkIdx) {
    this.chunkIdx = chunkIdx;
  }
}
