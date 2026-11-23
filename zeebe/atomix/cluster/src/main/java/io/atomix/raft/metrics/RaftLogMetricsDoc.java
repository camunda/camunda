/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.core.instrument.Meter.Type;

@SuppressWarnings("NullableProblems")
public enum RaftLogMetricsDoc implements ExtendedMeterDocumentation {
  /**
   * Counts the times a reader found no entry at an index at or below the commit index. Anything
   * above zero means the log is corrupted.
   */
  MISSING_COMMITTED_ENTRIES {
    @Override
    public String getName() {
      return "zeebe.raft.log.missing.committed.entries";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Number of times a reader found no entry in the log at an index that is committed,"
          + " which means the log is corrupted";
    }
  }
}
