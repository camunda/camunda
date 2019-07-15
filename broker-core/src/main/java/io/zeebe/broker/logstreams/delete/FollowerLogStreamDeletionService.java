/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams.delete;

import io.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.zeebe.logstreams.impl.delete.DeletionService;
import io.zeebe.logstreams.log.LogStream;

public class FollowerLogStreamDeletionService implements DeletionService {
  private final LogStream logStream;
  private StatePositionSupplier positionSupplier;

  public FollowerLogStreamDeletionService(
      LogStream logStream, StatePositionSupplier positionSupplier) {
    this.logStream = logStream;
    this.positionSupplier = positionSupplier;
  }

  @Override
  public void delete(final long position) {
    final long minPosition = Math.min(position, getMinimumExportedPosition());
    logStream.delete(minPosition);
  }

  private long getMinimumExportedPosition() {
    return positionSupplier.getMinimumExportedPosition();
  }
}
