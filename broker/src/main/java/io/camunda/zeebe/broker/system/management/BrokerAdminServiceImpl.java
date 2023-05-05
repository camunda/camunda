/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.management;

import static java.util.Objects.requireNonNull;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.partitioning.NoOpPartitionAdminAccess;
import io.camunda.zeebe.broker.partitioning.PartitionAdminAccess;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;

/**
 * A service that exposes interface to control some of the core functionalities of the broker such
 * as * Pause stream processing * Force take a snapshot
 *
 * <p>This is intended to be used only by advanced users
 */
public final class BrokerAdminServiceImpl extends Actor implements BrokerAdminService {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private PartitionAdminAccess adminAccess = new NoOpPartitionAdminAccess();
  private List<ZeebePartition> partitions = Collections.emptyList();

  public BrokerAdminServiceImpl() {}

  public void injectAdminAccess(final PartitionAdminAccess adminAccess) {
    this.adminAccess = requireNonNull(adminAccess);
  }

  public void injectPartitionInfoSource(
      @Deprecated /* TODO find smaller interface */ final List<ZeebePartition> partitions) {
    this.partitions = partitions;
  }

  @Override
  public void pauseStreamProcessing() {
    actor.call(this::pauseStreamProcessingOnAllPartitions);
  }

  @Override
  public void resumeStreamProcessing() {
    LOG.info("Resuming paused StreamProcessor on all partitions.");
    actor.call(() -> adminAccess.resumeProcessing());
  }

  @Override
  public void pauseExporting() {
    actor.call(this::pauseExportingOnAllPartitions);
  }

  @Override
  public void resumeExporting() {
    LOG.info("Resuming exporting on all partitions.");
    actor.call(() -> adminAccess.resumeExporting());
  }

  @Override
  public void takeSnapshot() {
    actor.call(this::takeSnapshotOnAllPartitions);
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
          if (partitions.isEmpty()) {
            // can happen before partitions are injected
            future.complete(partitionStatuses);
          } else {
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
          }
        });

    try {
      return future.get(5, TimeUnit.SECONDS);
    } catch (final Exception e) {
      LOG.warn("Error when querying partition status", e);
      return Map.of();
    }
  }

  private CompletableFuture<PartitionStatus> getPartitionStatus(final ZeebePartition partition) {
    final CompletableFuture<PartitionStatus> partitionStatus = new CompletableFuture<>();
    final var currentRoleFuture = partition.getCurrentRole();
    final var streamProcessorFuture = partition.getStreamProcessor();
    final var exporterDirectorFuture = partition.getExporterDirector();
    actor.runOnCompletion(
        List.of((ActorFuture) streamProcessorFuture, (ActorFuture) exporterDirectorFuture),
        error -> {
          if (error != null) {
            partitionStatus.completeExceptionally(error);
            return;
          }
          final var role = currentRoleFuture.join();
          final var streamProcessor = streamProcessorFuture.join();
          final var exporterDirector = exporterDirectorFuture.join();

          if (streamProcessor.isEmpty()) {
            partitionStatus.completeExceptionally(
                new IllegalStateException(
                    "No streamProcessor found for partition: %d."
                        .formatted(partition.getPartitionId())));
          } else if (exporterDirector.isEmpty()) {
            partitionStatus.completeExceptionally(
                new IllegalStateException(
                    "No exporter found for partition: %d .".formatted(partition.getPartitionId())));
          } else {
            getPartitionStatus(
                role, partition, streamProcessor.get(), exporterDirector.get(), partitionStatus);
          }
        });
    return partitionStatus;
  }

  private void getPartitionStatus(
      final Role role,
      final ZeebePartition partition,
      final StreamProcessor streamProcessor,
      final ExporterDirector exporterDirector,
      final CompletableFuture<PartitionStatus> partitionStatus) {

    final var positionFuture = streamProcessor.getLastProcessedPositionAsync();
    final var currentPhaseFuture = streamProcessor.getCurrentPhase();
    final var exporterPhaseFuture = exporterDirector.getPhase();
    final var exporterPositionFuture = exporterDirector.getLowestPosition();
    final var snapshotId = getSnapshotId(partition);
    final var processedPositionInSnapshot =
        snapshotId
            .flatMap(FileBasedSnapshotId::ofFileName)
            .map(FileBasedSnapshotId::getProcessedPosition)
            .orElse(null);

    actor.runOnCompletion(
        List.of(
            (ActorFuture) positionFuture,
            (ActorFuture) currentPhaseFuture,
            (ActorFuture) exporterPhaseFuture,
            (ActorFuture) exporterPositionFuture),
        error -> {
          if (error != null) {
            partitionStatus.completeExceptionally(error);
            return;
          }
          final var processedPosition = positionFuture.join();
          final var processorPhase = currentPhaseFuture.join();
          final var exporterPhase = exporterPhaseFuture.join();
          final var exporterPosition = exporterPositionFuture.join();
          final var status =
              new PartitionStatus(
                  role,
                  processedPosition,
                  snapshotId.orElse(null),
                  processedPositionInSnapshot,
                  processorPhase,
                  exporterPhase,
                  exporterPosition);
          partitionStatus.complete(status);
        });
  }

  private Optional<String> getSnapshotId(final ZeebePartition partition) {
    return partition.getSnapshotStore().getLatestSnapshot().map(PersistedSnapshot::getId);
  }

  private void prepareAllPartitionsForSafeUpgrade() {
    LOG.info("Preparing for safe upgrade.");

    final var pauseProcessingCompleted = pauseStreamProcessingOnAllPartitions();
    final var pauseExportingCompleted = pauseExportingOnAllPartitions();
    final var pauseAll =
        Stream.of(pauseProcessingCompleted, pauseExportingCompleted).collect(Collectors.toList());

    actor.runOnCompletion(pauseAll, t -> takeSnapshotOnAllPartitions());
  }

  private ActorFuture<Void> pauseStreamProcessingOnAllPartitions() {
    LOG.info("Pausing StreamProcessor on all partitions.");
    return adminAccess.pauseProcessing();
  }

  private ActorFuture<Void> takeSnapshotOnAllPartitions() {
    LOG.info("Triggering Snapshots on all partitions.");
    return adminAccess.takeSnapshot();
  }

  private ActorFuture<Void> pauseExportingOnAllPartitions() {
    LOG.info("Pausing exporting on all partitions.");
    return adminAccess.pauseExporting();
  }
}
