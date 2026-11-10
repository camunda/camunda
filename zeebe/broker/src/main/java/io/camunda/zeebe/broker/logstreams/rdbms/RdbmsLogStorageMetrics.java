/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.rdbms;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Metrics of the RDBMS log storage's writer, one transaction per append. */
final class RdbmsLogStorageMetrics {

  private final Timer appendLatency;
  private final DistributionSummary blocksPerAppend;

  RdbmsLogStorageMetrics(final MeterRegistry meterRegistry) {
    appendLatency =
        MicrometerUtil.buildTimer(RdbmsLogStorageMetricsDoc.APPEND_LATENCY).register(meterRegistry);
    blocksPerAppend =
        MicrometerUtil.buildSummary(RdbmsLogStorageMetricsDoc.BLOCKS_PER_APPEND)
            .register(meterRegistry);
  }

  /** Records one committed append transaction. */
  void observeAppend(final int blocks, final long durationNanos) {
    appendLatency.record(durationNanos, TimeUnit.NANOSECONDS);
    blocksPerAppend.record(blocks);
  }

  enum RdbmsLogStorageMetricsDoc implements ExtendedMeterDocumentation {
    /** Time of one committed append transaction, from locking the term to the commit. */
    APPEND_LATENCY {
      private static final Duration[] BUCKETS = {
        Duration.ofMillis(1),
        Duration.ofMillis(2),
        Duration.ofMillis(5),
        Duration.ofMillis(10),
        Duration.ofMillis(25),
        Duration.ofMillis(50),
        Duration.ofMillis(100),
        Duration.ofMillis(250),
        Duration.ofMillis(500),
        Duration.ofSeconds(1)
      };

      @Override
      public String getDescription() {
        return "Time of one committed append transaction to the RDBMS log, from locking the term to the commit";
      }

      @Override
      public String getName() {
        return "zeebe.rdbms.log.append.latency";
      }

      @Override
      public Type getType() {
        return Type.TIMER;
      }

      @Override
      public Duration[] getTimerSLOs() {
        return BUCKETS;
      }

      @Override
      public KeyName[] getAdditionalKeyNames() {
        return PartitionKeyNames.values();
      }
    },

    /** Number of blocks written by one committed append transaction. */
    BLOCKS_PER_APPEND {
      private static final double[] BUCKETS = {1, 2, 3, 5, 10, 25, 50, 100, 250};

      @Override
      public String getDescription() {
        return "Number of blocks written by one committed append transaction to the RDBMS log";
      }

      @Override
      public String getName() {
        return "zeebe.rdbms.log.append.blocks";
      }

      @Override
      public Type getType() {
        return Type.DISTRIBUTION_SUMMARY;
      }

      @Override
      public double[] getDistributionSLOs() {
        return BUCKETS;
      }

      @Override
      public KeyName[] getAdditionalKeyNames() {
        return PartitionKeyNames.values();
      }
    }
  }
}
