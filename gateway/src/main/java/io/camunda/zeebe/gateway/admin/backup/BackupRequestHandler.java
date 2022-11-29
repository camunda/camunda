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
import io.camunda.zeebe.protocol.impl.encoding.BackupListResponse;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
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
                  .thenApply(
                      ignore -> {
                        final var partitionStatuses =
                            statusesReceived.stream()
                                .map(response -> response.join().getResponse())
                                .map(PartitionBackupStatus::from)
                                .toList();

                        return aggregatePartitionStatus(backupId, partitionStatuses);
                      });
            });
  }

  @Override
  public CompletionStage<List<BackupStatus>> listBackups() {
    return checkTopologyComplete()
        .thenCompose(
            topology -> {
              final var backupsReceived =
                  topology.getPartitions().stream()
                      .map(this::getListRequest)
                      .map(brokerClient::sendRequestWithRetry)
                      .toList();

              return CompletableFuture.allOf(backupsReceived.toArray(CompletableFuture[]::new))
                  .thenApply(ignore -> aggregateBackupList(backupsReceived));
            });
  }

  private List<BackupStatus> aggregateBackupList(
      final List<CompletableFuture<BrokerResponse<BackupListResponse>>> backupsReceived) {
    final var backupStatuses =
        backupsReceived.stream()
            .map(f -> f.join().getResponse())
            .map(BackupListResponse::getBackups)
            .flatMap(List::stream)
            .toList();

    // backupId -> [partitiondId -> partitionBackupStatus]
    final var backups =
        backupStatuses.stream()
            .map(BackupListResponse.BackupStatus::backupId)
            .distinct()
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    backupId -> {
                      // If a partition does not have this backup, it is not included in the
                      // response received. So when aggregating backup status, an incomplete
                      // backup can be determined as completed. To prevent that initialize all
                      // partitions status to DOES_NOT_EXIST. This will be overwritten with the
                      // actual status returned if it exists.
                      final var partitionStatus = new HashMap<Integer, PartitionBackupStatus>();
                      topologyManager
                          .getTopology()
                          .getPartitions()
                          .forEach(
                              p ->
                                  partitionStatus.put(
                                      p, PartitionBackupStatus.notExistingStatus(p)));
                      return partitionStatus;
                    }));

    backupStatuses.forEach(
        status ->
            backups
                .get(status.backupId())
                .put(
                    status.partitionId(),
                    new PartitionBackupStatus(
                        status.partitionId(),
                        status.status(),
                        Optional.ofNullable(status.failureReason()),
                        Optional.ofNullable(status.createdAt()),
                        Optional.empty(),
                        Optional.empty(),
                        OptionalLong.empty(),
                        OptionalInt.empty(),
                        Optional.ofNullable(status.brokerVersion()))));

    return backups.entrySet().stream()
        .map(
            entry ->
                aggregatePartitionStatus(
                    entry.getKey(), entry.getValue().values().stream().toList()))
        .toList();
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
      final long backupId, final List<PartitionBackupStatus> partitionStatuses) {

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

    if (statuses.contains(BackupStatusCode.DOES_NOT_EXIST)) {
      return State.DOES_NOT_EXIST;
    }

    if (statuses.size() == 1 && statuses.contains(BackupStatusCode.COMPLETED)) {
      return State.COMPLETED;
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

  private BackupListRequest getListRequest(final Integer partitionId) {
    final var request = new BackupListRequest();
    request.setPartitionId(partitionId);
    return request;
  }
}
