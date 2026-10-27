/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.cluster.migration;

import org.jspecify.annotations.NullMarked;

/**
 * The state of a single upgrade-readiness condition reported by a {@link MigrationStatusProvider}.
 */
@NullMarked
public enum MigrationState {

  /**
   * The condition is fully satisfied for the current application version, computed with complete
   * information.
   */
  MIGRATED,

  /** The condition is confidently <b>not yet</b> satisfied. */
  MIGRATION_IN_PROGRESS,

  /**
   * No confident answer is available, typically because part of a distributed lookup (a broker, a
   * partition, a replica) did not respond in time.
   */
  UNKNOWN
}
