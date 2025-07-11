/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;

@SuppressWarnings("NullableProblems")
public enum SnapshotMetricsDoc implements ExtendedMeterDocumentation {
  /** Total count of committed snapshots on disk */
  SNAPSHOT_COUNT {
    @Override
    public String getDescription() {
      return "Total count of committed snapshots on disk";
    }

    @Override
    public String getName() {
      return "zeebe.snapshot.count";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return getPartitionBootstrapKeyNames();
    }
  },

  /** Estimated snapshot size on disk */
  SNAPSHOT_SIZE {
    @Override
    public String getDescription() {
      return "Estimated snapshot size on disk";
    }

    @Override
    public String getName() {
      return "zeebe.snapshot.size.bytes";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getKeyNames() {
      return getPartitionBootstrapKeyNames();
    }
  },

  /** Number of chunks in the last snapshot */
  SNAPSHOT_CHUNK_COUNT {
    @Override
    public String getDescription() {
      return "Number of chunks in the last snapshot";
    }

    @Override
    public String getName() {
      return "zeebe.snapshot.chunks.count";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getKeyNames() {
      return getPartitionBootstrapKeyNames();
    }
  },

  /** Approximate duration of snapshot operation */
  SNAPSHOT_DURATION {
    @Override
    public String getDescription() {
      return "Approximate duration of snapshot operation";
    }

    @Override
    public String getName() {
      return "zeebe.snapshot.duration";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return getPartitionBootstrapKeyNames();
    }
  },

  /** Approximate duration of snapshot persist operation */
  SNAPSHOT_PERSIST_DURATION {
    @Override
    public String getDescription() {
      return "Approximate duration of snapshot persist operation";
    }

    @Override
    public String getName() {
      return "zeebe.snapshot.persist.duration";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return getPartitionBootstrapKeyNames();
    }
  },
  /** Approximate duration of snapshot transfer between two nodes */
  SNAPSHOT_TRANSFER_DURATION {
    @Override
    public String getDescription() {
      return "Total transfer duration of a snapshot between two nodes";
    }

    @Override
    public String getName() {
      return "zeebe.snapshot.transfer.duration";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getBaseUnit() {
      return "ms";
    }

    @Override
    public Duration[] getTimerSLOs() {
      return new Duration[] {
        Duration.ofMillis(50),
        Duration.ofMillis(250),
        Duration.ofMillis(500),
        Duration.ofSeconds(1),
        Duration.ofSeconds(5),
        Duration.ofSeconds(15),
        Duration.ofMinutes(30)
      };
    }

    @Override
    public KeyName[] getKeyNames() {
      return getPartitionBootstrapKeyNames();
    }
  },
  /** Approximate size of snapshot files */
  SNAPSHOT_FILE_SIZE {
    private static final double[] BUCKETS = {.01, .1, .5, 1, 5, 10, 25, 50, 100, 250, 500};

    @Override
    public String getDescription() {
      return "Approximate size of snapshot files";
    }

    @Override
    public String getName() {
      return "zeebe.snapshot.file.size.megabytes";
    }

    @Override
    public String getBaseUnit() {
      return "MB";
    }

    @Override
    public Type getType() {
      return Type.DISTRIBUTION_SUMMARY;
    }

    @Override
    public KeyName[] getKeyNames() {
      return getPartitionBootstrapKeyNames();
    }

    @Override
    public double[] getDistributionSLOs() {
      return BUCKETS;
    }
  };

  private static KeyName[] getPartitionBootstrapKeyNames() {
    return KeyName.merge(PartitionKeyNames.values(), BootstrapKeyNames.values());
  }

  @SuppressWarnings("NullableProblems")
  public enum BootstrapKeyNames implements KeyName {
    /** Whether the metric is recorded during bootstrap phase */
    BOOTSTRAP {
      @Override
      public String asString() {
        return "bootstrap";
      }
    };

    public static Tags tags(final boolean bootstrap) {
      return Tags.of(BOOTSTRAP.asString(), String.valueOf(bootstrap));
    }
  }
}
