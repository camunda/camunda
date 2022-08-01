/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

final class MultiPartitionAdminAccess implements PartitionAdminAccess {
  private final ConcurrencyControl concurrencyControl;
  private final Map<Integer, ? extends PartitionAdminAccess> partitions;

  MultiPartitionAdminAccess(
      final ConcurrencyControl concurrencyControl,
      final Map<Integer, ? extends PartitionAdminAccess> partitions) {
    this.concurrencyControl = requireNonNull(concurrencyControl);
    this.partitions = Collections.unmodifiableMap(requireNonNull(partitions));
  }

  /**
   * @return A scoped-down admin access that that only act's on the given partition, not all
   *     partitions
   */
  @Override
  public PartitionAdminAccess forPartition(final int partitionId) {
    return partitions.get(partitionId);
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

  private ActorFuture<Void> callOnEachPartition(
      final Function<PartitionAdminAccess, ActorFuture<Void>> functionToCall) {
    final ActorFuture<Void> response = concurrencyControl.createFuture();
    final var aggregatedResult =
        partitions.values().stream()
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
