/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.state;

/** Implementations will be called when snapshots have been purged. */
@FunctionalInterface
public interface SnapshotDeletionListener {

  /**
   * Called by a {@link SnapshotStorage} whenever snapshots are removed.
   *
   * @param oldestRemainingSnapshot the oldest remaining snapshot
   */
  void onSnapshotsDeleted(Snapshot oldestRemainingSnapshot);
}
