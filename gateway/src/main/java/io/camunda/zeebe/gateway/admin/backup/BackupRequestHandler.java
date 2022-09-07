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

public final class BackupRequestHandler implements BackupApi {

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
      return CompletableFuture.failedFuture(
          backupFailed(backupId, new NoTopologyAvailableException()));
    }

    final int expectedPartitionCount = topology.getPartitionsCount();
    final int knownPartitions = topology.getPartitions().size();
    if (expectedPartitionCount != knownPartitions) {
      return CompletableFuture.failedFuture(
          backupFailed(
              backupId,
              new IncompleteTopologyException(
                  "Expected to send request to all %d partitions, but found only %d partitions in topology."
                      .formatted(expectedPartitionCount, knownPartitions))));
    }

    final var backupTriggered =
        topology.getPartitions().stream()
            .map(partitionId -> getRequestForPartition(backupId, partitionId))
            .map(brokerClient::sendRequestWithRetry)
            .toArray(CompletableFuture[]::new);

    return CompletableFuture.allOf(backupTriggered)
        .thenApply(ignore -> backupId)
        .exceptionallyCompose(
            error -> CompletableFuture.failedFuture(backupFailed(backupId, error.getCause())));
  }

  private static BackupFailedException backupFailed(final long backupId, final Throwable error) {
    return new BackupFailedException(backupId, error);
  }

  private static BrokerBackupRequest getRequestForPartition(
      final long backupId, final int partitionId) {
    final var request = new BrokerBackupRequest();
    request.setBackupId(backupId);
    request.setPartitionId(partitionId);
    return request;
  }
}
