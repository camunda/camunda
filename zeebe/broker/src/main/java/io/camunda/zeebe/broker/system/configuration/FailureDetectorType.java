/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

/** The failure detectors the system partition can run. */
public enum FailureDetectorType {
  /** No failure detection. */
  NONE,
  /**
   * The brokers track the cluster membership from SWIM gossip and report their view to each other;
   * the system partition leader consolidates the reports and commits a region failover when a data
   * region is gone.
   */
  SWIM
}
