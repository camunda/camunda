/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.partitions;

import io.zeebe.util.sched.future.ActorFuture;

public interface PartitionTransition {

  /**
   * Transitions to follower asynchronously by closing the current partition's components and
   * opening a follower partition.
   *
   * @param currentTerm the current term on which the transition happens
   * @return an ActorFuture to be completed when the transition is complete
   */
  ActorFuture<Void> toFollower(final long currentTerm);

  /**
   * Transitions to leader asynchronously by closing the current partition's components and opening
   * a leader partition.
   *
   * @param currentTerm the current term on which the transition happens
   * @return an ActorFuture to be completed when the transition is complete
   */
  ActorFuture<Void> toLeader(final long currentTerm);

  /**
   * Closes the current partition's components asynchronously.
   *
   * @return an ActorFuture completed when the transition is complete
   */
  ActorFuture<Void> toInactive();
}
