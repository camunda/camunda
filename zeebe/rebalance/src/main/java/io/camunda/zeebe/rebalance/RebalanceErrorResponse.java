/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

/** Reasons for the rebalancing coordinator to refuse a request. */
public record RebalanceErrorResponse(RebalanceErrorCode code, String message) {

  public enum RebalanceErrorCode {
    /** A rebalance is already running, and rebalances do not merge or queue. */
    REBALANCE_IN_PROGRESS,
    /** The member that received the request is not the current coordinator. */
    NOT_COORDINATOR,
    /**
     * A cluster configuration change is pending, so the configuration to plan against is not
     * settled.
     */
    CONFIGURATION_CHANGE_IN_PROGRESS,
    INTERNAL_ERROR
  }
}
