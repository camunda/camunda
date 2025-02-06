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

public enum LeaderMetricsDoc implements ExtendedMeterDocumentation {
  /** Latency to append an entry to a follower */
  APPEND_ENTRIES_LATENCY {
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
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Latency to append an entry to a follower";
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
  /** The count of entries appended (counting entries, not their size) */
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
  /** The number of non-replicated entries for a given followers */
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
  /** The count of entries committed (counting entries, not their size) */
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
  /** The number of non-committed entries on the leader */
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
