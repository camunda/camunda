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
import io.camunda.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupStatusResponse;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class BackupRequestHandler implements BackupApi {

  final BrokerClient brokerClient;
  final BrokerTopologyManager topologyManager;

  public BackupRequestHandler(final BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
    topologyManager = brokerClient.getTopologyManager();
  }

  @Override
  public CompletionStage<Long> takeBackup(final long backupId) {
    final Either<Throwable, Boolean> topologyComplete = checkTopologyComplete();
    if (topologyComplete.isLeft()) {
      return CompletableFuture.failedFuture(
          operationFailed("take", backupId, topologyComplete.getLeft()));
    }

    final var backupTriggered =
        topologyManager.getTopology().getPartitions().stream()
            .map(partitionId -> getBackupRequestForPartition(backupId, partitionId))
            .map(brokerClient::sendRequestWithRetry)
            .toArray(CompletableFuture[]::new);

    return CompletableFuture.allOf(backupTriggered)
        .thenApply(ignore -> backupId)
        .exceptionallyCompose(
            error ->
                CompletableFuture.failedFuture(
                    operationFailed("take", backupId, error.getCause())));
  }

  @Override
  public CompletionStage<BackupStatus> getStatus(final long backupId) {
    final Either<Throwable, Boolean> topologyComplete = checkTopologyComplete();
    if (topologyComplete.isLeft()) {
      return CompletableFuture.failedFuture(
          operationFailed("query", backupId, topologyComplete.getLeft()));
    }

    final var statusesReceived =
        topologyManager.getTopology().getPartitions().stream()
            .map(partitionId -> getStatusQueryForPartition(backupId, partitionId))
            .map(brokerClient::sendRequestWithRetry)
            .toList();

    return CompletableFuture.allOf(statusesReceived.toArray(CompletableFuture[]::new))
        .thenApply(ignore -> aggregatePartitionStatus(backupId, statusesReceived))
        .exceptionallyCompose(
            error ->
                CompletableFuture.failedFuture(
                    operationFailed("query", backupId, error.getCause())));
  }

  private Either<Throwable, Boolean> checkTopologyComplete() {
    final BrokerClusterState topology = topologyManager.getTopology();
    if (topology == null) {
      return Either.left(new NoTopologyAvailableException());
    }

    final int expectedPartitionCount = topology.getPartitionsCount();
    final int knownPartitions = topology.getPartitions().size();
    if (expectedPartitionCount != knownPartitions) {
      return Either.left(
          new IncompleteTopologyException(
              "Expected to send request to all %d partitions, but found only %d partitions in topology."
                  .formatted(expectedPartitionCount, knownPartitions)));
    }

    return Either.right(true);
  }

  private static BackupOperationFailedException operationFailed(
      final String operation, final long backupId, final Throwable error) {
    return new BackupOperationFailedException(operation, backupId, error);
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

    if (combinedStatus.isEmpty()) {
      throw new IllegalStateException(
          "Backup status cannot be calculated from %s".formatted(partitionStatuses));
    }

    String failureReason = null;
    if (combinedStatus.get() == BackupStatusCode.FAILED) {
      failureReason = collectFailureReason(partitionStatuses);
    }
    return new BackupStatus(
        backupId, combinedStatus.get(), Optional.ofNullable(failureReason), partitionStatuses);
  }

  private String collectFailureReason(final List<PartitionBackupStatus> partitionStatuses) {
    return partitionStatuses.stream()
        .filter(p -> p.status() == BackupStatusCode.FAILED)
        .map(
            p -> {
              final var reason = p.failureReason().orElse("Unknown reason");
              return "Backup on partition %d failed due to %s.".formatted(p.partitionId(), reason);
            })
        .toList()
        .toString();
  }

  private static Optional<BackupStatusCode> getAggregatedStatus(
      final List<PartitionBackupStatus> partitionStatuses) {
    return partitionStatuses.stream()
        .map(PartitionBackupStatus::status)
        .reduce(BackupRequestHandler::combine);
  }

  private static BackupStatusCode combine(final BackupStatusCode x, final BackupStatusCode y) {
    // Failed > DoesNotExist > InProgress > Completed

    if (x == BackupStatusCode.FAILED || y == BackupStatusCode.FAILED) {
      return BackupStatusCode.FAILED;
    }
    if (x == BackupStatusCode.DOES_NOT_EXIST || y == BackupStatusCode.DOES_NOT_EXIST) {
      return BackupStatusCode.DOES_NOT_EXIST;
    }
    if (x == BackupStatusCode.IN_PROGRESS || y == BackupStatusCode.IN_PROGRESS) {
      return BackupStatusCode.IN_PROGRESS;
    }
    if (x == BackupStatusCode.COMPLETED && y == BackupStatusCode.COMPLETED) {
      return BackupStatusCode.COMPLETED;
    }

    // This would never happen
    return BackupStatusCode.SBE_UNKNOWN;
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
