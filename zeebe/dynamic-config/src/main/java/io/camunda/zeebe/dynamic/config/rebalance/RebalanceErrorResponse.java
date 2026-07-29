/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

/** Why the coordinator refused a request. */
public record RebalanceErrorResponse(RebalanceErrorCode code, String message) {

  public enum RebalanceErrorCode {
    /** A rebalance is already running, and rebalances do not merge or queue. */
    REBALANCE_IN_PROGRESS,
    /**
     * The member that received the request is not the coordinator, which happens while a membership
     * change is still propagating to whoever forwarded it.
     */
    NOT_COORDINATOR,
    INTERNAL_ERROR
  }
}
