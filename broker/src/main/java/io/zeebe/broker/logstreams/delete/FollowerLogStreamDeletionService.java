/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams.delete;

import io.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.state.Snapshot;
import io.zeebe.logstreams.state.SnapshotDeletionListener;
import io.zeebe.logstreams.state.SnapshotStorage;

public class FollowerLogStreamDeletionService implements SnapshotDeletionListener {
  private final LogStream logStream;
  private final StatePositionSupplier positionSupplier;

  public FollowerLogStreamDeletionService(
      final LogStream logStream, final StatePositionSupplier positionSupplier) {
    this.logStream = logStream;
    this.positionSupplier = positionSupplier;
  }

  @Override
  public void onSnapshotDeleted(final Snapshot snapshot) {
    final long minPosition = Math.min(snapshot.getPosition(), getMinimumExportedPosition(snapshot));
    logStream.delete(minPosition);
  }

  private long getMinimumExportedPosition(final Snapshot snapshot) {
    return positionSupplier.getMinimumExportedPosition(snapshot.getPath());
  }
}
