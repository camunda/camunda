/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.partitions;

import io.zeebe.util.sched.future.ActorFuture;

public interface PartitionTransition {

  /**
   * Transitions to follower asynchronously by closing the current partition's components and
   * opening a follower partition.
   *
   * @return an ActorFuture to be completed when the transition is complete
   */
  ActorFuture<Void> toFollower();

  /**
   * Transitions to leader asynchronously by closing the current partition's components and opening
   * a leader partition.
   *
   * @return an ActorFuture to be completed when the transition is complete
   */
  ActorFuture<Void> toLeader();

  /**
   * Closes the current partition's components asynchronously.
   *
   * @return an ActorFuture completed when the transition is complete
   */
  ActorFuture<Void> toInactive();
}
