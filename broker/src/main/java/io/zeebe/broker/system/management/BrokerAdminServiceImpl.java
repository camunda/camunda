/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.management;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.system.partitions.ZeebePartition;
import io.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.zeebe.snapshots.broker.impl.FileBasedSnapshotMetadata;
import io.zeebe.snapshots.raft.PersistedSnapshot;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * A service that exposes interface to control some of the core functionalities of the broker such
 * as * Pause stream processing * Force take a snapshot
 *
 * <p>This is intended to be used only by advanced users
 */
public class BrokerAdminServiceImpl extends Actor implements BrokerAdminService {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private final List<ZeebePartition> partitions;

  public BrokerAdminServiceImpl(final List<ZeebePartition> partitions) {
    this.partitions = partitions;
  }

  @Override
  public void pauseStreamProcessing() {
    actor.call(this::pauseStreamProcessingOnAllPartitions);
  }

  @Override
  public void resumeStreamProcessing() {
    actor.call(this::unpauseStreamProcessingOnAllPartitions);
  }

  @Override
  public void takeSnapshot() {
    actor.call(() -> takeSnapshotOnAllPartitions(partitions));
  }

  @Override
  public void prepareForUpgrade() {
    actor.call(this::prepareAllPartitionsForSafeUpgrade);
  }

  @Override
  public Map<Integer, PartitionStatus> getPartitionStatus() {
    final CompletableFuture<Map<Integer, PartitionStatus>> future = new CompletableFuture<>();
    final Map<Integer, PartitionStatus> partitionStatuses = new ConcurrentHashMap<>();
    actor.call(
        () -> {
          final var statusFutures =
              partitions.stream()
                  .map(
                      partition ->
                          getPartitionStatus(partition)
                              .whenComplete(
                                  (ps, error) -> {
                                    if (error == null) {
                                      partitionStatuses.put(partition.getPartitionId(), ps);
                                    }
                                  }))
                  .collect(Collectors.toList());
          CompletableFuture.allOf(statusFutures.toArray(CompletableFuture[]::new))
              .thenAccept(r -> future.complete(partitionStatuses));
        });
    return future.join();
  }

  private CompletableFuture<PartitionStatus> getPartitionStatus(final ZeebePartition partition) {
    final CompletableFuture<PartitionStatus> partitionStatus = new CompletableFuture<>();
    getStreamProcessor(partition)
        .onComplete(
            (streamProcessor, throwable) -> {
              if (throwable != null) {
                partitionStatus.completeExceptionally(throwable);
                return;
              }
              streamProcessor.ifPresentOrElse(
                  sp -> getLeaderPartitionStatus(partition, sp, partitionStatus),
                  () -> getFollowerPartitionStatus(partition, partitionStatus));
            });
    return partitionStatus;
  }

  private void getFollowerPartitionStatus(
      final ZeebePartition partition, final CompletableFuture<PartitionStatus> partitionStatus) {
    final var snapshotId = getSnapshotId(partition);
    final var status = PartitionStatus.ofFollower(snapshotId.orElse(null));
    partitionStatus.complete(status);
  }

  private void getLeaderPartitionStatus(
      final ZeebePartition partition,
      final StreamProcessor streamProcessor,
      final CompletableFuture<PartitionStatus> partitionStatus) {
    final var positionFuture = streamProcessor.getLastProcessedPositionAsync();
    positionFuture.onComplete(
        (processedPosition, positionRetrieveError) -> {
          if (positionRetrieveError != null) {
            partitionStatus.completeExceptionally(positionRetrieveError);
            return;
          }

          streamProcessor
              .getCurrentPhase()
              .onComplete(
                  (phase, phaseError) -> {
                    if (phaseError != null) {
                      partitionStatus.completeExceptionally(phaseError);
                      return;
                    }

                    final var snapshotId = getSnapshotId(partition);
                    final var processedPositionInSnapshot =
                        snapshotId
                            .flatMap(s -> FileBasedSnapshotMetadata.ofFileName(s))
                            .map(FileBasedSnapshotMetadata::getProcessedPosition)
                            .orElse(null);
                    final var status =
                        PartitionStatus.ofLeader(
                            processedPosition,
                            snapshotId.orElse(null),
                            processedPositionInSnapshot,
                            phase);
                    partitionStatus.complete(status);
                  });
        });
  }

  private Optional<String> getSnapshotId(final ZeebePartition partition) {
    return partition.getSnapshotStore().getLatestSnapshot().map(PersistedSnapshot::getId);
  }

  private ActorFuture<Optional<StreamProcessor>> getStreamProcessor(
      final ZeebePartition partition) {
    return partition.getStreamProcessor();
  }

  private void prepareAllPartitionsForSafeUpgrade() {
    LOG.info("Preparing for safe upgrade.");

    final var pauseCompleted = pauseStreamProcessingOnAllPartitions();

    actor.runOnCompletion(pauseCompleted, t -> takeSnapshotOnAllPartitions(partitions));
  }

  private List<ActorFuture<Void>> pauseStreamProcessingOnAllPartitions() {
    LOG.info("Pausing StreamProcessor on all partitions.");
    return partitions.stream().map(ZeebePartition::pauseProcessing).collect(Collectors.toList());
  }

  private void unpauseStreamProcessingOnAllPartitions() {
    LOG.info("Resuming paused StreamProcessor on all partitions.");
    partitions.forEach(ZeebePartition::resumeProcessing);
  }

  private void takeSnapshotOnAllPartitions(final List<ZeebePartition> partitions) {
    LOG.info("Triggering Snapshots on all partitions.");
    partitions.forEach(ZeebePartition::triggerSnapshot);
  }
}
