/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.util.sched.future.ActorFuture;

public interface StateController extends AutoCloseable {
  /**
   * Takes a snapshot based on the given position. The position is a last processed lower bound
   * event position. When the returned future completes successfully the transient snapshot will be
   * valid.
   *
   * @param lowerBoundSnapshotPosition the lower bound snapshot position
   * @return a future
   */
  ActorFuture<TransientSnapshot> takeTransientSnapshot(long lowerBoundSnapshotPosition);

  /**
   * Recovers the state from the snapshot and opens the database
   *
   * @return ZeebeDb the database which is recovered from the snapshot.
   */
  ActorFuture<ZeebeDb> recover();

  /**
   * Close db if it was opened by {@link StateController#recover()}
   *
   * @return future
   */
  ActorFuture<Void> closeDb();
}
