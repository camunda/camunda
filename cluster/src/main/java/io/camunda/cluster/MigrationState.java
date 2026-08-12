/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.cluster;

import org.jspecify.annotations.NullMarked;

/**
 * The state of a single upgrade-readiness condition reported by a {@link MigrationStatusProvider}.
 *
 * <p>Once a condition is observed as {@link #MIGRATED}, it must never be reported again as {@link
 * #MIGRATION_IN_PROGRESS} or {@link #UNKNOWN} for the same target application version — providers,
 * and anything that aggregates them, must preserve this monotonicity guarantee even when a
 * distributed lookup is involved (e.g. by falling back to the last confirmed {@link #MIGRATED}
 * status instead of a transient lookup failure).
 */
@NullMarked
public enum MigrationState {
  /**
   * The condition is fully satisfied for the current application version, computed with complete
   * information.
   */
  MIGRATED,

  /**
   * The condition is confidently <b>not yet</b> satisfied — e.g. the schema is still on an older,
   * compatible version, or an exporter has not yet flushed all previous-version records. A normal,
   * expected, transient state right after an upgrade.
   */
  MIGRATION_IN_PROGRESS,

  /**
   * No confident answer is available, typically because part of a distributed lookup (a broker, a
   * partition, a replica) did not respond in time. Distinct from {@link #MIGRATION_IN_PROGRESS}: it
   * means "we don't know," not "we know it's not done," and may warrant operator attention if it
   * persists.
   */
  UNKNOWN
}
