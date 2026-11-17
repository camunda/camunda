/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;
import java.util.stream.Stream;

/** Metrics for the cluster-wide side of a rebalance (coordinated leadership transfer). */
@SuppressWarnings("NullableProblems")
public enum ClusterRebalanceMetricsDoc implements ExtendedMeterDocumentation {
  /** How long a whole cluster-wide rebalance took, tagged by how it ended. */
  REBALANCE_ELAPSED {
    private static final Duration[] BUCKETS =
        Stream.of(1, 5, 10, 30, 60, 120, 300, 600, 1_800, 3_600)
            .map(Duration::ofSeconds)
            .toArray(Duration[]::new);

    @Override
    public String getBaseUnit() {
      return "ms";
    }

    @Override
    public String getName() {
      return "zeebe.cluster.rebalance.elapsed";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Duration of a cluster-wide rebalance, by how it ended";
    }

    @Override
    public Duration[] getTimerSLOs() {
      return BUCKETS;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {ClusterRebalanceKeyNames.RESULT};
    }
  },

  /** How long the coordinator spent on one partition, tagged by the outcome. */
  PARTITION_DURATION {
    private static final Duration[] BUCKETS =
        Stream.of(100, 500, 1_000, 2_500, 5_000, 10_000, 20_000, 30_000, 60_000, 120_000, 300_000)
            .map(Duration::ofMillis)
            .toArray(Duration[]::new);

    @Override
    public String getBaseUnit() {
      return "ms";
    }

    @Override
    public String getName() {
      return "zeebe.cluster.rebalance.partition.duration";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Duration the rebalance spent on one partition, by what became of it";
    }

    @Override
    public Duration[] getTimerSLOs() {
      return BUCKETS;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {
        PartitionKeyNames.PARTITION,
        PartitionKeyNames.PHYSICAL_TENANT,
        ClusterRebalanceKeyNames.RESULT
      };
    }
  },

  /**
   * Where the most recent rebalance got to with a partition, as a single value per partition:
   * {@code 1} pending, {@code 2} transferring, {@code 3} completed.
   *
   * <p>Outlives the rebalance, so that where a partition stands can be read after the fact; a
   * rebalance may well be over inside a single metrics scrape.
   */
  PARTITION_STATE {
    @Override
    public String getName() {
      return "zeebe.cluster.rebalance.partition.state";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Where the last rebalance got to with a partition: 1 pending, 2 transferring, "
          + "3 completed";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION, PartitionKeyNames.PHYSICAL_TENANT};
    }
  };

  public enum ClusterRebalanceKeyNames implements KeyName {
    /** How a whole rebalance, or the rebalance's work on one partition, ended. */
    RESULT {
      @Override
      public String asString() {
        return "result";
      }
    }
  }
}
