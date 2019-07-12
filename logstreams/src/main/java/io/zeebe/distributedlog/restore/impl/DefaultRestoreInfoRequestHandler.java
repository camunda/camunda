/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.impl;

import io.zeebe.distributedlog.restore.RestoreInfoRequest;
import io.zeebe.distributedlog.restore.RestoreInfoResponse;
import io.zeebe.distributedlog.restore.RestoreInfoResponse.ReplicationTarget;
import io.zeebe.distributedlog.restore.RestoreServer.RestoreInfoRequestHandler;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreInfo;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.spi.SnapshotController;
import org.slf4j.Logger;

public class DefaultRestoreInfoRequestHandler implements RestoreInfoRequestHandler {
  private final SnapshotController snapshotController;
  private final LogStreamReader reader;
  private final LogStream logStream;

  public DefaultRestoreInfoRequestHandler(
      LogStream logStream, SnapshotController snapshotController) {
    this.logStream = logStream;
    this.reader = new BufferedLogStreamReader(logStream);
    this.snapshotController = snapshotController;
  }

  @Override
  public RestoreInfoResponse onRestoreInfoRequest(RestoreInfoRequest request, Logger logger) {
    RestoreInfoResponse response = DefaultRestoreInfoResponse.NONE;
    final long lastValidSnapshotPosition = snapshotController.getLastValidSnapshotPosition();

    logger.debug("Received restore info request {}", request);
    if (lastValidSnapshotPosition > -1
        && lastValidSnapshotPosition >= request.getLatestLocalPosition()) {
      final SnapshotRestoreInfo restoreInfo = snapshotController.getLatestSnapshotRestoreInfo();

      if (restoreInfo.getSnapshotId() >= request.getLatestLocalPosition()
          && restoreInfo.getNumChunks() > 0) {
        response = new DefaultRestoreInfoResponse(ReplicationTarget.SNAPSHOT, restoreInfo);
      }
    } else if (seekToRequestedPositionExclusive(request.getLatestLocalPosition())) {
      response = new DefaultRestoreInfoResponse(ReplicationTarget.EVENTS);
    }

    logger.debug(
        "Responding restore info request with {} (snapshot position: {}, log position: {})",
        response,
        lastValidSnapshotPosition,
        logStream.getCommitPosition());
    return response;
  }

  private boolean seekToRequestedPositionExclusive(long position) {
    if (position == -1) {
      reader.seekToFirstEvent();
      return reader.hasNext();
    }

    return reader.seek(position) && reader.hasNext();
  }
}
