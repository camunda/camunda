/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.snapshot;

import io.zeebe.logstreams.state.SnapshotChunk;
import io.zeebe.logstreams.state.SnapshotConsumer;
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
  public boolean completeSnapshot(long snapshotPosition) {
    snapshots.put(snapshotPosition, true);
    return true;
  }

  @Override
  public void invalidateSnapshot(long snapshotPosition) {
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
