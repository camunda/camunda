/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import org.jspecify.annotations.NullMarked;

/**
 * Drives the leadership transfers of one rebalance, one partition at a time.
 *
 * <p>This is the half of a rebalance the coordinator does not own: the coordinator decides that a
 * rebalance may start, holds its state and answers for it, while the runner picks the desired
 * leaders and asks each partition's leader to hand leadership over.
 */
@NullMarked
@FunctionalInterface
public interface RebalanceRunner {

  /**
   * Runs {@code rebalance} to its end, completing once no partition is left to transfer. Completing
   * exceptionally fails the rebalance; stopping early because {@link
   * RebalanceRun#isCancelRequested()} became true completes normally, and the coordinator reports
   * it as cancelled.
   */
  ActorFuture<Void> run(RebalanceRun rebalance);

  /**
   * A runner with no partitions to transfer, so every rebalance completes at once. Stands in until
   * the sequencing loop exists, and lets a test exercise the coordinator without one.
   */
  static RebalanceRunner none() {
    return rebalance -> CompletableActorFuture.completed();
  }
}
