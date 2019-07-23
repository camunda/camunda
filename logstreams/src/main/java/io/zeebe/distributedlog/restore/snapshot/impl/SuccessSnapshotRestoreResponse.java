/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.snapshot.impl;

import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreResponse;
import io.zeebe.logstreams.state.SnapshotChunk;

public class SuccessSnapshotRestoreResponse implements SnapshotRestoreResponse {

  private SnapshotChunk snapshotChunk;

  public SuccessSnapshotRestoreResponse(SnapshotChunk snapshotChunk) {
    this.snapshotChunk = snapshotChunk;
  }

  @Override
  public boolean isSuccess() {
    return true;
  }

  @Override
  public SnapshotChunk getSnapshotChunk() {
    return snapshotChunk;
  }

  @Override
  public String toString() {
    return "SuccessSnapshotRestoreResponse{" + "snapshotChunk=" + snapshotChunk + '}';
  }
}
