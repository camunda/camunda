/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

/** How a rebalance ended. */
public enum RebalanceOutcome {
  /** Every partition in scope was reached, whether its leadership moved or the transfer skipped. */
  COMPLETED,
  /** The operator cancelled the rebalance, so the partitions after the in-flight one were left. */
  CANCELLED,
  /** The rebalance stopped short of its remaining partitions. */
  FAILED
}
