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

import io.zeebe.distributedlog.restore.RestoreInfoRequest;
import io.zeebe.distributedlog.restore.RestoreInfoResponse;
import io.zeebe.distributedlog.restore.RestoreInfoResponse.ReplicationTarget;
import io.zeebe.distributedlog.restore.RestoreServer.RestoreInfoRequestHandler;
import io.zeebe.distributedlog.restore.snapshot.impl.DefaultSnapshotRestoreInfo;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.spi.SnapshotController;
import java.io.File;
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
      final File lastValidSnapshotDirectory = snapshotController.getLastValidSnapshotDirectory();
      final File[] chunks = lastValidSnapshotDirectory.listFiles();

      if (chunks != null && chunks.length > 0) {
        response =
            new DefaultRestoreInfoResponse(
                ReplicationTarget.SNAPSHOT,
                new DefaultSnapshotRestoreInfo(lastValidSnapshotPosition, chunks.length));
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
