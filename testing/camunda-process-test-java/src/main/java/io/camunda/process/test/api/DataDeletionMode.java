/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.api;

/** Defines how Camunda Process Test cleans up runtime and deployment data after each test. */
public enum DataDeletionMode {
  /** Purges the full cluster state after each test (default). */
  CLUSTER_PURGE,

  /** Skips runtime data deletion after each test. */
  NONE
}
