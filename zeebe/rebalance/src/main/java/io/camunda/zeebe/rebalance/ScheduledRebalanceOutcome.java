/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

/** What came of a scheduled rebalance once it was due. */
public enum ScheduledRebalanceOutcome {
  STARTED,
  /** Another rebalance was still running. */
  ALREADY_RUNNING,
  /** Every partition was already led by its desired leader. */
  ALREADY_BALANCED,
  /** The cluster was busier than a configured threshold. */
  BUSY,
  /** Not every broker reported its load over the whole window. */
  LOAD_UNKNOWN,
  CONFIGURATION_CHANGE_IN_PROGRESS,
  FAILED
}
