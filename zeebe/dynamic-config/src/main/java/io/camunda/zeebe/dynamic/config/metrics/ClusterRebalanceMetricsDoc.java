/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;
import java.util.stream.Stream;

/**
 * What the rebalancing coordinator reports about a cluster-wide rebalance. Only the coordinating
 * member publishes these, so a rebalance shows up once for the cluster rather than once per member.
 *
 * <p>The partition states describe the rebalance that last set them and stand until the next one
 * replaces them; the timers accumulate across rebalances. Both outlive the member's turn at
 * coordinating, so a rebalance can still be accounted for over a window that the coordinator moved
 * during.
 */
@SuppressWarnings("NullableProblems")
public enum ClusterRebalanceMetricsDoc implements ExtendedMeterDocumentation {
  /** How long a whole cluster-wide rebalance took, tagged by how it ended. */
  REBALANCE_ELAPSED {
    /**
     * A rebalance walks its partitions one at a time, so its length is the per-partition budget
     * multiplied by however many partitions have to move. The top of the range is sized for a large
     * cluster whose transfers are all going badly, which is the case worth being able to see.
     */
    private static final Duration[] TIMER_SLOS =
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
      return TIMER_SLOS;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {ClusterRebalanceKeyNames.RESULT};
    }
  },

  /**
   * How long the coordinator spent on one partition, from taking it on to knowing what became of
   * it, tagged by the outcome. Recorded once per partition per rebalance.
   */
  PARTITION_DURATION {
    /**
     * A partition's transfer is bounded by the coordinator's leader-wait timeout, a minute by
     * default, and an operator may raise it. The buckets reach well past that so that a transfer
     * the coordinator gave up on still lands somewhere other than the overflow.
     */
    private static final Duration[] TIMER_SLOS =
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
      return TIMER_SLOS;
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
   * Where the last rebalance got to with one partition, as a single value per partition in the
   * manner of {@code atomix.role}: {@code 1} pending, {@code 2} transferring, {@code 3}
   * transferred, {@code 4} skipped, {@code 5} failed.
   *
   * <p>Outlives the rebalance that set it, so that what became of a partition can be read after the
   * fact; a rebalance can be over inside a single scrape, and a state only published while one runs
   * would go unsampled altogether.
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
          + "3 transferred, 4 skipped, 5 failed";
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
