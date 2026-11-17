/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.camunda.zeebe.rebalance.RebalanceRequestFailedException.ConfigurationChangeInProgressException;
import io.camunda.zeebe.rebalance.RebalanceRequestFailedException.NotCoordinatorException;
import io.camunda.zeebe.rebalance.RebalanceRequestFailedException.RebalanceInProgressException;
import io.camunda.zeebe.scheduler.future.ActorFuture;

/** Interface for the rebalance coordinator. */
public interface RebalanceApi {
  /**
   * Starts a cluster-wide rebalance and reports the status it starts from. Fails with {@link
   * RebalanceInProgressException} if one is already running - rebalances neither queue nor merge,
   * because each pins its own view of the desired leaders.
   *
   * @throws RebalanceInProgressException If a rebalance is already running.
   * @throws NotCoordinatorException If the request is received by a broker that is not the current
   *     coordinator.
   * @throws ConfigurationChangeInProgressException If a cluster configuration change is pending, so
   *     there is no settled configuration to plan the rebalance against.
   */
  ActorFuture<RebalanceStatus> triggerRebalance(TriggerRebalanceRequest request);

  /**
   * Reports the rebalance in flight, if any, and the last one this coordinator finished.
   *
   * @throws NotCoordinatorException If the request is received by a broker that is not the current
   *     coordinator.
   */
  ActorFuture<RebalanceStatus> getRebalanceStatus();

  /**
   * Asks the running rebalance to stop once its in-flight transfer finishes, and reports whether
   * there was one to stop. Partitions already transferred keep their new leaders.
   *
   * @throws NotCoordinatorException If the request is received by a broker that is not the current
   *     coordinator.
   */
  ActorFuture<CancelRebalanceResponse> cancelRebalance();
}
