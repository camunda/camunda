/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore;

import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreInfo;

public interface RestoreInfoResponse {

  /** @return the replication target in order to restore the local log */
  ReplicationTarget getReplicationTarget();

  /**
   * @return restore info for snapshot if {@link this::getReplicationTarget()} is equal to SNAPSHOT.
   *     Otherwise returns null
   */
  SnapshotRestoreInfo getSnapshotRestoreInfo();

  enum ReplicationTarget {
    SNAPSHOT,
    EVENTS,
    NONE,
  }
}
