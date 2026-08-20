/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;

/** Drives the leadership transfers of one rebalance, one partition at a time. */
@FunctionalInterface
public interface RebalanceRunner {
  /**
   * Runs {@code rebalance} to its end, completing once no partition is left to transfer. Completing
   * exceptionally fails the rebalance; stopping early due to cancellation completes normally, and
   * the coordinator reports it as cancelled.
   */
  ActorFuture<Void> run(RebalanceRun rebalance);

  /** A no-op runner that completes immediately. */
  static RebalanceRunner none() {
    return rebalance -> CompletableActorFuture.completed();
  }
}
