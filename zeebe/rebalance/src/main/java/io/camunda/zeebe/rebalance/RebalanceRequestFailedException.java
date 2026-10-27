/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.camunda.zeebe.rebalance.RebalanceErrorResponse.RebalanceErrorCode;

/**
 * Reasons for the rebalancing coordinator to refuse a request. Each maps to a {@link
 * RebalanceErrorCode}.
 */
public abstract sealed class RebalanceRequestFailedException extends RuntimeException {
  protected RebalanceRequestFailedException(final String message) {
    super(message);
  }

  public abstract RebalanceErrorCode getErrorCode();

  /** Signals that a rebalance is already running, so this one is refused rather than queued. */
  public static final class RebalanceInProgressException extends RebalanceRequestFailedException {
    public RebalanceInProgressException(final String message) {
      super(message);
    }

    @Override
    public RebalanceErrorCode getErrorCode() {
      return RebalanceErrorCode.REBALANCE_IN_PROGRESS;
    }
  }

  /** Signals that the request reached a member that is not the coordinator. */
  public static final class NotCoordinatorException extends RebalanceRequestFailedException {
    public NotCoordinatorException(final String message) {
      super(message);
    }

    @Override
    public RebalanceErrorCode getErrorCode() {
      return RebalanceErrorCode.NOT_COORDINATOR;
    }
  }

  /**
   * Signals that a cluster configuration change is pending, so there is no settled configuration to
   * plan a rebalance against.
   */
  public static final class ConfigurationChangeInProgressException
      extends RebalanceRequestFailedException {
    public ConfigurationChangeInProgressException(final String message) {
      super(message);
    }

    @Override
    public RebalanceErrorCode getErrorCode() {
      return RebalanceErrorCode.CONFIGURATION_CHANGE_IN_PROGRESS;
    }
  }
}
