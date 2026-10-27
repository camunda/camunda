/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

/** How a rebalance ended. */
public enum RebalanceOutcome {
  /**
   * Every partition selected was processed (regardless of whether its leadership was transferred,
   * or the transfer was skipped).
   */
  COMPLETED,
  /** The rebalance was cancelled before all selected partitions were processed. */
  CANCELLED,
  /** The rebalance failed with an error before all selected partitions were processed. */
  FAILED
}
