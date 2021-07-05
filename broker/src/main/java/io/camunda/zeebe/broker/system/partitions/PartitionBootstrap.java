/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.camunda.zeebe.util.sched.future.ActorFuture;

public interface PartitionBootstrap {

  /**
   * Performs bootstrap actions required for the partition to function.
   *
   * @return future that contains {@link PartitionTransitionContext} which can subsequently be used
   *     to transition the partition into a certain state
   */
  ActorFuture<PartitionTransitionContext> open();

  /**
   * Perform tear-down actions to shutdown the partition.
   *
   * @return future
   */
  ActorFuture<Void> close();
}
