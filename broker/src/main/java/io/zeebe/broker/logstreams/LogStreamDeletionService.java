/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams;

import io.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.state.Snapshot;
import io.zeebe.logstreams.state.SnapshotDeletionListener;
import io.zeebe.logstreams.state.SnapshotStorage;
import io.zeebe.util.sched.Actor;

public class LogStreamDeletionService extends Actor implements SnapshotDeletionListener {
  private final LogStream logStream;
  private final StatePositionSupplier positionSupplier;
  private final SnapshotStorage snapshotStorage;

  public LogStreamDeletionService(
      final LogStream logStream,
      final SnapshotStorage snapshotStorage,
      final StatePositionSupplier positionSupplier) {
    this.snapshotStorage = snapshotStorage;
    this.logStream = logStream;
    this.positionSupplier = positionSupplier;
  }

  @Override
  protected void onActorStarting() {
    snapshotStorage.addDeletionListener(this);
  }

  @Override
  protected void onActorClosing() {
    if (snapshotStorage != null) {
      snapshotStorage.removeDeletionListener(this);
    }
  }

  @Override
  public void onSnapshotsDeleted(final Snapshot oldestRemainingSnapshot) {
    actor.run(() -> delegateDeletion(oldestRemainingSnapshot));
  }

  private void delegateDeletion(final Snapshot snapshot) {
    final long minPosition = positionSupplier.getLowestPosition(snapshot.getPath());
    logStream.delete(minPosition);
  }
}
