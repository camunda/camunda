/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.impl;

import io.zeebe.distributedlog.restore.RestoreInfoResponse;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreInfo;
import io.zeebe.distributedlog.restore.snapshot.impl.DefaultSnapshotRestoreInfo;
import io.zeebe.distributedlog.restore.snapshot.impl.NullSnapshotRestoreInfo;

public class DefaultRestoreInfoResponse implements RestoreInfoResponse {
  static final DefaultRestoreInfoResponse NONE =
      new DefaultRestoreInfoResponse(ReplicationTarget.NONE);
  private static final NullSnapshotRestoreInfo NULL_SNAPSHOT_RESTORE_INFO =
      new NullSnapshotRestoreInfo();
  private ReplicationTarget replicationTarget;
  private SnapshotRestoreInfo snapshotRestoreInfo;

  public DefaultRestoreInfoResponse() {
    snapshotRestoreInfo = NULL_SNAPSHOT_RESTORE_INFO;
  }

  public DefaultRestoreInfoResponse(ReplicationTarget replicationTarget) {
    this();
    this.replicationTarget = replicationTarget;
  }

  public DefaultRestoreInfoResponse(
      ReplicationTarget replicationTarget, SnapshotRestoreInfo snapshotRestoreInfo) {
    this.replicationTarget = replicationTarget;
    this.snapshotRestoreInfo = snapshotRestoreInfo;
  }

  @Override
  public ReplicationTarget getReplicationTarget() {
    return replicationTarget;
  }

  @Override
  public SnapshotRestoreInfo getSnapshotRestoreInfo() {
    return snapshotRestoreInfo;
  }

  public void setSnapshotRestoreInfo(SnapshotRestoreInfo snapshotRestoreInfo) {
    this.snapshotRestoreInfo = snapshotRestoreInfo;
  }

  public void setReplicationTarget(ReplicationTarget replicationTarget) {
    this.replicationTarget = replicationTarget;
  }

  public void setSnapshotRestoreInfo(long snapshotId, int numChunks) {
    if (snapshotId > 0) {
      setSnapshotRestoreInfo(new DefaultSnapshotRestoreInfo(snapshotId, numChunks));
    } else {
      setSnapshotRestoreInfo(NULL_SNAPSHOT_RESTORE_INFO);
    }
  }

  @Override
  public String toString() {
    return "DefaultRestoreInfoResponse{"
        + "replicationTarget="
        + replicationTarget
        + ", snapshotRestoreInfo="
        + snapshotRestoreInfo
        + '}';
  }
}
