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
   * The leader commits a tick every interval and the other brokers acknowledge each tick with a
   * tock, which the leader commits with its next tick.
   */
  TICK_TOCK
}
