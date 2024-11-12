/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;

/**
 * An executor that supports partition scaling and monitoring progress of redistribution and
 * relocation of data.
 */
public interface PartitionScalingChangeExecutor {
  /**
   * Initiates scaling up of partitions, i.e. starts the process of resource redistribution and data
   * relocation. The implementation must be idempotent.
   *
   * @return A future that completes as soon as scaling up has started.
   */
  ActorFuture<Void> initiateScaleUp(int desiredPartitionCount);

  final class NoopPartitionScalingChangeExecutor implements PartitionScalingChangeExecutor {
    @Override
    public ActorFuture<Void> initiateScaleUp(final int desiredPartitionCount) {
      return CompletableActorFuture.completed(null);
    }
  }
}
