/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.snapshot.impl;

import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreInfo;

public class DefaultSnapshotRestoreInfo implements SnapshotRestoreInfo {
  private long snapshotId;
  private int numChunks;

  public DefaultSnapshotRestoreInfo(long snapshotId, int numChunks) {
    this.snapshotId = snapshotId;
    this.numChunks = numChunks;
  }

  @Override
  public long getSnapshotId() {
    return snapshotId;
  }

  @Override
  public int getNumChunks() {
    return numChunks;
  }

  @Override
  public String toString() {
    return "DefaultSnapshotRestoreInfo{"
        + "snapshotId="
        + snapshotId
        + ", numChunks="
        + numChunks
        + '}';
  }
}
