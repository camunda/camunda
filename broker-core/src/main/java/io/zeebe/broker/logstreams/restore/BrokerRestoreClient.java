/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams.restore;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.zeebe.distributedlog.restore.RestoreClient;
import io.zeebe.distributedlog.restore.RestoreInfoRequest;
import io.zeebe.distributedlog.restore.RestoreInfoResponse;
import io.zeebe.distributedlog.restore.log.LogReplicationRequest;
import io.zeebe.distributedlog.restore.log.LogReplicationResponse;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreRequest;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreResponse;
import io.zeebe.util.ZbLogger;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

public class BrokerRestoreClient implements RestoreClient {
  private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private final ClusterCommunicationService communicationService;
  private final String logReplicationTopic;
  private final String restoreInfoTopic;
  private final String snapshotRequestTopic;
  private final Logger logger;

  public BrokerRestoreClient(ClusterCommunicationService communicationService, int partitionId) {
    this(
        communicationService,
        BrokerRestoreFactory.getLogReplicationTopic(partitionId),
        BrokerRestoreFactory.getRestoreInfoTopic(partitionId),
        BrokerRestoreFactory.getSnapshotRequestTopic(partitionId),
        new ZbLogger(String.format("%s-%d", BrokerRestoreClient.class.getName(), partitionId)));
  }

  public BrokerRestoreClient(
      ClusterCommunicationService communicationService,
      String logReplicationTopic,
      String restoreInfoTopic,
      String snapshotRequestTopic,
      Logger logger) {
    this.communicationService = communicationService;
    this.logReplicationTopic = logReplicationTopic;
    this.restoreInfoTopic = restoreInfoTopic;
    this.snapshotRequestTopic = snapshotRequestTopic;
    this.logger = logger;
  }

  @Override
  public CompletableFuture<SnapshotRestoreResponse> requestSnapshotChunk(
      MemberId server, SnapshotRestoreRequest request) {
    return communicationService.send(
        snapshotRequestTopic,
        request,
        SbeSnapshotRestoreRequest::serialize,
        SbeSnapshotRestoreResponse::new,
        server,
        DEFAULT_REQUEST_TIMEOUT);
  }

  @Override
  public CompletableFuture<LogReplicationResponse> requestLogReplication(
      MemberId server, LogReplicationRequest request) {
    logger.trace(
        "Sending log replication request {} to {} on topic {}",
        request,
        server,
        logReplicationTopic);
    return communicationService.send(
        logReplicationTopic,
        request,
        SbeLogReplicationRequest::serialize,
        SbeLogReplicationResponse::new,
        server,
        DEFAULT_REQUEST_TIMEOUT);
  }

  @Override
  public CompletableFuture<RestoreInfoResponse> requestRestoreInfo(
      MemberId server, RestoreInfoRequest request) {
    logger.trace(
        "Sending restore info request {} to {} on topic {}", request, server, restoreInfoTopic);
    return communicationService.send(
        restoreInfoTopic,
        request,
        SbeRestoreInfoRequest::serialize,
        SbeRestoreInfoResponse::new,
        server,
        DEFAULT_REQUEST_TIMEOUT);
  }
}
