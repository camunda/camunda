/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.metrics;

import static io.atomix.raft.metrics.RaftLogMetricsDoc.MISSING_COMMITTED_ENTRIES;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public final class RaftLogMetrics {
  private final Counter missingCommittedEntries;

  public RaftLogMetrics(final MeterRegistry registry) {
    missingCommittedEntries =
        Counter.builder(MISSING_COMMITTED_ENTRIES.getName())
            .description(MISSING_COMMITTED_ENTRIES.getDescription())
            .register(registry);
  }

  public void observeMissingCommittedEntry() {
    missingCommittedEntries.increment();
  }
}
