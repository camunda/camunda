/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import org.jspecify.annotations.NullMarked;

/**
 * What the rebalancing coordinator answers. Implemented on the coordinating member and reached from
 * any other member through {@link RebalanceRequestSender}.
 *
 * <p>Every method may fail with a {@link RebalanceRequestFailedException}.
 */
@NullMarked
public interface RebalanceApi {

  /**
   * Starts a cluster-wide rebalance and reports the status it starts from. Fails with {@link
   * RebalanceRequestFailedException.RebalanceInProgress} if one is already running - rebalances
   * neither queue nor merge, because each pins its own view of the desired leaders.
   */
  ActorFuture<RebalanceStatus> triggerRebalance(TriggerRebalanceRequest request);

  /** Reports the rebalance in flight, if any, and the last one this coordinator finished. */
  ActorFuture<RebalanceStatus> getRebalanceStatus();

  /**
   * Asks the running rebalance to stop once its in-flight transfer finishes, and reports whether
   * there was one to stop. Partitions already transferred keep their new leaders.
   */
  ActorFuture<CancelRebalanceResponse> cancelRebalance();
}
