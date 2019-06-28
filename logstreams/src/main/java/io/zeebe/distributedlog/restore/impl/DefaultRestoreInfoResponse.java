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
package io.zeebe.distributedlog.restore.impl;

import io.zeebe.distributedlog.restore.RestoreInfoResponse;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreInfo;
import io.zeebe.distributedlog.restore.snapshot.impl.DefaultSnapshotRestoreInfo;
import io.zeebe.distributedlog.restore.snapshot.impl.NullSnapshotRestoreInfo;

public class DefaultRestoreInfoResponse implements RestoreInfoResponse {
  private ReplicationTarget replicationTarget;
  private SnapshotRestoreInfo snapshotRestoreInfo;

  static final DefaultRestoreInfoResponse NONE =
      new DefaultRestoreInfoResponse(ReplicationTarget.NONE);
  private static final NullSnapshotRestoreInfo NULL_SNAPSHOT_RESTORE_INFO =
      new NullSnapshotRestoreInfo();

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

  public void setReplicationTarget(ReplicationTarget replicationTarget) {
    this.replicationTarget = replicationTarget;
  }

  public void setSnapshotRestoreInfo(SnapshotRestoreInfo snapshotRestoreInfo) {
    this.snapshotRestoreInfo = snapshotRestoreInfo;
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
