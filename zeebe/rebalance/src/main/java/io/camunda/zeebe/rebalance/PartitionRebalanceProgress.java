/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

/**
 * Where a rebalance has got to with one partition, independent of what the outcome was (see {@link
 * PartitionRebalanceOutcome}).
 */
public enum PartitionRebalanceProgress {
  /** The rebalance has not reached this partition yet. */
  PENDING,
  /** The partition's leader accepted the transfer and is running it. */
  TRANSFERRING,
  /** The rebalance is done with this partition (successfully or not). */
  COMPLETED
}
