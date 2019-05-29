/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.logstreams.restore;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.zeebe.distributedlog.restore.RestoreServer;
import io.zeebe.distributedlog.restore.impl.DefaultRestoreInfoRequestHandler;
import io.zeebe.distributedlog.restore.log.impl.DefaultLogReplicationRequestHandler;
import io.zeebe.distributedlog.restore.snapshot.impl.DefaultSnapshotRequestHandler;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.util.ZbLogger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;

public class BrokerRestoreServer implements RestoreServer {
  private final ClusterCommunicationService communicationService;
  private final String logReplicationTopic;
  private final String restoreInfoTopic;
  private final String snapshotRequestTopic;
  private final String snapshotInfoRequestTopic;
  private final ExecutorService executor;
  private final Logger logger;

  public BrokerRestoreServer(ClusterCommunicationService communicationService, int partitionId) {
    this(
        communicationService,
        BrokerRestoreFactory.getLogReplicationTopic(partitionId),
        BrokerRestoreFactory.getRestoreInfoTopic(partitionId),
        BrokerRestoreFactory.getSnapshotRequestTopic(partitionId),
        BrokerRestoreFactory.getSnapshotInfoRequestTopic(partitionId),
        Executors.newSingleThreadExecutor(
            r -> new Thread(r, String.format(BrokerRestoreServer.class.getName(), partitionId))),
        new ZbLogger(String.format(BrokerRestoreServer.class.getName(), partitionId)));
  }

  public BrokerRestoreServer(
      ClusterCommunicationService communicationService,
      String logReplicationTopic,
      String restoreInfoTopic,
      String snapshotRequestTopic,
      String snapshotInfoRequestTopic,
      ExecutorService executor,
      Logger logger) {
    this.communicationService = communicationService;
    this.logReplicationTopic = logReplicationTopic;
    this.restoreInfoTopic = restoreInfoTopic;
    this.snapshotRequestTopic = snapshotRequestTopic;
    this.snapshotInfoRequestTopic = snapshotInfoRequestTopic;
    this.executor = executor;
    this.logger = logger;
  }

  public CompletableFuture<Void> start(LogStream logStream, SnapshotController snapshotController) {
    final LogReplicationRequestHandler logReplicationHandler =
        new DefaultLogReplicationRequestHandler(logStream);
    final RestoreInfoRequestHandler restoreInfoHandler =
        new DefaultRestoreInfoRequestHandler(logStream, snapshotController);
    final SnapshotRequestHandler snapshotRequestHandler =
        new DefaultSnapshotRequestHandler(snapshotController);

    return serve(logReplicationHandler)
        .thenCompose(nothing -> serve(restoreInfoHandler))
        .thenCompose(nothing -> serve(snapshotRequestHandler))
        .thenRun(this::logServerStart);
  }

  @Override
  public void close() {
    communicationService.unsubscribe(logReplicationTopic);
    communicationService.unsubscribe(restoreInfoTopic);
    communicationService.unsubscribe(snapshotRequestTopic);
    communicationService.unsubscribe(snapshotInfoRequestTopic);
    executor.shutdownNow();

    logger.debug(
        "Closed restore server for topics: {}, {}, {}, {}",
        logReplicationTopic,
        restoreInfoTopic,
        snapshotRequestTopic,
        snapshotInfoRequestTopic);
  }

  @Override
  public CompletableFuture<Void> serve(LogReplicationRequestHandler handler) {
    logger.trace("Subscribed handler {} to topic {}", logReplicationTopic, handler);
    return communicationService.subscribe(
        logReplicationTopic,
        SbeLogReplicationRequest::new,
        handler::onReplicationRequest,
        SbeLogReplicationResponse::serialize,
        executor);
  }

  @Override
  public CompletableFuture<Void> serve(RestoreInfoRequestHandler handler) {
    logger.trace("Subscribed handler {} to topic {}", restoreInfoTopic, handler);
    return communicationService.subscribe(
        restoreInfoTopic,
        SbeRestoreInfoRequest::new,
        handler::onRestoreInfoRequest,
        SbeRestoreInfoResponse::serialize,
        executor);
  }

  @Override
  public CompletableFuture<Void> serve(SnapshotRequestHandler handler) {
    logger.trace("Subscribed handler {} to topic {}", snapshotRequestTopic, handler);
    return communicationService.subscribe(
        snapshotRequestTopic,
        SbeSnapshotRestoreRequest::new,
        handler::onSnapshotRequest,
        SbeSnapshotRestoreResponse::serialize,
        executor);
  }

  private void logServerStart() {
    logger.debug(
        "Started restore server for topics: {}, {}, {}, {}",
        logReplicationTopic,
        restoreInfoTopic,
        snapshotRequestTopic,
        snapshotInfoRequestTopic);
  }
}
