/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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

  public void setChunkIdx(int chunkIdx) {
    this.chunkIdx = chunkIdx;
  }

  public void setSnapshotId(long snapshotId) {
    this.snapshotId = snapshotId;
  }

  @Override
  public String toString() {
    return "DefaultSnapshotRestoreRequest{"
        + "snapshotId="
        + snapshotId
        + ", chunkIdx="
        + chunkIdx
        + '}';
  }
}
