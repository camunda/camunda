/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

/**
 * How the coordinator refuses a request. Each of these maps to a {@link
 * RebalanceErrorResponse.RebalanceErrorCode} on the wire; anything else it throws is reported as an
 * internal error.
 */
public sealed interface RebalanceRequestFailedException {

  /** Signals that a rebalance is already running, so this one is refused rather than queued. */
  final class RebalanceInProgress extends RuntimeException
      implements RebalanceRequestFailedException {
    public RebalanceInProgress(final String message) {
      super(message);
    }
  }

  /**
   * Signals that the request reached a member that is not the coordinator, so the sender's view of
   * the cluster configuration is behind this member's.
   */
  final class NotCoordinator extends RuntimeException implements RebalanceRequestFailedException {
    public NotCoordinator(final String message) {
      super(message);
    }
  }
}
