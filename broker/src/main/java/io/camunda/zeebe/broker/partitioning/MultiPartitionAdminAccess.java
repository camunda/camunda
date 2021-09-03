/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.ActorFutureCollector;
import java.util.List;
import java.util.function.Function;

final class MultiPartitionAdminAccess implements PartitionAdminAccess {
  private final ConcurrencyControl concurrencyControl;
  private final List<? extends PartitionAdminAccess> partitions;

  MultiPartitionAdminAccess(
      final ConcurrencyControl concurrencyControl,
      final List<? extends PartitionAdminAccess> partitions) {
    this.concurrencyControl = requireNonNull(concurrencyControl);
    this.partitions = unmodifiableList(requireNonNull(partitions));
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
        partitions.stream()
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
