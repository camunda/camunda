/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.admin.backup;

import io.camunda.zeebe.gateway.cmd.NoTopologyAvailableException;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

public class BackupRequestHandler implements BackupApi {

  final BrokerClient brokerClient;
  final BrokerTopologyManager topologyManager;

  public BackupRequestHandler(final BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
    topologyManager = brokerClient.getTopologyManager();
  }

  @Override
  public CompletableFuture<Long> takeBackup(final long backupId) {
    final BrokerClusterState topology = topologyManager.getTopology();
    if (topology == null) {
      return CompletableFuture.failedFuture(new NoTopologyAvailableException());
    }

    final int partitionsCount = topology.getPartitionsCount();

    final var backupTriggered =
        IntStream.rangeClosed(1, partitionsCount)
            .mapToObj(partitionId -> getRequestForPartition(backupId, partitionId))
            .map(brokerClient::sendRequestWithRetry)
            .toArray(CompletableFuture[]::new);

    return CompletableFuture.allOf(backupTriggered)
        .thenApply(ignore -> backupId)
        .exceptionallyCompose(
            error ->
                CompletableFuture.failedFuture(
                    new BackupFailedException(backupId, error.getCause())));
  }

  private static BrokerBackupRequest getRequestForPartition(
      final long backupId, final int partitionId) {
    final var request = new BrokerBackupRequest();
    request.setBackupId(backupId);
    request.setPartitionId(partitionId);
    return request;
  }
}
