/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.admin.backup;

import io.camunda.zeebe.gateway.admin.IncompleteTopologyException;
import io.camunda.zeebe.gateway.cmd.NoTopologyAvailableException;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupStatusResponse;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public final class BackupRequestHandler implements BackupApi {

  final BrokerClient brokerClient;
  final BrokerTopologyManager topologyManager;

  public BackupRequestHandler(final BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
    topologyManager = brokerClient.getTopologyManager();
  }

  @Override
  public CompletionStage<Long> takeBackup(final long backupId) {
    return checkTopologyComplete()
        .thenCompose(
            topology ->
                CompletableFuture.allOf(
                        topology.getPartitions().stream()
                            .map(partitionId -> getBackupRequestForPartition(backupId, partitionId))
                            .map(brokerClient::sendRequestWithRetry)
                            .toArray(CompletableFuture[]::new))
                    .thenApply(ignore -> backupId));
  }

  @Override
  public CompletionStage<BackupStatus> getStatus(final long backupId) {
    return checkTopologyComplete()
        .thenCompose(
            topology -> {
              final var statusesReceived =
                  topology.getPartitions().stream()
                      .map(partitionId -> getStatusQueryForPartition(backupId, partitionId))
                      .map(brokerClient::sendRequestWithRetry)
                      .toList();

              return CompletableFuture.allOf(statusesReceived.toArray(CompletableFuture[]::new))
                  .thenApply(ignore -> aggregatePartitionStatus(backupId, statusesReceived));
            });
  }

  private CompletionStage<BrokerClusterState> checkTopologyComplete() {
    final BrokerClusterState topology = topologyManager.getTopology();
    if (topology == null) {
      return CompletableFuture.failedFuture(new NoTopologyAvailableException());
    }

    final int expectedPartitionCount = topology.getPartitionsCount();
    final int knownPartitions = topology.getPartitions().size();
    if (expectedPartitionCount != knownPartitions) {
      return CompletableFuture.failedFuture(
          new IncompleteTopologyException(
              "Expected to send request to all %d partitions, but found only %d partitions in topology."
                  .formatted(expectedPartitionCount, knownPartitions)));
    }

    return CompletableFuture.completedFuture(topology);
  }

  private BackupStatus aggregatePartitionStatus(
      final long backupId,
      final List<CompletableFuture<BrokerResponse<BackupStatusResponse>>> completedFutures) {

    final var partitionStatuses =
        completedFutures.stream()
            .map(response -> response.join().getResponse())
            .map(PartitionBackupStatus::from)
            .toList();

    final var combinedStatus = getAggregatedStatus(partitionStatuses);

    String failureReason = null;
    if (combinedStatus == State.FAILED) {
      failureReason = collectFailureReason(partitionStatuses);
    }
    return new BackupStatus(
        backupId, combinedStatus, Optional.ofNullable(failureReason), partitionStatuses);
  }

  private String collectFailureReason(final List<PartitionBackupStatus> partitionStatuses) {
    return partitionStatuses.stream()
        .filter(p -> p.status() == BackupStatusCode.FAILED)
        .map(
            p -> {
              final var reason = p.failureReason().orElse("Unknown reason");
              return "Backup on partition %d failed due to %s. ".formatted(p.partitionId(), reason);
            })
        .collect(Collectors.joining());
  }

  private State getAggregatedStatus(final List<PartitionBackupStatus> partitionStatuses) {
    final var statuses =
        partitionStatuses.stream().map(PartitionBackupStatus::status).distinct().toList();

    if (statuses.contains(BackupStatusCode.FAILED)) {
      return State.FAILED;
    }
    if ((statuses.contains(BackupStatusCode.IN_PROGRESS)
            || statuses.contains(BackupStatusCode.COMPLETED))
        && statuses.contains(BackupStatusCode.DOES_NOT_EXIST)) {
      return State.INCOMPLETE;
    }
    if (statuses.contains(BackupStatusCode.IN_PROGRESS)) {
      return State.IN_PROGRESS;
    }
    if (statuses.contains(BackupStatusCode.COMPLETED)) {
      return State.COMPLETED;
    }

    if (statuses.contains(BackupStatusCode.DOES_NOT_EXIST)) {
      return State.DOES_NOT_EXIST;
    }

    // This should never happen, because partitionStatuses would never be empty.
    throw new IllegalStateException(
        "Backup status cannot be calculated from status of partitions backup %s. Possible incomplete topology."
            .formatted(partitionStatuses));
  }

  private BackupStatusRequest getStatusQueryForPartition(
      final long backupId, final int partitionId) {
    final var request = new BackupStatusRequest();
    request.setBackupId(backupId);
    request.setPartitionId(partitionId);
    return request;
  }

  private static BrokerBackupRequest getBackupRequestForPartition(
      final long backupId, final int partitionId) {
    final var request = new BrokerBackupRequest();
    request.setBackupId(backupId);
    request.setPartitionId(partitionId);
    return request;
  }
}
