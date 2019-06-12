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

import io.zeebe.logstreams.state.SnapshotChunk;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecordingSnapshotConsumer implements SnapshotConsumer {

  List<SnapshotChunk> consumedChunks = new ArrayList<>();
  Map<Long, Boolean> snapshots = new HashMap<>();

  @Override
  public boolean consumeSnapshotChunk(SnapshotChunk chunk) {
    snapshots.put(chunk.getSnapshotPosition(), false);
    consumedChunks.add(chunk);
    return true;
  }

  @Override
  public boolean moveValidSnapshot(long snapshotPosition) {
    snapshots.put(snapshotPosition, true);
    return true;
  }

  @Override
  public void clearTmpSnapshot(long snapshotPosition) {
    consumedChunks.clear();
  }

  public List<SnapshotChunk> getConsumedChunks() {
    return consumedChunks;
  }

  public void reset() {
    consumedChunks = new ArrayList<>();
    snapshots = new HashMap<>();
  }

  public boolean isSnapshotValid(long snapshotId) {
    return snapshots.get(snapshotId);
  }
}
