/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.snapshot;

import io.zeebe.logstreams.state.SnapshotChunk;

public interface SnapshotRestoreResponse {

  /**
   * Indicates if the request was successful and the response contains the requested snapshot chunk
   */
  boolean isSuccess();

  /**
   * if {@link this::isSuccess()} is true, {@link this::getSnapshotChunk()} must return a non null
   * SnapshotChunk
   */
  SnapshotChunk getSnapshotChunk();
}
