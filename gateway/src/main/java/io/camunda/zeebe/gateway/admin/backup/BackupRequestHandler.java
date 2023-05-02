/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.admin.backup;

import static java.lang.Long.max;

import io.camunda.zeebe.gateway.admin.IncompleteTopologyException;
import io.camunda.zeebe.gateway.cmd.NoTopologyAvailableException;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupListResponse;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import java.util.Collections;
import java.util.Comparator;
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
            topology -> {
              final var backupsTaken =
                  topology.getPartitions().stream()
                      .map(partitionId -> createBackupRequest(backupId, partitionId))
                      .map(brokerClient::sendRequestWithRetry)
                      .toList();

              return CompletableFuture.allOf(backupsTaken.toArray(CompletableFuture[]::new))
                  .thenApply(
                      ignore -> {

                        // If all partition created checkpoint, then return success.
                        // If all partitions rejected with the requested id or some rejected with a
                        // higher id, then fail.
                        // If some partitions created checkpoint, other partition
                        // rejected with the same id then return success. The partitions that
                        // rejected might have created a checkpoint due to inter-partition
                        // communication, or it had already created one in a previous attempt to
                        // take the backup. Since it is difficult to distinguish these cases, we
                        // assume it as a success because other partitions have created a new
                        // backup.
                        final var aggregatedResponse =
                            backupsTaken.stream()
                                .map(response -> response.join().getResponse())
                                .distinct()
                                .reduce(
                                    (r1, r2) ->
                                        new BackupResponse(
                                            r1.created() || r2.created(),
                                            max(r1.checkpointId(), r2.checkpointId())))
                                .orElseThrow();

                        if (aggregatedResponse.created()
                            && aggregatedResponse.checkpointId() == backupId) {
                          // atleast one partition created a new checkpoint && all partitions have
                          // the latest checkpoint at backupId
                          return backupId;
                        } else {
                          throw new BackupAlreadyExistException(
                              backupId, aggregatedResponse.checkpointId());
                        }
                      });
            });
  }

  @Override
  public CompletionStage<BackupStatus> getStatus(final long backupId) {
    return checkTopologyComplete()
        .thenCompose(
            topology -> {
              final var statusesReceived =
                  topology.getPartitions().stream()
                      .map(partitionId -> createStatusQueryRequest(backupId, partitionId))
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
                      .map(this::createListRequest)
                      .map(brokerClient::sendRequestWithRetry)
                      .toList();

              return CompletableFuture.allOf(backupsReceived.toArray(CompletableFuture[]::new))
                  .thenApply(ignore -> aggregateBackupList(backupsReceived));
            });
  }

  @Override
  public CompletionStage<Void> deleteBackup(final long backupId) {
    return checkTopologyComplete()
        .thenCompose(
            topology ->
                CompletableFuture.allOf(
                    topology.getPartitions().stream()
                        .map(partitionId -> createDeleteRequest(backupId, partitionId))
                        .map(brokerClient::sendRequestWithRetry)
                        .toArray(CompletableFuture[]::new)));
  }

  private List<BackupStatus> aggregateBackupList(
      final List<CompletableFuture<BrokerResponse<BackupListResponse>>> backupsReceived) {
    // backupId -> [partitiondId -> partitionBackupStatus]
    final var statusByBackupAndPartition =
        backupsReceived.stream()
            .map(f -> f.join().getResponse())
            .flatMap(backupListResponse -> backupListResponse.getBackups().stream())
            .collect(
                Collectors.groupingBy(
                    BackupListResponse.BackupStatus::backupId,
                    Collectors.toMap(
                        BackupListResponse.BackupStatus::partitionId,
                        Function.identity(),
                        this::mergeDuplicatePartitionBackupStatus)));

    final var partitions = topologyManager.getTopology().getPartitions();
    // calculate status of each backup from the status of each partition
    return statusByBackupAndPartition.entrySet().stream()
        .map(
            entry -> {
              final var backupId = entry.getKey();
              final var statusByPartition = entry.getValue();
              return aggregatePartitionStatus(
                  backupId,
                  partitions.stream()
                      .map(
                          partitionId -> {
                            if (!statusByPartition.containsKey(partitionId)) {
                              // If a partition does not have this backup, it is not included in the
                              // response received. So when aggregating backup status, an incomplete
                              // backup can be determined as completed. To prevent that replace a
                              // missing status with all DOES_NOT_EXIST.
                              return PartitionBackupStatus.notExistingStatus(partitionId);
                            }
                            final var status = statusByPartition.get(partitionId);
                            return new PartitionBackupStatus(
                                status.partitionId(),
                                status.status(),
                                status.status() == BackupStatusCode.FAILED
                                    ? Optional.ofNullable(status.failureReason())
                                    : Optional.empty(),
                                Optional.ofNullable(status.createdAt()),
                                Optional.empty(),
                                Optional.empty(),
                                OptionalLong.empty(),
                                OptionalInt.empty(),
                                Optional.ofNullable(status.brokerVersion()));
                          })
                      .toList());
            })
        .toList();
  }

  // When a backup status returns more than one status for a partition, this method helps to
  // choose the best one from the available backups of a partition.
  private BackupListResponse.BackupStatus mergeDuplicatePartitionBackupStatus(
      final BackupListResponse.BackupStatus x, final BackupListResponse.BackupStatus y) {
    if (x.partitionId() != y.partitionId()) {
      throw new IllegalArgumentException(
          "Expected to merge backup status from same partitions, but provided backups of different partitions. Provided backups : %s, %s"
              .formatted(x, y));
    }

    final List<BackupStatusCode> comparingOrder =
        List.of(
            BackupStatusCode.SBE_UNKNOWN,
            BackupStatusCode.DOES_NOT_EXIST,
            BackupStatusCode.FAILED,
            BackupStatusCode.IN_PROGRESS,
            BackupStatusCode.COMPLETED);

    return Collections.max(
        List.of(x, y),
        Comparator.comparing(
            BackupListResponse.BackupStatus::status,
            Comparator.comparingInt(comparingOrder::indexOf)));
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

  private BackupStatusRequest createStatusQueryRequest(final long backupId, final int partitionId) {
    final var request = new BackupStatusRequest();
    request.setBackupId(backupId);
    request.setPartitionId(partitionId);
    return request;
  }

  private static BrokerBackupRequest createBackupRequest(
      final long backupId, final int partitionId) {
    final var request = new BrokerBackupRequest();
    request.setBackupId(backupId);
    request.setPartitionId(partitionId);
    return request;
  }

  private BackupListRequest createListRequest(final Integer partitionId) {
    final var request = new BackupListRequest();
    request.setPartitionId(partitionId);
    return request;
  }

  private BackupDeleteRequest createDeleteRequest(final long backupId, final Integer partitionId) {
    final var request = new BackupDeleteRequest();
    request.setPartitionId(partitionId);
    request.setBackupId(backupId);
    return request;
  }
}
