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
import java.time.Duration;

public enum LeaderMetricsDoc implements ExtendedMeterDocumentation {
  APPEND_ENTRIES_LATENCY {
    // FIXME check values
    private static final Duration[] BUCKETS = {
      Duration.ofNanos(100_000), // 100 micros
      Duration.ofMillis(1),
      Duration.ofMillis(10),
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofSeconds(1),
      Duration.ofSeconds(2)
    };

    @Override
    public String getBaseUnit() {
      return "ms";
    }

    @Override
    public String getName() {
      return "atomix.append.entries.latency";
    }

    @Override
    public Type getType() {
      // FIXME CHECK
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Latency to append an entry to a follower";
    }

    @Override
    public Duration[] getTimerSLOs() {
      return BUCKETS;
    }
  },
  APPEND_RATE {
    @Override
    public String getName() {
      return "atomix.append.entries.rate";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "The count of entries appended (counting entries, not their size)";
    }
  },
  APPEND_DATA_RATE {
    @Override
    public String getName() {
      return "atomix.append.entries.data.rate";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "The count of entries appended (counting entries, not their size)";
    }
  },
  NON_REPLICATED_ENTRIES {
    @Override
    public String getName() {
      return "atomix.non_replicated.entries";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "The number of non-replicated entries for a given followers";
    }
  },
  COMMIT_RATE {
    @Override
    public String getName() {
      return "atomix.commit.entries.rate";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "The count of entries committed (counting entries, not their size)";
    }
  },
  NON_COMMITTED_ENTRIES {
    @Override
    public String getName() {
      return "atomix.non_committed.entries";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "The number of non-committed entries on the leader";
    }
  }
}
