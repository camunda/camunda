/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

final class MultiPartitionAdminAccess implements PartitionAdminAccess {
  private final ConcurrencyControl concurrencyControl;
  private final Map<Integer, CompletableFuture<? extends ZeebePartition>> partitions;

  MultiPartitionAdminAccess(
      final ConcurrencyControl concurrencyControl,
      final Map<Integer, CompletableFuture<ZeebePartition>> partitions) {
    this.concurrencyControl = requireNonNull(concurrencyControl);
    this.partitions = Collections.unmodifiableMap(requireNonNull(partitions));
  }

  /**
   * @return A scoped-down admin access that that only act's on the given partition, not all
   *     partitions
   */
  @Override
  public Optional<PartitionAdminAccess> forPartition(final int partitionId) {
    return Optional.ofNullable(
        partitions
            .get(partitionId)
            .thenApply(ZeebePartition::createAdminAccess)
            .orTimeout(5000, TimeUnit.MILLISECONDS)
            .join());
  }

  @Override
  public ActorFuture<Void> takeSnapshot() {
    return callOnEachPartition(PartitionAdminAccess::takeSnapshot);
  }

  @Override
  public ActorFuture<Void> pauseExporting() {
    return callOnEachPartition(PartitionAdminAccess::pauseExporting);
  }

  @Override
  public ActorFuture<Void> resumeExporting() {
    return callOnEachPartition(PartitionAdminAccess::resumeExporting);
  }

  @Override
  public ActorFuture<Void> pauseProcessing() {
    return callOnEachPartition(PartitionAdminAccess::pauseProcessing);
  }

  @Override
  public ActorFuture<Void> resumeProcessing() {
    return callOnEachPartition(PartitionAdminAccess::resumeProcessing);
  }

  @Override
  public ActorFuture<Void> banInstance(final long processInstanceKey) {
    final var partitionId = Protocol.decodePartitionId(processInstanceKey);
    final var partition =
        partitions
            .get(partitionId)
            .thenApply(ZeebePartition::createAdminAccess)
            .orTimeout(1000, TimeUnit.MILLISECONDS)
            .join();
    if (partition == null) {
      return CompletableActorFuture.completedExceptionally(
          new RuntimeException(
              "Could not ban process instance %s, partition %s does not exist"
                  .formatted(processInstanceKey, partitionId)));
    }

    return partition.banInstance(processInstanceKey);
  }

  private ActorFuture<Void> callOnEachPartition(
      final Function<PartitionAdminAccess, ActorFuture<Void>> functionToCall) {
    final ActorFuture<Void> response = concurrencyControl.createFuture();
    final var aggregatedResult =
        partitions.values().stream()
            .map(
                p ->
                    p.thenApply(ZeebePartition::createAdminAccess)
                        .orTimeout(1000, TimeUnit.MILLISECONDS)
                        .join())
            .map(functionToCall)
            .collect(new ActorFutureCollector<>(concurrencyControl));

    concurrencyControl.runOnCompletion(
        aggregatedResult,
        (value, error) -> {
          if (error != null) {
            response.completeExceptionally(error);
          } else {
            response.complete(null);
          }
        });

    return response;
  }
}
